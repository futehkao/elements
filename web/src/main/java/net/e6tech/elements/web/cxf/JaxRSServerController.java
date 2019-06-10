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

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class JaxRSServerController extends ServerController<JAXRSServerFactoryBean> {

    private List<Class<?>> resourceClasses = new ArrayList<>();

    JaxRSServerController(URL url, JAXRSServerFactoryBean bean) {
        super(url, bean);
    }

    synchronized void addResourceClasses(List<Class<?>> list) {
        resourceClasses.addAll(list);
    }

    List<Class<?>> getResourceClasses() {
        return resourceClasses;
    }
}
