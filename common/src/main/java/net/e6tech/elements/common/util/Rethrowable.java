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
package net.e6tech.elements.common.util;


import java.lang.reflect.Constructor;

/**
 * Created by futeh.
 */
public interface Rethrowable {

    default RuntimeException runtimeException(Throwable e) {
        return runtimeException(e.getMessage(), e);
    }

    default RuntimeException runtimeException(String msg) {
        return runtimeException(msg, null);
    }

    default RuntimeException runtimeException(String msg, Throwable th) {
        RuntimeException e = null;
        if (th == null) e = new RuntimeException(msg);
        if (th == null) return e;
        return new RuntimeException(th);
    }

    default <T extends Throwable> T  exception(Class<T> exceptionClass, Throwable e) {
        return exception(exceptionClass, e.getMessage(), e);
    }

    default <T extends Throwable> T  exception(Class<T> exceptionClass, String msg, Throwable e) {
        Constructor constructor = null;
        T th = null;
        try {
            constructor = exceptionClass.getConstructor(Throwable.class);
            th = (T) constructor.newInstance(e);
        } catch (Throwable ex) {
            e.printStackTrace();
        }

        if (th == null) {
            try {
                constructor = exceptionClass.getConstructor(String.class);
                th = (T) constructor.newInstance(msg);
            } catch (Throwable ex) {
            }
        }

        if (constructor == null) {
            try {
                constructor = exceptionClass.getConstructor();
                th = (T) constructor.newInstance();
            } catch (Throwable ex) {
            }
        }
        return th;
    }
}
