/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.web.federation;

public class Service<P, T>  {
    private P provider;
    private T prototype;
    private Class<T> serviceClass;

    public Service() {
    }

    public Service(P provider, Class<T> serviceClass, T prototype) {
        this.provider = provider;
        this.prototype = prototype;
        this.serviceClass = serviceClass;
    }

    public P getProvider() {
        return provider;
    }

    public void setProvider(P provider) {
        this.provider = provider;
    }

    public T getPrototype() {
        return prototype;
    }

    public void setPrototype(T prototype) {
        this.prototype = prototype;
    }

    public Class<T> getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class<T> serviceClass) {
        this.serviceClass = serviceClass;
    }
}
