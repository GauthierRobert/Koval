package com.koval.trainingplannerbackend.notification;

import lombok.Getter;
import lombok.Setter;

/**
 * Embedded document holding per-user notification preferences.
 */
@Getter
@Setter
public class NotificationPreferences {

    private boolean workoutAssigned = true;
    private boolean workoutReminder = true;
    private boolean workoutCompletedCoach = true;
    private boolean clubSessionCreated = true;
    private boolean clubSessionCancelled = true;
    private boolean waitingListPromoted = true;
    private boolean planActivated = true;
    private boolean clubAnnouncement = true;
}
