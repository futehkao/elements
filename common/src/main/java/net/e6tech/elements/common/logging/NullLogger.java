/*
 * Copyright 2017 Futeh Kao
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

import org.slf4j.Marker;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S3878")
public class NullLogger implements org.slf4j.Logger {
    private static final Object[] EMPTY_ARGS = new Object[0];

    @Override
    public String getName() {
        return "NullLogger";
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void trace(String msg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(String format, Object arg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(String format, Object... arguments) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(String msg, Throwable t) {
        info(msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return false;
    }

    @Override
    public void trace(Marker marker, String msg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(Marker marker, String format, Object... arguments) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        info(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void debug(String msg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(String format, Object arg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(String format, Object... arguments) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(String msg, Throwable t) {
        info(msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return false;
    }

    @Override
    public void debug(Marker marker, String msg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        info(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        info(msg, EMPTY_ARGS);
    }

    @Override
    public void info(String format, Object arg) {
        info(format,  new Object[] {arg});
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        info(format, new Object[] {arg1, arg2});
    }

    @Override
    public void info(String format, Object... arguments) {
        // do nothing, it is a NullLogger
    }

    @Override
    public void info(String msg, Throwable t) {
        // do nothing, it is a NullLogger
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return true;
    }

    @Override
    public void info(Marker marker, String msg) {
        info(marker + ": " + msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        info(marker + ": " + format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        info(marker + ": " + format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        info(marker + ": " + format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        info(marker + ": " + msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        info(msg);
    }

    @Override
    public void warn(String format, Object arg) {
        info(format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        info(format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        info(format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        info(msg, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return true;
    }

    @Override
    public void warn(Marker marker, String msg) {
        info(marker, msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        info(marker, format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        info(marker, format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        info(marker, format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        info(marker, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        info(msg);
    }

    @Override
    public void error(String format, Object arg) {
        info(format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        info(format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        info(format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        info(msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return true;
    }

    @Override
    public void error(Marker marker, String msg) {
        info(marker, msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        info(marker, format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        info(marker, format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        info(marker, format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        info(marker, msg, t);
    }
}
