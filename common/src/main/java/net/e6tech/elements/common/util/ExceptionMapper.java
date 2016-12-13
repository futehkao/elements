package net.e6tech.elements.common.util;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by futeh.
 */
public interface ExceptionMapper {

    static Throwable unwrap(Throwable exception) {
        if (exception instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) exception;
            if (ite.getTargetException() != null) exception = ite.getTargetException();
        }

        while (exception instanceof RuntimeException) {
            Exception runtime = (RuntimeException) exception;
            Throwable th = runtime.getCause();
            if (th == null) break;
            if (th == exception) break;
            exception = th;
        }

        if (exception instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) exception;
            if (ite.getTargetException() != null) exception = ite.getTargetException();
        }

        return exception;
    }

    ErrorResponse toResponse(Throwable exception);

}
