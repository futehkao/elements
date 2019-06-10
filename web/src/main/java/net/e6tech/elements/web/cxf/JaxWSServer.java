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
package net.e6tech.elements.web.cxf;

import net.e6tech.elements.common.reflection.Reflection;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.util.SystemException;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

import java.net.URL;


/**
 * Created by futeh.
 */
public class JaxWSServer extends CXFServer {

    Object implementor;
    Class serviceClass;

    @Override
    public void initialize(Resources resources) {
        // code is based on http://cxf.apache.org/docs/a-simple-jax-ws-service.html
        // in Publishing your service
        for (URL url : getURLs()) {
            JaxWsServerFactoryBean svrFactory = new JaxWsServerFactoryBean();
            svrFactory.setServiceClass(serviceClass);
            svrFactory.setAddress(url.toExternalForm());
            svrFactory.setServiceBean(implementor);
            svrFactory.getInInterceptors().add(new LoggingInInterceptor());
            svrFactory.getOutInterceptors().add(new LoggingOutInterceptor());
            addController(new ServerController<>(url, svrFactory));
        }

        super.initialize(resources);
    }

    public Object getImplementor() {
        return implementor;
    }

    public void setImplementor(Object implementor) {
        Object impl = implementor;
        if (implementor instanceof  String) {
            impl = Reflection.newInstance(implementor.toString(), null);
        }
        this.implementor = impl;
    }

    public Class getServiceClass() {
        return serviceClass;
    }

    public void setServiceClass(Class serviceClass) {
        this.serviceClass = serviceClass;
    }

    public void setServiceClass(String serviceClass) {
        this.serviceClass = Reflection.loadClass(serviceClass, null);
    }

}
