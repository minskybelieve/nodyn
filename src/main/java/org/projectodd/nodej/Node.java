package org.projectodd.nodej;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.dynjs.runtime.DynJS;
import org.dynjs.runtime.ExecutionContext;
import org.dynjs.runtime.GlobalObject;
import org.dynjs.runtime.JSFunction;
import org.projectodd.nodej.bindings.buffer.BufferType;
import org.projectodd.nodej.bindings.timers.ClearTimeout;
import org.projectodd.nodej.bindings.timers.SetTimeout;

public class Node {

    public static final String VERSION = "0.1.0";
    public static final int MAX_THREADS = 100;
    private static final ScheduledExecutorService DISPATCH = Executors.newScheduledThreadPool(MAX_THREADS);

    private DynJS runtime;
    private Process process;
    private String filename = "<eval>";

    public Node(DynJS runtime) {
        System.err.println("Starting NodeJ");
        this.runtime = runtime;
        ExecutionContext context = runtime.getExecutionContext();
        ExecutionContext parent = context.getParent();
        while (parent != null) {
            context = parent;
            parent = context.getParent();
        }
        GlobalObject globalObject = context.getGlobalObject();
        process = new Process(globalObject, null);
        globalObject.defineGlobalProperty("process", new Process(globalObject, null));
        final ClearTimeout clearTimeout = new ClearTimeout(globalObject);
        globalObject.defineGlobalProperty("setTimeout", new SetTimeout(globalObject, false));
        globalObject.defineGlobalProperty("clearTimeout", clearTimeout);
        globalObject.defineGlobalProperty("setInterval", new SetTimeout(globalObject, true));
        globalObject.defineGlobalProperty("clearInterval", clearTimeout);
        globalObject.defineGlobalProperty("Buffer", new BufferType(globalObject));
    }

    public static Future<Object> dispatch(final JSFunction func, final ExecutionContext context, final Object...args) {
        Callable<Object> callable = new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return context.call(func, null, args);
            }
        };
        return DISPATCH.submit(callable);
    }
    
    @SuppressWarnings("unchecked")
    public static Future<Object> dispatchAt(final ExecutionContext context, long delay, boolean repeat, final JSFunction func, final Object...args) {
        if (repeat) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    context.call(func, null, args);
                }
            };
            return (Future<Object>) DISPATCH.scheduleWithFixedDelay(runnable, delay, delay, TimeUnit.MILLISECONDS);
        } else {
            Callable<Object> callable = new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return context.call(func, null, args);
                }
            };
            return DISPATCH.schedule(callable, delay, TimeUnit.MILLISECONDS);
        }
    }
    
    


    // I'm not sure if we really want to expose this or not.
    // At the moment, it's being used for testing
    public DynJS getRuntime() {
        return this.runtime;
    }
    
    // May return null if Node hasn't started
    public Process getProcess() {
        return this.process;
    }
    
    public String getDirname() {
        return System.getProperty("user.dir");
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void execute(File file) {
        try {
            this.setFilename(file.getCanonicalPath());
            this.runtime.newRunner().withSource(file).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
