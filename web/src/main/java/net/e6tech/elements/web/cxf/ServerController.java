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

package net.e6tech.elements.web.cxf;

import org.apache.cxf.endpoint.AbstractEndpointFactory;

import java.net.URL;

public class ServerController<T extends AbstractEndpointFactory> {
    protected T factory;
    protected URL url;

    ServerController(URL url, T factory) {
        this.url = url;
        this.factory = factory;
    }

    public synchronized URL getURL() {
        return url;
    }

    public T getFactory() {
        return factory;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    public boolean equals(Object object) {
        if (!(object instanceof ServerController)) {
            return false;
        }

        ServerController c = (ServerController) object;
        return url.equals(c.getURL()) && factory.equals(c.getFactory());
    }
}

