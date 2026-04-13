package com.koval.trainingplannerbackend.ical;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.coach.ScheduleStatus;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates an iCalendar (RFC 5545) feed for a user's training schedule,
 * combining scheduled workouts and club sessions.
 */
@Service
public class ICalService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubTrainingSessionRepository clubTrainingSessionRepository;

    public ICalService(ScheduledWorkoutRepository scheduledWorkoutRepository,
                       TrainingRepository trainingRepository,
                       ClubMembershipRepository clubMembershipRepository,
                       ClubTrainingSessionRepository clubTrainingSessionRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
    }

    public String generateFeed(User user) {
        LocalDate now = LocalDate.now();
        LocalDate from = now.minusDays(30);
        LocalDate to = now.plusDays(90);
        String userId = user.getId();

        // Fetch scheduled workouts
        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, from, to);

        // Batch-fetch linked trainings
        Set<String> trainingIds = workouts.stream()
                .filter(sw -> sw.getTrainingId() != null)
                .map(ScheduledWorkout::getTrainingId)
                .collect(Collectors.toSet());

        // Fetch club sessions
        List<String> clubIds = clubMembershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .map(ClubMembership::getClubId)
                .toList();

        List<ClubTrainingSession> clubSessions = List.of();
        if (!clubIds.isEmpty()) {
            clubSessions = clubTrainingSessionRepository
                    .findByClubIdInAndScheduledAtBetween(clubIds,
                            from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                    .stream()
                    .filter(cs -> !Boolean.TRUE.equals(cs.getCancelled()))
                    .filter(cs -> cs.getParticipantIds().contains(userId))
                    .toList();

            clubSessions.stream()
                    .filter(cs -> cs.getLinkedTrainingId() != null)
                    .map(ClubTrainingSession::getLinkedTrainingId)
                    .forEach(trainingIds::add);
        }

        Map<String, Training> trainingMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, t -> t));

        String now_utc = LocalDateTime.now().format(DATETIME_FMT) + "Z";

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Koval Training Planner//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Training Schedule\r\n");

        // Scheduled workouts as all-day events
        for (ScheduledWorkout sw : workouts) {
            Training t = sw.getTrainingId() != null ? trainingMap.get(sw.getTrainingId()) : null;
            String title = t != null ? t.getTitle() : "Scheduled Workout";
            String desc = buildWorkoutDescription(sw, t);
            String status = switch (sw.getStatus()) {
                case PENDING -> "TENTATIVE";
                case COMPLETED -> "CONFIRMED";
                case SKIPPED -> "CANCELLED";
            };

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:sw-").append(sw.getId()).append("@koval.app\r\n");
            sb.append("DTSTAMP:").append(now_utc).append("\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(sw.getScheduledDate().format(DATE_FMT)).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(sw.getScheduledDate().plusDays(1).format(DATE_FMT)).append("\r\n");
            sb.append("SUMMARY:").append(escapeIcs(title)).append("\r\n");
            if (!desc.isEmpty()) {
                sb.append("DESCRIPTION:").append(escapeIcs(desc)).append("\r\n");
            }
            sb.append("STATUS:").append(status).append("\r\n");
            sb.append("END:VEVENT\r\n");
        }

        // Club sessions as timed events
        for (ClubTrainingSession cs : clubSessions) {
            int durationMinutes = cs.getDurationMinutes() != null ? cs.getDurationMinutes() : 60;
            LocalDateTime start = cs.getScheduledAt();
            LocalDateTime end = start.plusMinutes(durationMinutes);

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:club-").append(cs.getId()).append("@koval.app\r\n");
            sb.append("DTSTAMP:").append(now_utc).append("\r\n");
            sb.append("DTSTART:").append(start.format(DATETIME_FMT)).append("\r\n");
            sb.append("DTEND:").append(end.format(DATETIME_FMT)).append("\r\n");
            sb.append("SUMMARY:").append(escapeIcs(cs.getTitle())).append("\r\n");
            if (cs.getDescription() != null && !cs.getDescription().isEmpty()) {
                sb.append("DESCRIPTION:").append(escapeIcs(cs.getDescription())).append("\r\n");
            }
            if (cs.getLocation() != null && !cs.getLocation().isEmpty()) {
                sb.append("LOCATION:").append(escapeIcs(cs.getLocation())).append("\r\n");
            }
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String buildWorkoutDescription(ScheduledWorkout sw, Training t) {
        StringBuilder desc = new StringBuilder();
        if (t != null && t.getDescription() != null) {
            desc.append(t.getDescription());
        }
        if (sw.getTss() != null) {
            if (!desc.isEmpty()) desc.append("\\n");
            desc.append("TSS: ").append(sw.getTss());
        } else if (t != null && t.getEstimatedTss() != null) {
            if (!desc.isEmpty()) desc.append("\\n");
            desc.append("TSS: ~").append(t.getEstimatedTss());
        }
        if (sw.getNotes() != null && !sw.getNotes().isEmpty()) {
            if (!desc.isEmpty()) desc.append("\\n");
            desc.append("Coach: ").append(sw.getNotes());
        }
        return desc.toString();
    }

    private String escapeIcs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
