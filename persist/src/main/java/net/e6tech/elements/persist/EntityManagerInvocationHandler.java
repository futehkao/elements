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

package net.e6tech.elements.persist;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Resources;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by futeh.
 */
public class EntityManagerInvocationHandler extends Watcher {

    private Resources resources;

    public EntityManagerInvocationHandler(Resources resources, EntityManager em) {
        super(em);
        this.resources = resources;
    }

    protected EntityManagerInvocationHandler(Object target) {
        super(target);
    }

    @Override
    public Object doInvoke(Object proxy, Method method, Object[] args) throws Throwable {

        long start = System.currentTimeMillis();
        Object ret = null;
        try {
            ret = method.invoke(getTarget(), args);
        } catch (InvocationTargetException ex) {
            Logger.suppress(ex);
            throw ex.getCause();
        } finally {
            if (logger.isDebugEnabled() && isMonitorTransaction()) {
                long duration = System.currentTimeMillis() - start;
                Class returnType = method.getReturnType();
                if (ret != null && Query.class.isAssignableFrom(returnType)) {
                    EntityManagerInvocationHandler handler = new EntityManagerInvocationHandler(ret);
                    handler.setLongTransaction(getLongTransaction());
                    handler.setIgnoreInitialLongTransactions(getIgnoreInitialLongTransactions());
                    if (returnType.isInterface()) {
                        ret = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {returnType} , handler);
                    } else {
                        ret = Proxy.newProxyInstance(getClass().getClassLoader(), returnType.getInterfaces(), handler);
                    }
                }

                log(method, args, duration);
            }
        }
        return ret;
    }
}
