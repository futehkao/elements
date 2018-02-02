/*
 * Copyright 2015 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.common.logging;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * This class is not thread safe
 * Created by futeh.
 */
@SuppressWarnings({"squid:S1149", "squid:MethodCyclomaticComplexity"})
public class TimedLogger {

    private static Set<String> includes = Collections.synchronizedSet(new HashSet<>());
    private static Set<String> excludes = Collections.synchronizedSet(new HashSet<>());
    private static String regex;
    private static Stack<StringBuilder> builders = new Stack<>();
    private static long defaultTimeout = 50L;

    private long start = System.currentTimeMillis();
    private LogLevel logLevel = LogLevel.DEBUG;
    private Logger logger;
    private boolean shouldLog;
    private long timeout = defaultTimeout;

    public TimedLogger(String name) {
        logger = Logger.getLogger(name);
    }

    /**
     *
     * @param name name of the Logger
     * @param timeout indicates whether to log depending the value. 0 to disable, meaning log everything.
     *                However, if one calls time, instead of log, the timeout value is effectively 0.
     */
    public TimedLogger(String name, long timeout) {
        this(name);
        this.timeout = timeout;
    }

    /**
     * Constructs a TimedLogger using the default logger named TimedLogger
     * @param timeout indicates wheter to log depending the value. 0 to disable
     */
    public TimedLogger(long timeout) {
        this();
        this.timeout = timeout;
    }

    /**
     * Constructs a TimedLogger using the default logger named TimedLogger
     */
    public TimedLogger() {
        logger = Logger.getLogger("TimedLogger");

        Throwable th = new Throwable();
        StackTraceElement[] trace = th.getStackTrace();
        String thisClassName = getClass().getName();

        int i = 1;

        for (; i < trace.length; i++) {
            if (!thisClassName.equals(trace[i].getClassName()))
                break;
        }

        shouldLog = false;
        if (trace.length > i ) {
            String className = trace[i].getClassName();
            if (regex != null && className.matches(regex)) {
                shouldLog = true;
                if (excludes.contains(className)) {
                    shouldLog = false;
                }
            } else if (excludes.contains(className)) {
                shouldLog = false;
                return;
            }else if (includes.contains(className)) {
                shouldLog = true;
                return;
            }
        }

        if (shouldLog)
            computeLogging();
    }

    public static void setRegex(String pattern) {
        regex = pattern;
    }

    public static void setExcludes(Class ... classes) {
        if (classes != null) {
            for (Class cls : classes)
                excludes.add(cls.getName());
        }
    }

    public static void setIncludes(Class ... classes) {
        if (classes != null) {
            for (Class cls : classes)
                includes.add(cls.getName());
        }
    }

    public static long getDefaultTimeout() {
        return defaultTimeout;
    }

    public static void setDefaultTimeout(long defaultTimeout) {
        TimedLogger.defaultTimeout = defaultTimeout;
    }

    private void computeLogging() {
        switch (logLevel) {
            case FATAL:
            case ERROR:
                if (!logger.isErrorEnabled())
                    shouldLog = false;
                break;
            case WARN:
                if (!logger.isWarnEnabled())
                    shouldLog = false;
                break;
            case INFO:
                if (!logger.isInfoEnabled())
                    shouldLog = false;
                break;
            case DEBUG:
                if (!logger.isDebugEnabled())
                    shouldLog = false;
                break;
            case TRACE:
                shouldLog = true;
                break;
            default:
                shouldLog = true;
                break;
        }
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
        computeLogging();
    }

    public TimedLogger log() {
        return _log("", timeout);
    }

    public TimedLogger log(String message) {
        return _log(message, timeout);
    }

    public TimedLogger time() {
        return _log("", 0);
    }

    public TimedLogger time(String message) {
        return _log(message, 0);
    }

    @SuppressWarnings({"squid:S0010", "squid:S00100", "squid:S2629"})
    private TimedLogger _log(String message, long timeout) {
        long duration = System.currentTimeMillis() - start;
        start = System.currentTimeMillis();
        if (duration <= timeout)
            return this;
        if (!shouldLog)
            return this;

        StringBuilder builder = checkout();
        Thread thread = Thread.currentThread();
        builder.append("Thread[").append(thread.getName()).append("] ");
        getCallerInfo(builder);
        builder.append(": ").append(message).append(" ").append(duration).append("ms");
        switch (logLevel) {
            case FATAL:
            case ERROR:
                logger.error(builder.toString());
                break;
            case WARN:
                logger.warn(builder.toString());
                break;
            case INFO:
                logger.info(builder.toString());
                break;
            case DEBUG:
                logger.debug(builder.toString());
                break;
            case TRACE:
                logger.trace(builder.toString());
                break;
            default:
                break;
        }
        builder.setLength(0);
        checkin(builder);
        return this;
    }

    public long duration() {
        return System.currentTimeMillis() - start;
    }

    public TimedLogger start() {
        start = System.currentTimeMillis();
        return this;
    }

    protected void getCallerInfo(StringBuilder builder) {
        Throwable th = new Throwable();
        StackTraceElement[] trace = th.getStackTrace();
        String thisClassName = getClass().getName();

        int i;
        for (i = 0; i < trace.length; i++) {
            if (thisClassName.equals(trace[i].getClassName()))
                break;
        }
        if (trace.length > i + 3) {
            builder.append(trace[i + 3].getClassName());
            builder.append(".");
            builder.append(trace[i + 3].getMethodName());
            builder.append("(");
            builder.append(trace[i + 3].getFileName());
            builder.append(":");
            builder.append(trace[i + 3].getLineNumber());
            builder.append(")");
        }
    }

    private static StringBuilder checkout() {
        synchronized (builders) {
            if (!builders.isEmpty())
                return builders.pop();
        }
        return new StringBuilder();
    }

    private static void checkin(StringBuilder builder) {
        synchronized (builders) {
            builders.push(builder);
        }
    }
}
