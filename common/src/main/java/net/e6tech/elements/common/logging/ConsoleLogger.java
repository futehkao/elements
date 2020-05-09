/*
 * Copyright 2015-2020 Futeh Kao
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
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintStream;

@SuppressWarnings("squid:S106")
public class ConsoleLogger implements org.slf4j.Logger {

    private PrintStream out = System.out;
    private boolean traceEnabled = false;
    private boolean debugEnabled = false;
    private boolean infoEnabled = true;
    private boolean warnEnabled = true;
    private boolean errorEnabled = true;

    public ConsoleLogger traceEnabled() {
        setTraceEnabled(true);
        return this;
    }

    public ConsoleLogger traceEnabled(boolean t) {
        setTraceEnabled(t);
        return this;
    }

    public ConsoleLogger debugEnabled() {
        setDebugEnabled(true);
        return this;
    }

    public ConsoleLogger debugEnabled(boolean t) {
        traceEnabled = t;
        return this;
    }

    public ConsoleLogger infoEnabled() {
        setInfoEnabled(true);
        return this;
    }

    public ConsoleLogger infoEnabled(boolean t) {
        setInfoEnabled(t);
        return this;
    }

    public ConsoleLogger warnEnabled() {
        setWarnEnabled(true);
        return this;
    }

    public ConsoleLogger warnEnabled(boolean t) {
        setWarnEnabled(t);
        return this;
    }

    public ConsoleLogger errorEnabled() {
        setErrorEnabled(true);
        return this;
    }

    public ConsoleLogger errorEnabled(boolean t) {
        setErrorEnabled(t);
        return this;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void println(String msg) {
        if (out != null)
            out.println(msg);
    }

    public void println(String format, Object arg) {
        if (out != null)
            out.println(MessageFormatter.format(format, arg).getMessage());
    }

    public void println(String format, Object arg1, Object arg2) {
        if (out != null)
            out.println(MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    public void println(String format, Object... arguments) {
        if (out != null)
            out.println(MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    public void println(String msg, Throwable t) {
        if (out != null) {
            println(msg);
            if (t != null) {
                t.printStackTrace(out);
                out.println();
            }
        }
    }

    public void println(Marker marker, String msg) {
        println(marker + ": " + msg);
    }

    public void println(Marker marker, String format, Object arg) {
        println(marker + ": " + format, arg);
    }

    public void println(Marker marker, String format, Object arg1, Object arg2) {
        println(marker + ": " + format, arg1, arg2);
    }

    public void println(Marker marker, String format, Object... arguments) {
        println(marker + ": " + format, arguments);
    }

    public void println(Marker marker, String msg, Throwable t) {
        println(marker + ": " + msg, t);
    }

    @Override
    public String getName() {
        return "ConsoleLogger";
    }

    @Override
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    @Override
    public void trace(String msg) {
        if (isTraceEnabled())
            println(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        if (isTraceEnabled())
            println(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (isTraceEnabled())
            println(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (isTraceEnabled())
            println(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (isTraceEnabled())
            println(msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    @Override
    public void trace(Marker marker, String msg) {
        println(marker, msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        println(marker, format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        println(marker, format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... arguments) {
        println(marker, format, arguments);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        println(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    @Override
    public void debug(String msg) {
        if (isDebugEnabled())
            println(msg);
    }

    @Override
    public void debug(String format, Object arg) {
        if (isDebugEnabled())
            println(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled())
            println(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnabled())
            println(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (isDebugEnabled())
            println(msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    @Override
    public void debug(Marker marker, String msg) {
        if (isDebugEnabled())
            println(marker, msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        if (isDebugEnabled())
            println(marker, format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (isDebugEnabled())
            println(marker, format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        if (isDebugEnabled())
            println(marker, format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        if (isDebugEnabled())
            println(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    public void setInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
    }

    @Override
    public void info(String msg) {
        if (isInfoEnabled())
            println(msg);
    }

    @Override
    public void info(String format, Object arg) {
        if (isInfoEnabled())
            println(format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled())
            println(format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnabled())
            println(format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        if (isInfoEnabled())
            println(msg, t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    @Override
    public void info(Marker marker, String msg) {
        if (isInfoEnabled())
            println(marker, msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        if (isInfoEnabled())
            println(marker, format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (isInfoEnabled())
            println(marker, format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        if (isInfoEnabled())
            println(marker, format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        if (isInfoEnabled())
            println(marker, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return warnEnabled;
    }

    public void setWarnEnabled(boolean warnEnabled) {
        this.warnEnabled = warnEnabled;
    }

    @Override
    public void warn(String msg) {
        if (isWarnEnabled())
            println(msg);
    }

    @Override
    public void warn(String format, Object arg) {
        if (isWarnEnabled())
            println(format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled())
            println(format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnabled())
            println(format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (isWarnEnabled())
            println(msg, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public void warn(Marker marker, String msg) {
        if (isWarnEnabled())
            println(marker, msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        if (isWarnEnabled())
            println(marker, format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (isWarnEnabled())
            println(marker, format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        if (isWarnEnabled())
            println(marker, format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        if (isWarnEnabled())
            println(marker, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return errorEnabled;
    }

    public void setErrorEnabled(boolean errorEnabled) {
        this.errorEnabled = errorEnabled;
    }

    @Override
    public void error(String msg) {
        if (isErrorEnabled())
            println(msg);
    }

    @Override
    public void error(String format, Object arg) {
        if (isErrorEnabled())
            println(format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled())
            println(format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        if (isErrorEnabled())
            println(format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        if (isErrorEnabled())
            println(msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    @Override
    public void error(Marker marker, String msg) {
        if (isErrorEnabled())
            println(marker, msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        if (isErrorEnabled())
            println(marker, format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        if (isErrorEnabled())
            println(marker, format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        if (isErrorEnabled())
            println(marker, format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        if (isErrorEnabled())
            println(marker, msg, t);
    }
}
