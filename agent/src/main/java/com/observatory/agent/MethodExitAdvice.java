package com.observatory.agent;

import net.bytebuddy.asm.Advice;

/**
 * Advice woven into the END of every instrumented method.
 *
 * Fires on both normal return and thrown exception paths.
 * On return: captures the return value and pops the frame from our tracker.
 * When the outermost tracked frame returns (stack depth = 0), we emit the
 * full JSON snapshot to stdout.
 */
public class MethodExitAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Origin("#t")             String    className,
            @Advice.Origin("#m")             String    methodName,
            @Advice.Return(typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC, readOnly = true)
                                             Object    returnValue,
            @Advice.Thrown(readOnly = true)  Throwable thrown
    ) {
        try {
            String retStr = (thrown != null)
                ? "<threw: " + thrown.getClass().getSimpleName() + ": " + thrown.getMessage() + ">"
                : MethodEntryAdvice.safeToString(returnValue);

            CallStackTracker.popFrame(className, methodName, retStr);

        } catch (Throwable t) {
            System.err.println("[Agent] Exit advice error: " + t.getMessage());
        }
    }
}
