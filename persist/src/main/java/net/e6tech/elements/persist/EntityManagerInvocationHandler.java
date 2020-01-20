/*
Copyright 2015-2019 Futeh Kao

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
import java.util.HashSet;
import java.util.Set;

/**
 * Created by futeh.
 */
public class EntityManagerInvocationHandler extends Watcher<EntityManager> implements EntityManagerInfo {
    private static Set<Method> infoMethods = new HashSet<>();

    private Resources resources;
    private String alias;
    private EntityManagerProvider provider;
    private EntityManagerConfig config;
    private InvocationListener<EntityManager> entityManagerListener;
    private InvocationListener<Query> queryListener;

    static {
        for (Method method : EntityManagerInfo.class.getDeclaredMethods()) {
            infoMethods.add(method);
        }

        for (Method method : EntityManagerInvocationHandler.class.getDeclaredMethods()) {
            infoMethods.add(method);
        }
    }

    public EntityManagerInvocationHandler(Resources resources, EntityManager em,
                                          String alias,
                                          EntityManagerProvider provider,
                                          EntityManagerConfig config,
                                          InvocationListener<EntityManager> entityManagerListener,
                                          InvocationListener<Query> queryListener) {
        super(em);
        this.resources = resources;
        this.alias = alias;
        this.provider = provider;
        this.config = config;
        this.entityManagerListener = entityManagerListener;
        this.queryListener = queryListener;
    }

    @SuppressWarnings({"unchecked", "squid:S00112"})
    private static Object doInvoke(Class callingClass, Watcher watcher, InvocationListener listener, InvocationListener<Query> queryListener, Object proxy, Method method, Object[] args) throws Throwable {
        long start = System.currentTimeMillis();
        Object ret = null;
        try {
            if (listener != null)
                listener.beforeInvocation(callingClass, proxy, method, args);
            ret = method.invoke(watcher.getTarget(), args);
            if (listener != null)
                listener.afterInvocation(callingClass, proxy, method, args, ret);
        } catch (InvocationTargetException ex) {
            Logger.suppress(ex);
            if (listener != null)
                listener.onException(callingClass, proxy, method, args, ex.getTargetException());
            throw ex.getCause();
        } finally {
            if (logger.isDebugEnabled() && watcher.isMonitorTransaction()) {
                long duration = System.currentTimeMillis() - start;
                Class returnType = method.getReturnType();
                if (ret != null && Query.class.isAssignableFrom(returnType)) {
                    QueryInvocationHandler handler = new QueryInvocationHandler((Query) ret, queryListener);
                    handler.listener = queryListener;
                    handler.setLongTransaction(watcher.getLongTransaction());
                    handler.setIgnoreInitialLongTransactions(watcher.getIgnoreInitialLongTransactions());
                    if (returnType.isInterface()) {
                        ret = Proxy.newProxyInstance(watcher.getClass().getClassLoader(), new Class[] {returnType} , handler);
                    } else {
                        ret = Proxy.newProxyInstance(watcher.getClass().getClassLoader(), returnType.getInterfaces(), handler);
                    }
                }
                watcher.log(method, args, duration);
            }
        }
        return ret;
    }

    @Override
    public Object doInvoke(Class callingClass, Object proxy, Method method, Object[] args) throws Throwable {
        if (infoMethods.contains(method))
            return method.invoke(this, args);
        return doInvoke(callingClass, this, entityManagerListener, queryListener, proxy, method, args);
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public EntityManagerProvider getProvider() {
        return provider;
    }

    @Override
    public EntityManagerConfig getConfig() {
        return config;
    }

    public static class QueryInvocationHandler extends Watcher<Query> {
        private InvocationListener<Query> listener;

        public QueryInvocationHandler(Query target, InvocationListener<Query> listener) {
            super(target);
            this.listener = listener;
        }

        @Override
        public Object doInvoke(Class callingClass, Object proxy, Method method, Object[] args) throws Throwable {
            return EntityManagerInvocationHandler.doInvoke(callingClass, this, listener, listener, proxy, method, args);
        }
    }
}
