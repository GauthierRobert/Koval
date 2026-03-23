package com.koval.trainingplanner.domain.model

enum class UserRole { ATHLETE, COACH }

enum class ScheduleStatus { PENDING, COMPLETED, SKIPPED }

enum class SportType { CYCLING, RUNNING, SWIMMING, BRICK }

enum class TrainingType {
    VO2MAX, THRESHOLD, SWEET_SPOT, ENDURANCE, SPRINT, RECOVERY, MIXED, TEST
}

enum class BlockType {
    WARMUP, STEADY, INTERVAL, COOLDOWN, RAMP, FREE, PAUSE
}

enum class SseEventType {
    CONTENT, CONVERSATION_ID, TOOL_CALL, TOOL_RESULT, STATUS;

    companion object {
        fun fromString(s: String): SseEventType =
            entries.find { it.name.equals(s.replace("-", "_"), ignoreCase = true) } ?: CONTENT
    }
}
