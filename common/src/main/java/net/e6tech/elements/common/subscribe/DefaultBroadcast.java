/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.common.subscribe;

import net.e6tech.elements.common.logging.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Created by futeh.
 */
public class DefaultBroadcast implements Broadcast {

    Logger logger = Logger.getLogger();
    Map<String, List<Subscriber>> subscribers = new HashMap<>();
    Map<String, List<Subscriber>> copy = new HashMap<>();
    Executor executor;
    Supplier<Executor> executorSupplier = () -> runnable -> new Thread(runnable).start();

    public DefaultBroadcast() {
    }

    public DefaultBroadcast(Executor executor) {
        this.executor = executor;
    }

    public DefaultBroadcast(Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
    }


    public void setExecutor(Executor ex) {
        this.executor = ex;
    }

    @Override
    public <T extends Serializable> void subscribe(String topic, Subscriber<T> subscriber) {
        List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new LinkedList<>());
        synchronized (list) {
            list.add(subscriber);
            List<Subscriber> copyList = new LinkedList<>(list);
            copy.put(topic, copyList);
        }
    }

    @Override
    public <T extends Serializable> void unsubscribe(String topic, Subscriber<T> subscriber) {
        List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new LinkedList<>());
        synchronized (list) {
            list.remove(subscriber);
            List<Subscriber> copyList = new LinkedList<>(list);
            copy.put(topic, copyList);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Serializable> void publish(Notice<T> notice) {
        if (notice.isExternalOnly())
            return;
        Executor exec = executor;

        if (exec == null)
            exec = executorSupplier.get();
        exec.execute(()-> {
            try {
                List<Subscriber> list = subscribers.computeIfAbsent(notice.getTopic(), key -> new LinkedList<>());
                synchronized (list) {
                    list = copy.computeIfAbsent(notice.getTopic(), key -> new LinkedList<>());
                }
                for (Subscriber subscriber : list) {
                    subscriber.receive(notice);
                }
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        });
    }
}
