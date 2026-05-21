package com.koval.trainingplannerbackend.integration.sync;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.integration.sync.events.WorkoutSyncCancelledEvent;
import com.koval.trainingplannerbackend.integration.sync.events.WorkoutSyncCreatedEvent;
import com.koval.trainingplannerbackend.integration.sync.events.WorkoutSyncEvent;
import com.koval.trainingplannerbackend.integration.sync.events.WorkoutSyncUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Single async listener that fans WorkoutSyncEvent instances out to every registered
 * {@link WorkoutSyncProvider} bean. Each provider decides — via {@link WorkoutSyncProvider#isEnabled}
 * — whether to handle the event for this athlete, so adding a new integration is one new
 * {@code @Component} with no edits to this class or to any of the publishing services.
 *
 * <p>State is held in {@link WorkoutSyncRecord} so reschedule/cancel can update or delete the
 * provider-side entry instead of creating duplicates. Failures are recorded on the record and
 * never propagate — they ran on a separate thread, the user-facing mutation already returned.
 */
@Component
public class WorkoutSyncDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkoutSyncDispatcher.class);

    private final List<WorkoutSyncProvider> providers;
    private final WorkoutSyncRecordRepository recordRepository;
    private final WorkoutSyncPayloadResolver resolver;
    private final UserRepository userRepository;

    public WorkoutSyncDispatcher(List<WorkoutSyncProvider> providers,
                                 WorkoutSyncRecordRepository recordRepository,
                                 WorkoutSyncPayloadResolver resolver,
                                 UserRepository userRepository) {
        this.providers = providers;
        this.recordRepository = recordRepository;
        this.resolver = resolver;
        this.userRepository = userRepository;
    }

    @EventListener
    @Async
    public void onCreated(WorkoutSyncCreatedEvent event) {
        handleUpsert(event, false);
    }

    @EventListener
    @Async
    public void onUpdated(WorkoutSyncUpdatedEvent event) {
        handleUpsert(event, true);
    }

    @EventListener
    @Async
    public void onCancelled(WorkoutSyncCancelledEvent event) {
        User athlete = userRepository.findById(event.athleteId()).orElse(null);
        if (athlete == null) return;
        List<WorkoutSyncRecord> records = recordRepository
                .findByAthleteIdAndSourceTypeAndSourceId(event.athleteId(), event.sourceType(), event.sourceId());
        for (WorkoutSyncRecord record : records) {
            providerById(record.getProviderId()).ifPresent(provider -> deleteRemote(provider, athlete, record));
        }
    }

    private void handleUpsert(WorkoutSyncEvent event, boolean updateIfExists) {
        User athlete = userRepository.findById(event.athleteId()).orElse(null);
        if (athlete == null) return;

        Optional<WorkoutSyncPayload> payloadOpt = resolver.resolve(
                event.athleteId(), event.sourceType(), event.sourceId());
        if (payloadOpt.isEmpty()) {
            log.debug("No payload resolved for {}/{} — skipping push", event.sourceType(), event.sourceId());
            return;
        }
        WorkoutSyncPayload payload = payloadOpt.get();

        for (WorkoutSyncProvider provider : providers) {
            if (!provider.isEnabled(athlete)) continue;
            syncOne(provider, athlete, payload, updateIfExists);
        }
    }

    private void syncOne(WorkoutSyncProvider provider, User athlete, WorkoutSyncPayload payload,
                         boolean updateIfExists) {
        WorkoutSyncRecord record = recordRepository
                .findByAthleteIdAndSourceTypeAndSourceIdAndProviderId(
                        payload.athleteId(), payload.sourceType(), payload.sourceId(), provider.providerId())
                .orElseGet(() -> new WorkoutSyncRecord(payload.athleteId(), payload.sourceType(),
                        payload.sourceId(), provider.providerId()));

        try {
            Optional<String> externalRef;
            if (updateIfExists && record.getExternalRef() != null) {
                externalRef = provider.update(athlete, payload, record.getExternalRef());
            } else {
                externalRef = provider.push(athlete, payload);
            }
            record.setExternalRef(externalRef.orElse(record.getExternalRef()));
            record.setStatus(externalRef.isPresent() ? WorkoutSyncStatus.SYNCED : WorkoutSyncStatus.FAILED);
            record.setError(externalRef.isPresent() ? null : "Provider returned no external ref");
            record.setLastSyncedAt(LocalDateTime.now());
        } catch (RuntimeException e) {
            log.warn("Provider {} failed to sync {}/{} for athlete {}: {}",
                    provider.providerId(), payload.sourceType(), payload.sourceId(),
                    payload.athleteId(), e.getMessage());
            record.setStatus(WorkoutSyncStatus.FAILED);
            record.setError(truncate(e.getMessage()));
        }
        recordRepository.save(record);
    }

    private void deleteRemote(WorkoutSyncProvider provider, User athlete, WorkoutSyncRecord record) {
        if (record.getExternalRef() == null) {
            recordRepository.delete(record);
            return;
        }
        // We can't resolve a full payload (source may be gone) — pass a minimal stub.
        WorkoutSyncPayload stub = new WorkoutSyncPayload(record.getAthleteId(), record.getSourceType(),
                record.getSourceId(), null, null, null, null);
        try {
            provider.delete(athlete, stub, record.getExternalRef());
        } catch (RuntimeException e) {
            log.warn("Provider {} failed to delete {}/{}: {}", provider.providerId(),
                    record.getSourceType(), record.getSourceId(), e.getMessage());
        }
        record.setStatus(WorkoutSyncStatus.DELETED);
        record.setLastSyncedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private Optional<WorkoutSyncProvider> providerById(String id) {
        return providers.stream().filter(p -> p.providerId().equals(id)).findFirst();
    }

    private static String truncate(String message) {
        if (message == null) return null;
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
