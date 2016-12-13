package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.resources.BindClass;

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

    public Observer clone() {
        try {
            return (Observer) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
