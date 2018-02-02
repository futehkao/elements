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

import net.e6tech.elements.common.util.SystemException;

import java.lang.reflect.Constructor;

/**
 * Created by futeh.
 */
public interface LoggerExtension {

    Logger logger(LogLevel level);

    void log(String msg, Throwable e);

    Logger log(LogLevel l, String msg, Throwable e);

    /**
     * Creates a SystemException to wrap a Throwable
     * @param e throwable to be wrapped
     * @return SystemException
     */
    default SystemException systemException(Throwable e) {
        return systemException(e.getMessage(), e);
    }

    default SystemException systemException(String msg) {
        return systemException(msg, null);
    }

    default SystemException systemException(String msg, Throwable th) {
        if (th == null)
            return new SystemException(msg);
        return new SystemException(msg, th);
    }

    default <T extends Throwable> T exception(Class<T> exceptionClass, Throwable e) {
        return exception(exceptionClass, e.getMessage(), e);
    }

    /**
     * Creating a exception with msg and throwable
     * @param exceptionClass class of exception to be created
     * @param msg message
     * @param e   cause
     * @param <T> type of throwable
     * @return exception instance
     */
    default <T extends Throwable> T  exception(Class<T> exceptionClass, String msg, Throwable e) {
        Constructor constructor = null;
        T th = null;
        try {
            constructor = exceptionClass.getConstructor(Throwable.class);
            th = (T) constructor.newInstance(e);
        } catch (Exception ex) {
            Logger.suppress(ex);
        }

        if (th == null) {
            try {
                constructor = exceptionClass.getConstructor(String.class);
                th = (T) constructor.newInstance(msg);
            } catch (Exception ex) {
                Logger.suppress(ex);
            }
        }

        if (constructor == null) {
            try {
                constructor = exceptionClass.getConstructor();
                th = (T) constructor.newInstance();
            } catch (Exception ex) {
                Logger.suppress(ex);
            }
        }
        return th;
    }
}
