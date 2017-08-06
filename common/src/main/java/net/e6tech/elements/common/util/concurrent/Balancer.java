/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.common.util.concurrent;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.util.SystemException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by futeh.
 */
public abstract class Balancer<T> {

    private BlockingQueue<T> liveList = new LinkedBlockingQueue<>();
    private BlockingQueue<T>  deadList = new LinkedBlockingQueue<>();
    private long timeout = 3000L;
    private long recoveryPeriod = 60000L;
    private Thread recoveryThread;
    private volatile boolean stopped = false;

    @SuppressWarnings({"unchecked"})
    public T getService() {
        Class cls = Reflection.getParametrizedType(getClass(), 0);
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { cls },
                (proxy,  method, args)->
                     execute(service -> {
                        try {
                            return method.invoke(service, args);
                        } catch (IllegalAccessException e) {
                            throw new SystemException(e);
                        } catch (InvocationTargetException e) {
                            Logger.suppress(e);
                            throw new SystemException(e.getCause());
                        }
                    })
                );
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getRecoveryPeriod() {
        return recoveryPeriod;
    }

    public void setRecoveryPeriod(long recoveryPeriod) {
        this.recoveryPeriod = recoveryPeriod;
    }

    public void addService(T service) {
        liveList.add(service);
    }

    public void start() {
        Iterator<T> iterator = liveList.iterator();
        stopped = false;
        while (iterator.hasNext()) {
            T service = iterator.next();
            try {
               start(service);
            } catch (Exception th) {
                Logger.suppress(th);
                iterator.remove();
                recover(service);
            }
        }
    }

    public void stop() {
        stopped = true;
    }

    protected abstract void start(T service) throws IOException;

    protected abstract void stop(T service) throws IOException;

    private void recoverTask() {
        List<T> list = new LinkedList<>();
        while (!stopped) {
            list.clear();
            T service = deadList.poll();
            try {
                start(service);
                liveList.offer(service);
            } catch (Exception ex) {
                Logger.suppress(ex);
                try {
                    stop(service);
                } catch (Exception e) {
                    Logger.suppress(e);
                }
                list.add(service);
            }

            list.forEach(h -> deadList.offer(h));
            try {
                Thread.sleep(recoveryPeriod);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    protected synchronized void recover(T service) {
        try {
            stop(service);
        } catch (Exception e) {
            Logger.suppress(e);
        }
        deadList.offer(service);
        if (recoveryThread == null) {
            recoveryThread = new Thread(this::recoverTask);
            recoveryThread.start();
        }
    }

    public <R> R execute(Submit<T, R> submit) throws IOException {
        while (true) {
            T service;
            try {
                service = liveList.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException();
            }

            try {
                R ret = submit.apply(service);
                liveList.offer(service);
                return ret;
            } catch (IOException ex) {
                Logger.suppress(ex);
                recover(service);
            }
        }
    }

    @FunctionalInterface
    public interface Submit<T, R> {
        R apply(T t) throws IOException;
    }

}
