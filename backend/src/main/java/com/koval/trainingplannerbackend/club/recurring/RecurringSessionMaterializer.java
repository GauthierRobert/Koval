package com.koval.trainingplannerbackend.club.recurring;

import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.club.session.SessionPropertyMapper;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class RecurringSessionMaterializer {

    public static final String VIRTUAL_PREFIX = "virtual:";

    private final ClubTrainingSessionRepository sessionRepository;
    private final RecurringSessionTemplateRepository templateRepository;

    public RecurringSessionMaterializer(ClubTrainingSessionRepository sessionRepository,
                                        RecurringSessionTemplateRepository templateRepository) {
        this.sessionRepository = sessionRepository;
        this.templateRepository = templateRepository;
    }

    public static boolean isVirtualId(String sessionId) {
        return sessionId != null && sessionId.startsWith(VIRTUAL_PREFIX);
    }

    public ClubTrainingSession resolveOrMaterialize(String sessionId) {
        if (!isVirtualId(sessionId)) {
            return sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        }
        String[] parts = sessionId.split(":", 3);
        if (parts.length != 3) {
            throw new ValidationException("Invalid virtual session ID: " + sessionId);
        }
        String templateId = parts[1];
        LocalDate date;
        try {
            date = LocalDate.parse(parts[2]);
        } catch (Exception e) {
            throw new ValidationException("Invalid virtual session date: " + parts[2]);
        }

        List<ClubTrainingSession> existing = sessionRepository
                .findByRecurringTemplateIdAndScheduledAtBetween(
                        templateId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringSessionTemplate", templateId));
        validateOccurrence(template, date);
        return sessionRepository.save(buildSession(template, date, false));
    }

    public List<ClubTrainingSession> synthesizeVirtuals(List<RecurringSessionTemplate> activeTemplates,
                                                        LocalDate fromDate, LocalDate toDate,
                                                        Set<String> coveredKeys) {
        List<ClubTrainingSession> virtuals = new ArrayList<>();
        for (RecurringSessionTemplate t : activeTemplates) {
            if (t.getDayOfWeek() == null || t.getTimeOfDay() == null) continue;
            LocalDate end = (t.getEndDate() != null && t.getEndDate().isBefore(toDate))
                    ? t.getEndDate() : toDate;
            LocalDate cur = fromDate.with(TemporalAdjusters.nextOrSame(t.getDayOfWeek()));
            while (!cur.isAfter(end)) {
                if (!coveredKeys.contains(t.getId() + ":" + cur)) {
                    virtuals.add(buildSession(t, cur, true));
                }
                cur = cur.plusWeeks(1);
            }
        }
        return virtuals;
    }

    private ClubTrainingSession buildSession(RecurringSessionTemplate t, LocalDate date, boolean virtual) {
        ClubTrainingSession s = new ClubTrainingSession();
        if (virtual) {
            s.setId(VIRTUAL_PREFIX + t.getId() + ":" + date);
        }
        s.setClubId(t.getClubId());
        s.setCreatedBy(t.getCreatedBy());
        s.setScheduledAt(date.atTime(t.getTimeOfDay()));
        s.setRecurringTemplateId(t.getId());
        s.setCreatedAt(LocalDateTime.now());
        SessionPropertyMapper.applyTemplate(t, s);
        return s;
    }

    private void validateOccurrence(RecurringSessionTemplate t, LocalDate date) {
        if (!Boolean.TRUE.equals(t.getActive())) {
            throw new ValidationException("Recurring template is not active");
        }
        if (t.getDayOfWeek() == null || t.getTimeOfDay() == null) {
            throw new ValidationException("Recurring template is missing dayOfWeek or timeOfDay");
        }
        if (date.getDayOfWeek() != t.getDayOfWeek()) {
            throw new ValidationException("Date " + date + " does not match template's dayOfWeek " + t.getDayOfWeek());
        }
        if (t.getEndDate() != null && date.isAfter(t.getEndDate())) {
            throw new ValidationException("Date " + date + " is past template's endDate " + t.getEndDate());
        }
        if (date.isBefore(LocalDate.now().minusDays(1))) {
            throw new ValidationException("Cannot materialize past occurrence: " + date);
        }
    }
}
