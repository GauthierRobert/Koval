package com.koval.trainingplannerbackend.ai.tools.action;

/**
 * Thread-local tracker to detect whether an AI tool was actually invoked
 * during a one-shot action. Used to distinguish "AI completed the task"
 * from "AI is asking a clarifying question".
 */
public final class ActionToolTracker {

    private static final ThreadLocal<Boolean> TOOL_CALLED = ThreadLocal.withInitial(() -> false);

    private ActionToolTracker() {}

    public static void reset() { TOOL_CALLED.set(false); }
    public static void markCalled() { TOOL_CALLED.set(true); }
    public static boolean wasCalled() { return TOOL_CALLED.get(); }
}
