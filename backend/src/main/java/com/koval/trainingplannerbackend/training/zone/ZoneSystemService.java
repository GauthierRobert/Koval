package com.koval.trainingplannerbackend.training.zone;

import com.koval.trainingplannerbackend.club.membership.ClubMemberRole;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.training.group.GroupService;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages training zone systems: CRUD, default selection, and access control across coach, athlete, and club relationships.
 */
@Service
public class ZoneSystemService {

    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final ZoneSystemRepository zoneSystemRepository;
    private final GroupService groupService;
    private final ClubMembershipRepository clubMembershipRepository;

    private record CacheEntry(List<ZoneSystem> value, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private final Map<String, CacheEntry> defaultZoneCache = new ConcurrentHashMap<>();

    public ZoneSystemService(ZoneSystemRepository zoneSystemRepository, GroupService groupService,
                             ClubMembershipRepository clubMembershipRepository) {
        this.zoneSystemRepository = zoneSystemRepository;
        this.groupService = groupService;
        this.clubMembershipRepository = clubMembershipRepository;
    }

    /**
     * Creates a new zone system, sets its timestamps, persists it, and invalidates the default zone cache for the coach.
     *
     * @param zoneSystem the zone system to create
     * @return the persisted zone system with generated ID and timestamps
     */
    public ZoneSystem createZoneSystem(ZoneSystem zoneSystem) {
        zoneSystem.setCreatedAt(LocalDateTime.now());
        zoneSystem.setUpdatedAt(LocalDateTime.now());
        ZoneSystem saved = zoneSystemRepository.save(zoneSystem);
        defaultZoneCache.remove(zoneSystem.getCoachId());
        return saved;
    }

    /**
     * Updates an existing zone system with the provided field values. The coach ID cannot be changed.
     *
     * @param id      the ID of the zone system to update
     * @param updates a zone system object carrying the new field values
     * @return the updated and persisted zone system
     * @throws IllegalArgumentException if the zone system is not found or the coach ID differs
     */
    public ZoneSystem updateZoneSystem(String id, ZoneSystem updates) {
        ZoneSystem existing = zoneSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ZoneSystem not found: " + id));

        if (!Objects.equals(existing.getCoachId(), updates.getCoachId())) {
            throw new ValidationException("Cannot change coachId of a ZoneSystem");
        }

        applyUpdates(existing, updates);
        ZoneSystem saved = zoneSystemRepository.save(existing);
        defaultZoneCache.remove(existing.getCoachId());
        return saved;
    }

    /**
     * Deletes a zone system after verifying ownership by the given coach.
     *
     * @param id      the ID of the zone system to delete
     * @param coachId the ID of the coach who must own the zone system
     * @throws IllegalArgumentException if the zone system is not found or does not belong to the coach
     */
    public void deleteZoneSystem(String id, String coachId) {
        getOwnedZoneSystem(id, coachId);
        zoneSystemRepository.deleteById(id);
        defaultZoneCache.remove(coachId);
    }

    /**
     * Returns all zone systems belonging to a coach.
     *
     * @param coachId the coach's user ID
     * @return the list of zone systems owned by the coach
     */
    public List<ZoneSystem> getZoneSystemsForCoach(String coachId) {
        return zoneSystemRepository.findByCoachId(coachId);
    }

    /**
     * Returns the first zone system matching a coach ID and name.
     *
     * @param coachId the coach's user ID
     * @param name    the zone system name to search for
     * @return the matching zone system
     * @throws IllegalArgumentException if no zone system matches the coach and name
     */
    public ZoneSystem getZoneSystemsForCoach(String coachId, String name) {
        return zoneSystemRepository.findByCoachIdAndName(coachId, name).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ZoneSystem does not exist for this coach and name"));
    }

    /**
     * Retrieves a zone system by its ID.
     *
     * @param id the zone system ID
     * @return the zone system
     * @throws IllegalArgumentException if the zone system is not found
     */
    public ZoneSystem getZoneSystem(String id) {
        return zoneSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ZoneSystem not found: " + id));
    }

    /**
     * Retrieves a zone system by ID, verifying that the user has access as owner, athlete, or club member.
     *
     * @param id     the zone system ID
     * @param userId the user requesting access
     * @return the zone system if access is granted
     * @throws AccessDeniedException if the user does not have access
     */
    public ZoneSystem getZoneSystemWithAccess(String id, String userId) {
        ZoneSystem zs = getZoneSystem(id);
        if (userId.equals(zs.getCoachId())) return zs;
        List<String> coachIds = groupService.getCoachIdsForAthlete(userId);
        if (coachIds.contains(zs.getCoachId())) return zs;
        // Check if user shares a club with the zone system's coach
        List<ZoneSystem> clubZones = getZoneSystemsFromClubCoaches(userId);
        if (clubZones.stream().anyMatch(z -> z.getId().equals(id))) return zs;
        throw new AccessDeniedException("You do not have access to this zone system");
    }

    /**
     * Sets or unsets a zone system as the default for its sport type. When setting as default,
     * any previous default for the same coach and sport is unset first.
     *
     * @param zoneSystemId the ID of the zone system to update
     * @param coachId      the coach who must own the zone system
     * @param isDefault    {@code true} to mark as default, {@code false} to unmark
     * @return the updated zone system
     * @throws IllegalArgumentException if the zone system is not found or does not belong to the coach
     */
    public ZoneSystem setDefaultForSport(String zoneSystemId, String coachId, boolean isDefault) {
        ZoneSystem target = getOwnedZoneSystem(zoneSystemId, coachId);

        if (isDefault) {
            unsetPreviousDefault(coachId, target.getSportType(), zoneSystemId);
        }

        target.setDefaultForSport(isDefault);
        target.setUpdatedAt(LocalDateTime.now());
        ZoneSystem saved = zoneSystemRepository.save(target);
        defaultZoneCache.remove(coachId);
        return saved;
    }

    /**
     * Returns the default zone system for a given coach and sport type, if one exists.
     *
     * @param coachId   the coach's user ID
     * @param sportType the sport type to look up
     * @return an {@link Optional} containing the default zone system, or empty if none is set
     */
    public Optional<ZoneSystem> getDefaultZoneSystem(String coachId, SportType sportType) {
        return zoneSystemRepository.findByCoachIdAndSportTypeAndDefaultForSportTrue(coachId, sportType);
    }

    /**
     * Returns all default zone systems for a coach, using a time-based cache to reduce database lookups.
     *
     * @param coachId the coach's user ID
     * @return the list of default zone systems across all sport types
     */
    public List<ZoneSystem> getDefaultZoneSystems(String coachId) {
        CacheEntry entry = defaultZoneCache.get(coachId);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        List<ZoneSystem> result = zoneSystemRepository.findByCoachIdAndDefaultForSportTrue(coachId);
        defaultZoneCache.put(coachId, new CacheEntry(result, System.currentTimeMillis()));
        return result;
    }

    /**
     * Returns all zone systems from coaches who coach the given athlete (via group relationships).
     *
     * @param athleteId the athlete's user ID
     * @return the list of zone systems from the athlete's coaches, or an empty list if the athlete has no coaches
     */
    public List<ZoneSystem> getZoneSystemsForAthlete(String athleteId) {
        List<String> coachIds = groupService.getCoachIdsForAthlete(athleteId);
        if (coachIds.isEmpty()) return List.of();
        return zoneSystemRepository.findByCoachIdIn(coachIds);
    }

    /**
     * Returns zone systems from all coaches in clubs where the user is an active member.
     */
    public List<ZoneSystem> getZoneSystemsFromClubCoaches(String userId) {
        List<ClubMembership> userMemberships = clubMembershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .toList();
        if (userMemberships.isEmpty()) return List.of();

        List<String> clubCoachIds = findClubCoachIds(userMemberships, userId);
        if (clubCoachIds.isEmpty()) return List.of();
        return zoneSystemRepository.findByCoachIdIn(clubCoachIds.stream().distinct().toList());
    }

    /**
     * Copies all mutable fields from {@code updates} onto {@code existing} and sets the updated timestamp.
     */
    private void applyUpdates(ZoneSystem existing, ZoneSystem updates) {
        existing.setName(updates.getName());
        existing.setSportType(updates.getSportType());
        existing.setReferenceType(updates.getReferenceType());
        existing.setReferenceName(updates.getReferenceName());
        existing.setReferenceUnit(updates.getReferenceUnit());
        existing.setZones(new ArrayList<>(updates.getZones()));
        existing.setDefaultForSport(updates.getDefaultForSport());
        existing.setAnnotations(updates.getAnnotations());
        existing.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Retrieves a zone system by ID and verifies it belongs to the given coach.
     */
    private ZoneSystem getOwnedZoneSystem(String zoneSystemId, String coachId) {
        ZoneSystem zoneSystem = zoneSystemRepository.findById(zoneSystemId)
                .orElseThrow(() -> new ResourceNotFoundException("ZoneSystem not found: " + zoneSystemId));

        if (!Objects.equals(zoneSystem.getCoachId(), coachId)) {
            throw new ForbiddenOperationException("ZoneSystem does not belong to this coach");
        }
        return zoneSystem;
    }

    /**
     * Finds and un-defaults the current default zone system for the given coach and sport,
     * excluding the specified zone system ID.
     */
    private void unsetPreviousDefault(String coachId, SportType sport, String excludeId) {
        zoneSystemRepository.findByCoachIdAndSportTypeAndDefaultForSportTrue(coachId, sport)
                .ifPresent(current -> {
                    if (!current.getId().equals(excludeId)) {
                        current.setDefaultForSport(false);
                        current.setUpdatedAt(LocalDateTime.now());
                        zoneSystemRepository.save(current);
                    }
                });
    }

    /**
     * Collects user IDs of coaches/owners from the clubs represented by the given memberships,
     * excluding the specified user.
     */
    private List<String> findClubCoachIds(List<ClubMembership> memberships, String excludeUserId) {
        List<String> coachIds = new ArrayList<>();
        for (ClubMembership membership : memberships) {
            clubMembershipRepository.findByClubIdAndStatus(membership.getClubId(), ClubMemberStatus.ACTIVE).stream()
                    .filter(m -> m.getRole() == ClubMemberRole.COACH || m.getRole() == ClubMemberRole.OWNER)
                    .filter(m -> !m.getUserId().equals(excludeUserId))
                    .map(ClubMembership::getUserId)
                    .forEach(coachIds::add);
        }
        return coachIds;
    }
}
