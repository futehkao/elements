/*
Copyright 2015-2019 Futeh Kao

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

import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.ext.logging.event.LogEvent;
import org.apache.cxf.ext.logging.event.LogEventSender;
import org.apache.cxf.ext.logging.event.LogMessageFormatter;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by futeh.
 */
public class JaxWSTest {

    @Test
    public void test() throws Exception {

        JaxWSServer server = new JaxWSServer();
        List<String> addresses = new ArrayList<>();
        addresses.add("http://0.0.0.0:9000/helloWorld");
        server.setAddresses(addresses);
        server.setImplementor(new HelloWorldImpl());
        server.setServiceClass(HelloWorld.class);
        server.initialize(null);
        server.start();

        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        LoggingFeature feature = new LoggingFeature();
        DefaultLogEventSender sender = new DefaultLogEventSender();
        feature.setInSender(sender);
        feature.setOutSender(sender);
        factory.getFeatures().add(feature);
        factory.setServiceClass(HelloWorld.class);
        factory.setAddress("http://localhost:9000/helloWorld");
        HelloWorld client = (HelloWorld) factory.create();

        String reply = client.sayHi("HI");
        System.out.println("Server said: " + reply);
    }

    private class DefaultLogEventSender implements LogEventSender {

        @Override
        public void send(LogEvent event) {
            System.out.println(getLogMessage(event));
        }

        private String getLogMessage(LogEvent event) {
            return LogMessageFormatter.format(event);
        }
    }

}
