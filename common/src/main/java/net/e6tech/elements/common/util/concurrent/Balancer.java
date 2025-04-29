/*
 * Copyright 2015-2021 Futeh Kao
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
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.function.FunctionWithException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Created by futeh.
 */
public class Balancer<T> {
    private static Logger logger= Logger.getLogger();
    private List<T> services = new ArrayList<>();
    private BlockingQueue<T> liveList = new LinkedBlockingQueue<>();
    private final Set<T> processingSet = Collections.synchronizedSet(new HashSet<>());

    private BlockingQueue<T>  deadList = new LinkedBlockingQueue<>();
    private long timeout = 3000L;
    private long recoveryPeriod = 60000L;
    private Thread recoveryThread;
    private volatile boolean stopped = false;
    private boolean threadSafe = false;
    private ServiceHandler<T> starter;
    private ServiceHandler<T> stopper;

    @SuppressWarnings({"unchecked"})
    public T getService() {
        Class cls = Reflection.getParametrizedType(getClass(), 0);
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { cls },
                (proxy,  method, args)-> execute(service -> method.invoke(service, args)));
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Balancer<T> timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    public long getRecoveryPeriod() {
        return recoveryPeriod;
    }

    public void setRecoveryPeriod(long recoveryPeriod) {
        this.recoveryPeriod = recoveryPeriod;
    }

    public Balancer<T> recoveryPeriod(long recoveryPeriod) {
        setRecoveryPeriod(recoveryPeriod);
        return this;
    }

    public boolean isThreadSafe() {
        return threadSafe;
    }

    public void setThreadSafe(boolean threadSafe) {
        this.threadSafe = threadSafe;
    }

    public Balancer<T> threadSafe(boolean threadSafe) {
        setThreadSafe(threadSafe);
        return this;
    }

    public void addService(T service) {
        liveList.add(service);
        services.add(service);
    }

    public void forEach(Consumer<T> consumer) {
        services.forEach(consumer);
    }

    public Balancer<T> timeout(T service) {
        addService(service);
        return this;
    }

    public int getAvailable() {
        return liveList.size();
    }

    public int getProcessingCount() {
        return processingSet.size();
    }

    public ServiceHandler<T> getStarter() {
        return starter;
    }

    public void setStarter(ServiceHandler<T> starter) {
        this.starter = starter;
    }

    public Balancer<T> starter(ServiceHandler<T> starter) {
        setStarter(starter);
        return this;
    }

    public ServiceHandler<T> getStopper() {
        return stopper;
    }

    public void setStopper(ServiceHandler<T> stopper) {
        this.stopper = stopper;
    }

    public Balancer<T> stopper(ServiceHandler<T> stopper) {
        setStopper(stopper);
        return this;
    }

    public void start() {
        Iterator<T> iterator = liveList.iterator();
        stopped = false;
        while (iterator.hasNext()) {
            T service = iterator.next();
            try {
               start(service);
            } catch (Exception th) {
                logger.warn("Cannot start service " + service.getClass(), th);
                iterator.remove();
                recover(service);
            }
        }
    }

    public void stop() {
        stopped = true;
    }

    protected void start(T service) throws IOException {
        if (starter != null)
            starter.handle(service);
        if (service instanceof BalancerAware)
            ((BalancerAware) service).start();
    }

    protected void stop(T service) throws IOException {
        if (stopper != null)
            stopper.handle(service);
        if (service instanceof BalancerAware)
            ((BalancerAware) service).stop();
    }

    @SuppressWarnings("squid:S899")
    private void recoverTask() {
        try {
            recovering();
        } finally {
            synchronized (this) {
                recoveryThread = null;
            }
        }
    }

    @SuppressWarnings("squid:S899")
    private void recovering() {
        while (!stopped) {
            T service = null;
            try {
                service = deadList.take();
                start(service);
                liveList.offer(service);
            } catch (Exception ex) {
                if (service != null) {
                    logger.warn("Cannot restart service " + service.getClass(), ex);
                    try {
                        stop(service);
                    } catch (Exception e) {
                        logger.warn("Cannot restart service " + service.getClass(), ex);
                    }
                    deadList.offer(service);
                }
            }

            try {
                Thread.sleep(recoveryPeriod);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @SuppressWarnings("squid:S899")
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

    @SuppressWarnings({"squid:S3776", "squid:S899", "squid:S1193"})
    public <R> R execute(FunctionWithException<T, R, Exception> submit) throws IOException {
        while (true) {  // the while loop is for in case of IOException
            T service;
            boolean owner = false;
            try {
                service = liveList.poll(timeout, TimeUnit.MILLISECONDS);
                if (service != null) {
                    processingSet.add(service);
                    owner = true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException();
            }

            if (service == null && threadSafe) {
                synchronized (processingSet) {
                    service = processingSet.iterator().hasNext() ? processingSet.iterator().next() : null;
                }
            }

            if (service == null)
                throw new IOException("No service available");

            SystemException error = null;
            boolean recovery = false;
            try {
                return submit.apply(service);
            } catch (Exception ex) {
                if (shouldRecover(ex)) {
                    recovery = true;
                } else {
                    if (ex instanceof SystemException) {
                        error = (SystemException) ex;
                    } else if (ex instanceof InvocationTargetException) {
                        error = new SystemException(ex.getCause());
                    } else if (ex instanceof RuntimeException) {
                        error = new SystemException(ex.getCause());
                    } else {
                        error = new SystemException(ex);
                    }
                }
            } finally {
                if (owner) {
                    processingSet.remove(service);  // this needs to happened before adding it back to liveList so that
                                                    // other threads can't pick up from liveList and attempt to add it
                                                    // to processingSet.
                    // we only add back to liveList if it's an error that we don't want to recover.
                    if (recovery) {
                        recover(service);
                    } else {
                        liveList.offer(service);
                    }
                }
            }

            if (error != null)
                throw error;
        }
    }

    protected boolean shouldRecover(Exception exception) {
        Throwable throwable = ExceptionMapper.unwrap(exception);
        return throwable instanceof IOException;
    }

    // provides a way for an external system to start or to stop a service.
    public interface ServiceHandler<T> {
        void handle(T t) throws IOException;
    }
}
