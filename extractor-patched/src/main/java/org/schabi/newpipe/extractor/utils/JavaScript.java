package org.schabi.newpipe.extractor.utils;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;
import java.util.concurrent.CancellationException;

/** NewPipe's Rhino adapter, with modern lexical scope and interruptible execution. GPL-3.0-or-later. */
public final class JavaScript {
    private JavaScript() { }
    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override protected Context makeContext() {
            Context context = super.makeContext();
            context.setInterpretedMode(true);
            // The current player requires modern JavaScript syntax.
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setInstructionObserverThreshold(10000);
            context.setClassShutter(name -> false);
            return context;
        }
        @Override protected void observeInstructionCount(Context context, int count) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("JavaScript interrupted");
        }
    };
    public static void compileOrThrow(String function) {
        try (Context context = FACTORY.enterContext()) {
            context.compileString(function, null, 1, null);
        }
    }
    public static String run(String function, String functionName, String... parameters) {
        try (Context context = FACTORY.enterContext()) {
            ScriptableObject scope = context.initSafeStandardObjects();
            context.evaluateString(scope, function, functionName, 1, null);
            Function callable = (Function) scope.get(functionName, scope);
            return Context.toString(callable.call(context, scope, scope, parameters));
        }
    }
}
