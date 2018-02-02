/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.common.logging;

import net.e6tech.elements.common.reflection.Reflection;
import org.apache.logging.log4j.ThreadContext;

import java.lang.reflect.Proxy;

/**
 * Created by futeh.
 *
 * IMPORTANT: must not declare an instance method in this interface.
 *
 * Usage:
 * Logger logger = Logger.getLogger();
 * To throw a RuntimeException and to log it:
 * throw new logger.systemException(msg, ex);
 * The default log level is ERROR.
 *
 * To change the log level, e.g.
 * logger2 = logger.exceptionLogger(LogLevel.INFO)
 * logger2.systemException ...
 *
 */
@SuppressWarnings({"squid:S2176", "squid:S00115", "squid:S1214"})
public interface Logger extends org.slf4j.Logger, LoggerExtension {

    public static final String logDir = "elements.common.logging.logDir";

    static void suppress(Throwable th) {
        // do nothing
    }

    static void put(String name, String value) {
        ThreadContext.put(name, value);
    }

    static String get(String name) {
        return ThreadContext.get(name);
    }

    static Logger getLogger() {
        Class cls = Reflection.getCallingClass();
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[]{Logger.class},
                new LogHandler(cls));
    }

    static Logger getLogger(Class cls) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[] {Logger.class},
                new LogHandler(cls));
    }

    static Logger getLogger(String name) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[] {Logger.class},
                new LogHandler(name));
    }

    static Logger nullLogger() {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[] {Logger.class},
                new LogHandler(new NullLogger()));
    }
}
