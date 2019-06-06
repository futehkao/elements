package net.e6tech.elements.common.util;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by futeh.
 */
public interface ExceptionMapper<R> {

    @SuppressWarnings("squid:S135")
    static Throwable unwrap(Throwable throwable) {
        Throwable exception = throwable;
        if (exception instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) exception;
            if (ite.getTargetException() != null)
                exception = ite.getTargetException();
        }

        while (exception instanceof RuntimeException) {
            Exception runtime = (RuntimeException) exception;
            Throwable th = runtime.getCause();
            if (th == null)
                break;
            if (th == exception)
                break;
            exception = th;
        }

        if (exception instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) exception;
            if (ite.getTargetException() != null)
                exception = ite.getTargetException();
        }

        return exception;
    }

    Class<R> errorResponseClass();

    R toResponse(Throwable exception);

    default Throwable fromResponse(R response) {
        return null;
    }

}
