package com.koval.trainingplannerbackend.notification;

/**
 * Embedded document holding per-user notification preferences.
 */
public record NotificationPreferences(
        Boolean workoutAssigned,
        Boolean workoutReminder,
        Boolean workoutCompletedCoach,
        Boolean clubSessionCreated,
        Boolean clubSessionCancelled,
        Boolean waitingListPromoted,
        Boolean planActivated,
        Boolean clubAnnouncement,
        Boolean openSessionCreated
) {
    public NotificationPreferences {
        workoutAssigned = workoutAssigned != null ? workoutAssigned : true;
        workoutReminder = workoutReminder != null ? workoutReminder : true;
        workoutCompletedCoach = workoutCompletedCoach != null ? workoutCompletedCoach : true;
        clubSessionCreated = clubSessionCreated != null ? clubSessionCreated : true;
        clubSessionCancelled = clubSessionCancelled != null ? clubSessionCancelled : true;
        waitingListPromoted = waitingListPromoted != null ? waitingListPromoted : true;
        planActivated = planActivated != null ? planActivated : true;
        clubAnnouncement = clubAnnouncement != null ? clubAnnouncement : true;
        openSessionCreated = openSessionCreated != null ? openSessionCreated : true;
    }

    public NotificationPreferences() {
        this(true, true, true, true, true, true, true, true, true);
    }
}
