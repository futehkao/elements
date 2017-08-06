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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S00112", "squid:S1149"})
public class Watcher implements InvocationHandler {
    protected static Logger logger = Logger.getLogger();
    private static ThreadLocal<Stack<Long>> gracePeriod = new ThreadLocal<>();

    private Object target;
    private boolean monitorTransaction = true;
    private long longTransaction = 200L;
    private AtomicInteger ignoreInitialLongTransactions;

    public Watcher(Object target) {
        this.target = target;
    }

    // disable long transaction monitoring if time is greater than longTransaction
    public static void addGracePeriod(long time) {
        if (!logger.isDebugEnabled())
            return;
        Stack<Long> stack = gracePeriod.get();
        if (stack == null)
            return;
        for (int i = 0; i < stack.size(); i++) {
            long existing = stack.get(i);
            stack.set(i, existing + time);
        }
    }

    protected static long getGracePeriod() {
        if (!logger.isDebugEnabled())
            return 0l;
        Stack<Long> stack = gracePeriod.get();
        if (stack == null)
            return 0L;
        return stack.peek();
    }

    protected static void clearGracePeriod() {
        if (!logger.isDebugEnabled())
            return;
        Stack<Long> stack = gracePeriod.get();
        if (stack != null) {
            stack.pop();
            if (stack.isEmpty()) {
                gracePeriod.remove();
            }
        }
    }

    protected static void initGracePeriod() {
        if (!logger.isDebugEnabled())
            return;
        Stack<Long> stack = gracePeriod.get();
        if (stack == null) {
            stack = new Stack<>();
            gracePeriod.set(stack);
        }
        stack.push(0l);
    }

    public long getLongTransaction() {
        return longTransaction;
    }

    public void setLongTransaction(long longTransaction) {
        this.longTransaction = longTransaction;
    }

    public AtomicInteger getIgnoreInitialLongTransactions() {
        return ignoreInitialLongTransactions;
    }

    public void setIgnoreInitialLongTransactions(AtomicInteger ignoreInitialLongTransactions) {
        this.ignoreInitialLongTransactions = ignoreInitialLongTransactions;
    }

    public Object getTarget() {
        return target;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public boolean isMonitorTransaction() {
        return monitorTransaction;
    }

    public void setMonitorTransaction(boolean monitorTransaction) {
        this.monitorTransaction = monitorTransaction;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            initGracePeriod();
            return doInvoke(proxy, method, args);
        } finally {
            clearGracePeriod();
        }
    }

    @SuppressWarnings("squid:S1172")
    public Object doInvoke(Object proxy, Method method, Object[] args) throws Throwable {

        long start = System.currentTimeMillis();
        try {
            return method.invoke(target, args);
        } catch(InvocationTargetException ex) {
            Logger.suppress(ex);
            throw ex.getCause();
        } finally {
            long duration = System.currentTimeMillis() - start;
            log(method, args, duration);
        }
    }

    @SuppressWarnings("squid:S1172")
    protected void log(Method method, Object[] args, long duration) {
        if (!logger.isDebugEnabled() || duration < longTransaction + getGracePeriod()) {
            if (duration >= longTransaction) {
                System.currentTimeMillis();
            }
            return;
        }

        if (ignoreInitialLongTransactions != null) {
            int left = ignoreInitialLongTransactions.decrementAndGet();
            if (left >= 0) {
                return;
            }
        }

        Throwable th = new Throwable();
        StackTraceElement[] trace = th.getStackTrace();
        StringBuilder builder = new StringBuilder();
        builder.append("Long transaction: " + duration + "ms. Method called=" + method.getName() + "\n");

        for (int i = 3; i < 20; i++) {
            if (i == trace.length)
                break;
            builder.append("\tat " + trace[i] + "\n");
        }
        if (trace.length > 20)
            builder.append("...\n");
        logger.debug(builder.toString());

    }
}
