package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.resources.BindClass;
import net.e6tech.elements.common.util.SystemException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * Created by futeh.
 */
@BindClass(Observer.class)
public abstract class Observer implements Cloneable {

    public void beforeInvocation(HttpServletRequest request, HttpServletResponse response, Object instance, Method method, Object[] args) {
    }

    public void afterInvocation(Object result) {
    }

    public void onException(Exception exception) {

    }

    @SuppressWarnings("squid:S2975")
    public Observer clone() {
        try {
            return (Observer) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new SystemException(e);
        }
    }
}
