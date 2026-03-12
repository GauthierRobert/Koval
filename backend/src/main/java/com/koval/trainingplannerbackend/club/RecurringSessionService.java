package com.koval.trainingplannerbackend.club;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class RecurringSessionService {

    private final RecurringSessionTemplateRepository templateRepository;
    private final ClubTrainingSessionRepository sessionRepository;
    private final ClubService clubService;

    public RecurringSessionService(RecurringSessionTemplateRepository templateRepository,
                                   ClubTrainingSessionRepository sessionRepository,
                                   ClubService clubService) {
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
        this.clubService = clubService;
    }

    public RecurringSessionTemplate createTemplate(String userId, String clubId,
                                                    ClubController.CreateRecurringSessionRequest req) {
        clubService.validateAdminOrCoachAccess(userId, clubId);

        RecurringSessionTemplate template = new RecurringSessionTemplate();
        template.setClubId(clubId);
        template.setCreatedBy(userId);
        template.setTitle(req.title());
        template.setSport(req.sport());
        template.setDayOfWeek(req.dayOfWeek());
        template.setTimeOfDay(req.timeOfDay());
        template.setLocation(req.location());
        template.setDescription(req.description());
        template.setLinkedTrainingId(req.linkedTrainingId());
        template.setMaxParticipants(req.maxParticipants());
        template.setDurationMinutes(req.durationMinutes());
        template.setCreatedAt(LocalDateTime.now());
        template = templateRepository.save(template);

        generateInstances(template, 4);
        return template;
    }

    public RecurringSessionTemplate updateTemplate(String userId, String templateId,
                                                    ClubController.CreateRecurringSessionRequest req) {
        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        clubService.validateAdminOrCoachAccess(userId, template.getClubId());

        template.setTitle(req.title());
        template.setSport(req.sport());
        template.setDayOfWeek(req.dayOfWeek());
        template.setTimeOfDay(req.timeOfDay());
        template.setLocation(req.location());
        template.setDescription(req.description());
        template.setLinkedTrainingId(req.linkedTrainingId());
        template.setMaxParticipants(req.maxParticipants());
        template.setDurationMinutes(req.durationMinutes());
        return templateRepository.save(template);
    }

    public void deactivateTemplate(String userId, String templateId) {
        RecurringSessionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        clubService.validateAdminOrCoachAccess(userId, template.getClubId());
        template.setActive(false);
        templateRepository.save(template);
    }

    public List<RecurringSessionTemplate> listTemplates(String clubId) {
        return templateRepository.findByClubId(clubId);
    }

    public void generateInstances(RecurringSessionTemplate template, int weeksAhead) {
        LocalDate today = LocalDate.now();
        DayOfWeek targetDay = template.getDayOfWeek();

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
            session.setTitle(template.getTitle());
            session.setSport(template.getSport());
            session.setScheduledAt(scheduledAt);
            session.setLocation(template.getLocation());
            session.setDescription(template.getDescription());
            session.setLinkedTrainingId(template.getLinkedTrainingId());
            session.setMaxParticipants(template.getMaxParticipants());
            session.setDurationMinutes(template.getDurationMinutes());
            session.setRecurringTemplateId(template.getId());
            session.setCreatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
    }

    public void generateAllRecurring() {
        List<RecurringSessionTemplate> templates = templateRepository.findByActiveTrue();
        for (RecurringSessionTemplate template : templates) {
            generateInstances(template, 4);
        }
    }
}
