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
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh.
 */
public class DefaultBroadcast implements Broadcast {

    Logger logger = Logger.getLogger();
    Map<String, List<Subscriber>> subscribers = new HashMap<>();
    Map<String, List<Subscriber>> copy = new HashMap<>();
    ExecutorService threadPool;

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new LinkedList<Subscriber>());
        synchronized (list) {
            list.add(subscriber);
            List<Subscriber> copyList = new LinkedList<>(list);
            copy.put(topic, copyList);
        }
    }

    @Override
    public <T extends Serializable> void subscribe(Class<T> topic, Subscriber<T> listener) {
        subscribe(topic.getName(), listener);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new LinkedList<Subscriber>());
        synchronized (list) {
            list.remove(subscriber);
            List<Subscriber> copyList = new LinkedList<>(list);
            copy.put(topic, copyList);
        }
    }

    @Override
    public void unsubscribe(Class topic, Subscriber subscriber) {
        unsubscribe(topic.getName(), subscriber);
    }

    @Override
    public void publish(String topic, Serializable object) {
        threadPool.execute(()-> {
            try {
                Notice notice = new Notice(topic, object);
                List<Subscriber> list = subscribers.computeIfAbsent(topic, key -> new LinkedList<Subscriber>());
                synchronized (list) {
                    list = copy.computeIfAbsent(topic, key -> new LinkedList<Subscriber>());
                }
                for (Subscriber subscriber : list) {
                    subscriber.receive(notice);
                }
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        });
    }

    @Override
    public <T extends Serializable> void publish(Class<T> cls, T object) {
        publish(cls.getName(), object);
    }

}
