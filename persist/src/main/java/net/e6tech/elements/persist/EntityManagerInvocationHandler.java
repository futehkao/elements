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
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by futeh.
 */
public class EntityManagerInvocationHandler extends Watcher<EntityManager> implements EntityManagerSupport {
    private static Set<Method> supportMethods = new HashSet<>();
    private static Logger logger = Logger.getLogger();

    private Resources resources;
    private String alias;
    private EntityManagerProvider provider;
    private EntityManagerConfig config;
    private InvocationListener<EntityManager> entityManagerListener;
    private InvocationListener<Query> queryListener;
    private EntityManagerExtension proxy;
    private Map<String, Object> context = new HashMap<>();

    static {
        for (Method method : EntityManagerSupport.class.getDeclaredMethods()) {
            supportMethods.add(method);
        }

        for (Method method : EntityManagerInvocationHandler.class.getDeclaredMethods()) {
            supportMethods.add(method);
        }
    }

    public EntityManagerInvocationHandler(Resources resources,
                                          EntityManager em,
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

            Class returnType = method.getReturnType();
            if (EntityTransaction.class.isAssignableFrom(returnType)) {
                TransactionInvocationHandler handler = new TransactionInvocationHandler((EntityManagerInvocationHandler) watcher, (EntityTransaction) ret);
                ret = Proxy.newProxyInstance(watcher.getClass().getClassLoader(), new Class[] {returnType} , handler);
            }
        }
        return ret;
    }

    @Override
    public Object doInvoke(Class callingClass, Object proxy, Method method, Object[] args) throws Throwable {
        if (supportMethods.contains(method))
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

    @Override
    public EntityManagerExtension lockTimeout(long millis) {
        runExtension("setLockTimeout", millis);
        return getProxy();
    }

    @Override
    public long lockTimeout() {
        return (long) runExtension("getLockTimeout");
    }

    @Override
    public Map<String, Object> getContext() {
        return context;
    }

    @Override
    public Object get(String key) {
        return context.get(key);
    }

    @Override
    public EntityManagerExtension put(String key, Object value) {
        context.put(key, value);
        return getProxy();
    }

    @Override
    public EntityManagerExtension remove(String key) {
        context.remove(key);
        return getProxy();
    }

    private EntityManagerExtension getProxy() {
        if (proxy == null)
            proxy = (EntityManagerExtension) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{EntityManager.class, EntityManagerExtension.class}, this);
        return proxy;
    }

    @Override
    public Object runExtension(String extension, Object ... args) {
        Extension ext = provider.getExtensions().get(extension);
        if (ext != null) {
            if (args != null) {
                switch (args.length) {
                    case 0:
                        return ext.call(getProxy());
                    default:
                        Object[] params = new Object[args.length + 1];
                        for (int i = 0; i < params.length; i++) {
                            if (i == 0)
                                params[i] = getProxy();
                            else
                                params[i] = args[i - 1];
                        }
                        return ext.call(params);
                }

            } else {
                return ext.call(getTarget());
            }
        }
        return null;
    }

    public void onCommit() {
        runExtension("onCommit");
    }

    public void onAbort() {
        runExtension("onAbort");
    }

    public void onClose() {
        runExtension("onClose");
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

    public static class TransactionInvocationHandler implements InvocationHandler {
        private EntityTransaction transaction;
        private EntityManagerInvocationHandler handler;
        public TransactionInvocationHandler(EntityManagerInvocationHandler handler, EntityTransaction transaction) {
            this.transaction = transaction;
            this.handler = handler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if (method.getName().equals("commit")) {
                    handler.onCommit();
                } else if (method.getName().equals("rollback")) {
                    handler.onAbort();
                }
            } catch (Exception ex) {
                logger.warn("Error running extension for " + method.getName(), ex);
            } finally {
                try {
                    return method.invoke(transaction, args);
                } catch (InvocationTargetException iex) {
                    throw iex.getTargetException();
                }
            }
        }
    }
}
