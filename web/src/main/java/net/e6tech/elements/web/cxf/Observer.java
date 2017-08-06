package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.resources.BindClass;
import net.e6tech.elements.common.util.SystemException;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * Created by futeh.
 */
@BindClass(Observer.class)
public abstract class Observer implements Cloneable {

    public void service(HttpServletRequest request) {
    }

    public void beforeInvocation(Object instance, Method method, Object[] args) {
    }

    public void afterInvocation(Object result) {
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
