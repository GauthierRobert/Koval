package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecurringSessionService {

    private final RecurringSessionTemplateRepository templateRepository;
    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubAuthorizationService authorizationService;
    private final ClubActivityService clubActivityService;

    public RecurringSessionService(RecurringSessionTemplateRepository templateRepository,
                                   ClubTrainingSessionRepository sessionRepository,
                                   ClubAuthorizationService authorizationService,
                                   ClubActivityService clubActivityService) {
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.clubActivityService = clubActivityService;
    }

    public RecurringSessionTemplate createTemplate(String userId, String clubId,
                                                    CreateRecurringSessionRequest req) {
        authorizationService.requireAdminOrCoach(userId, clubId);

        RecurringSessionTemplate template = new RecurringSessionTemplate();
        template.setClubId(clubId);
        template.setCreatedBy(userId);
        template.setCreatedAt(LocalDateTime.now());
        SessionPropertyMapper.applyRequest(req, template);
        template = templateRepository.save(template);

        generateInstances(template, 4);
        return template;
    }

    public RecurringSessionTemplate updateTemplate(String userId, String templateId,
                                                    CreateRecurringSessionRequest req) {
        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        authorizationService.requireAdminOrCoach(userId, template.getClubId());

        SessionPropertyMapper.applyRequest(req, template);
        return templateRepository.save(template);
    }

    public int cancelFutureInstances(String userId, String clubId, String templateId, String reason) {
        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        if (!template.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Template does not belong to this club");
        }
        authorizationService.requireAdminOrCoach(userId, clubId);

        template.setActive(false);
        templateRepository.save(template);

        List<ClubTrainingSession> futureInstances = sessionRepository
                .findByRecurringTemplateIdAndScheduledAtAfter(templateId, LocalDateTime.now());
        List<ClubTrainingSession> toSave = new ArrayList<>();
        int cancelledCount = 0;
        for (ClubTrainingSession session : futureInstances) {
            if (!session.isCancelled()) {
                session.setCancelled(true);
                session.setCancellationReason(reason);
                session.setCancelledAt(LocalDateTime.now());
                toSave.add(session);
                cancelledCount++;
            }
        }
        if (!toSave.isEmpty()) {
            sessionRepository.saveAll(toSave);
        }

        clubActivityService.emitActivity(clubId, ClubActivityType.RECURRING_SERIES_CANCELLED,
                userId, templateId, template.getTitle());

        return cancelledCount;
    }

    public void deactivateTemplate(String userId, String templateId) {
        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        authorizationService.requireAdminOrCoach(userId, template.getClubId());
        template.setActive(false);
        templateRepository.save(template);
    }

    public List<RecurringSessionTemplate> listTemplates(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        return templateRepository.findByClubId(clubId);
    }

    public void generateInstances(RecurringSessionTemplate template, int weeksAhead) {
        LocalDate today = LocalDate.now();
        DayOfWeek targetDay = template.getDayOfWeek();

        List<ClubTrainingSession> toSave = new ArrayList<>();
        for (int week = 0; week < weeksAhead; week++) {
            LocalDate targetDate = today.plusWeeks(week).with(TemporalAdjusters.nextOrSame(targetDay));
            // Skip if the target date is in the past
            if (targetDate.isBefore(today)) {
                continue;
            }

            LocalDateTime scheduledAt = targetDate.atTime(template.getTimeOfDay());
            LocalDateTime dayStart = targetDate.atStartOfDay();
            LocalDateTime dayEnd = targetDate.plusDays(1).atStartOfDay();

            // Check if an instance already exists for this day
            List<ClubTrainingSession> existing = sessionRepository
                    .findByRecurringTemplateIdAndScheduledAtBetween(template.getId(), dayStart, dayEnd);
            if (!existing.isEmpty()) {
                continue;
            }

            ClubTrainingSession session = new ClubTrainingSession();
            session.setClubId(template.getClubId());
            session.setCreatedBy(template.getCreatedBy());
            session.setScheduledAt(scheduledAt);
            session.setRecurringTemplateId(template.getId());
            session.setCreatedAt(LocalDateTime.now());
            SessionPropertyMapper.applyTemplate(template, session);
            toSave.add(session);
        }
        if (!toSave.isEmpty()) {
            sessionRepository.saveAll(toSave);
        }
    }

    public void updateFutureInstances(String templateId) {
        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        List<ClubTrainingSession> futureInstances = sessionRepository
                .findByRecurringTemplateIdAndScheduledAtAfter(templateId, LocalDateTime.now());
        for (ClubTrainingSession session : futureInstances) {
            SessionPropertyMapper.applyTemplate(template, session);
            // Update time if timeOfDay changed
            if (template.getTimeOfDay() != null && session.getScheduledAt() != null) {
                session.setScheduledAt(session.getScheduledAt().toLocalDate().atTime(template.getTimeOfDay()));
            }
        }
        if (!futureInstances.isEmpty()) {
            sessionRepository.saveAll(futureInstances);
        }
    }

    public void generateAllRecurring() {
        List<RecurringSessionTemplate> templates = templateRepository.findByActiveTrue();
        for (RecurringSessionTemplate template : templates) {
            generateInstances(template, 4);
        }
    }
}
