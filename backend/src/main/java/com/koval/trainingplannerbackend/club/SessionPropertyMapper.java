package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.club.dto.CreateRecurringSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility that copies shared properties onto a ClubTrainingSession
 * from either a RecurringSessionTemplate or a CreateSessionRequest.
 */
public final class SessionPropertyMapper {

    private SessionPropertyMapper() {}

    public static void applyTemplate(RecurringSessionTemplate template, ClubTrainingSession session) {
        session.setTitle(template.getTitle());
        session.setSport(template.getSport());
        session.setLocation(template.getLocation());
        session.setDescription(template.getDescription());
        session.setLinkedTrainingId(template.getLinkedTrainingId());
        if (template.getLinkedTrainings() != null && !template.getLinkedTrainings().isEmpty()) {
            List<GroupLinkedTraining> copy = new ArrayList<>();
            for (GroupLinkedTraining src : template.getLinkedTrainings()) {
                GroupLinkedTraining dest = new GroupLinkedTraining();
                dest.setClubGroupId(src.getClubGroupId());
                dest.setClubGroupName(src.getClubGroupName());
                dest.setTrainingId(src.getTrainingId());
                dest.setTrainingTitle(src.getTrainingTitle());
                dest.setTrainingDescription(src.getTrainingDescription());
                copy.add(dest);
            }
            session.setLinkedTrainings(copy);
        }
        session.setMaxParticipants(template.getMaxParticipants());
        session.setDurationMinutes(template.getDurationMinutes());
        session.setClubGroupId(template.getClubGroupId());
        session.setOpenToAll(template.isOpenToAll());
        session.setOpenToAllDelayValue(template.getOpenToAllDelayValue());
        session.setOpenToAllDelayUnit(template.getOpenToAllDelayUnit());
        session.setResponsibleCoachId(template.getResponsibleCoachId());
    }

    public static void applyRequest(CreateRecurringSessionRequest req, RecurringSessionTemplate template) {
        template.setTitle(req.title());
        template.setSport(req.sport());
        template.setDayOfWeek(req.dayOfWeek());
        template.setTimeOfDay(req.timeOfDay());
        template.setLocation(req.location());
        template.setDescription(req.description());
        template.setLinkedTrainingId(req.linkedTrainingId());
        template.setMaxParticipants(req.maxParticipants());
        template.setDurationMinutes(req.durationMinutes());
        template.setClubGroupId(req.clubGroupId());
        template.setOpenToAll(req.openToAll() == null || req.openToAll());
        template.setOpenToAllDelayValue(req.openToAllDelayValue());
        template.setOpenToAllDelayUnit(req.openToAllDelayUnit());
        template.setResponsibleCoachId(req.responsibleCoachId());
    }

    public static void applyRequest(CreateSessionRequest req, ClubTrainingSession session) {
        session.setTitle(req.title());
        session.setSport(req.sport());
        session.setScheduledAt(req.scheduledAt());
        session.setLocation(req.location());
        session.setDescription(req.description());
        session.setLinkedTrainingId(req.linkedTrainingId());
        session.setMaxParticipants(req.maxParticipants());
        session.setDurationMinutes(req.durationMinutes());
        session.setClubGroupId(req.clubGroupId());
        session.setOpenToAll(req.openToAll() != null && req.openToAll());
        session.setOpenToAllDelayValue(req.openToAllDelayValue());
        session.setOpenToAllDelayUnit(req.openToAllDelayUnit());
        session.setResponsibleCoachId(req.responsibleCoachId());
    }
}
