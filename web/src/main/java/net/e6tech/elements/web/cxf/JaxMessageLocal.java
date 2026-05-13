/*
 * Copyright 2015-2026 Futeh Kao
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

package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.resources.Resources;
import org.apache.cxf.message.Message;

import java.util.function.Consumer;

public class JaxMessageLocal {
    private static final ThreadLocal<JaxMessageLocal> messageThreadLocal = new ThreadLocal<>();

    private JaxRSServer server;
    private Message message;
    private Resources resources;

    public static ThreadLocal<JaxMessageLocal> getMessageThreadLocal() {
        return messageThreadLocal;
    }

    public static JaxMessageLocal set(Consumer<JaxMessageLocal> consumer) {
        JaxMessageLocal local = new JaxMessageLocal();
        consumer.accept(local);
        getMessageThreadLocal().set(local);
        return local;
    }

    private static JaxMessageLocal getOrCreate() {
        JaxMessageLocal local = getMessageThreadLocal().get();
        if (local == null) {
            local = new JaxMessageLocal();
            getMessageThreadLocal().set(local);
        }
        return local;
    }

    public static JaxMessageLocal get() {
        return getMessageThreadLocal().get();
    }

    public static void remove() {
        getMessageThreadLocal().remove();
    }

    public JaxRSServer getServer() {
        return server;
    }

    public void setServer(JaxRSServer server) {
        this.server = server;
    }

    public JaxMessageLocal server(JaxRSServer server) {
        this.server = server;
        return this;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public JaxMessageLocal message(Message message) {
        this.message = message;
        return this;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public JaxMessageLocal resources(Resources resources) {
        this.resources = resources;
        return this;
    }
}
