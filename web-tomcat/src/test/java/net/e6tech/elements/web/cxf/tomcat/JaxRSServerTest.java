/*
 * Copyright 2015-2020 Futeh Kao
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

package net.e6tech.elements.web.cxf.tomcat;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.network.restful.RestfulClient;
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.web.cxf.HelloWorldRS;
import net.e6tech.elements.web.cxf.HelloWorldRS2;
import net.e6tech.elements.web.cxf.JaxRSServer;
import net.e6tech.elements.web.cxf.PutData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;

/**
 * Created by futeh.
 */
public class JaxRSServerTest {

    public Provision provision;

    public Provision getProvision() {
        return provision;
    }

    @Inject
    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    @Test
    public void simpleTomcat() {
        String input = "net.e6tech.elements.web.cxf.tomcat.TomcatEngine";
        new LaunchController().launchScript("conf/provisioning/jaxrs/simple_hello.groovy")
                .property("serverEngineClass", input)
                .inject(this).launch();

        JaxRSServer server = provision.getComponentResource("helloworld", "_helloworld");
        Assertions.assertEquals(server.getServerEngine().getClass().getName(), input);

        RestfulProxy proxy = new RestfulProxy("http://localhost:" + 9000 + "/restful");
        proxy.setPrinter(new PrintWriter(System.out, true));
        HelloWorldRS api = proxy.newProxy(HelloWorldRS.class);
        String reply = api.sayHi("Mr. Jones");

        server.stop();

        server.start();

        reply = api.sayHi("Mr. Anderson");

        provision.getResourceManager().shutdown();
    }

    // NOTE when running using Tomcat, it's quite a bit slower because the shutdown time is long.
    // The various runXXX methods start and stop the engine.
    @ParameterizedTest
    @ValueSource(strings = {"net.e6tech.elements.web.cxf.jetty.JettyEngine", "net.e6tech.elements.web.cxf.tomcat.TomcatEngine", })
    public void hello(String input) {
        new LaunchController().launchScript("conf/provisioning/jaxrs/helloworld.groovy")
                .property("serverEngineClass", input)
                .inject(this).launch();

        JaxRSServer server = provision.getComponentResource("helloworld", "_helloworld");
        Assertions.assertEquals(server.getServerEngine().getClass().getName(), input);
        runHello("http://localhost:" + 9000 + "/restful");
        runHello("http://localhost:" + 9001 + "/restful");
        runHello("http://localhost:" + 9002 + "/restful");
        runHello2();

        provision.getResourceManager().shutdown();
    }

    protected void runHello(String address) {
        RestfulProxy proxy = new RestfulProxy(address);
        proxy.setPrinter(new PrintWriter(System.out, true));
        HelloWorldRS api = proxy.newProxy(HelloWorldRS.class);
        String reply = api.sayHi("Mr. Jones");
        JaxRSServer server = provision.getComponentResource("helloworld", "_helloworld");
        server.stop();
        JaxRSServer server2 = provision.getComponentResource("helloworld2", "_helloworld");
        server2.stop();

        try {
            reply = api.sayHi("Mr. Jones");
        } catch (Exception ex) {
            System.out.println("caught expected exception: " + ex);
        }

        server.start();
        server2.start();
        reply = api.sayHi("Mr. Anderson");

        PutData data = new PutData();
        data.setIntValue(10);
        data.setStringValue("Hello");
        api.putMethod("Test" , data);
    }

    protected void runHello2() {
        RestfulProxy proxy = new RestfulProxy("http://localhost:" + 9000 + "/restful");
        proxy.setPrinter(new PrintWriter(System.out, true));
        HelloWorldRS2 api = proxy.newProxy(HelloWorldRS2.class);
        String reply = api.sayHi("Mr. Jones");
        Assertions.assertEquals(reply, "Hello2 Mr. Jones");
    }

    @ParameterizedTest
    @ValueSource(strings = {"net.e6tech.elements.web.cxf.tomcat.TomcatEngine", "net.e6tech.elements.web.cxf.jetty.JettyEngine"})
    public void httpsKeyStore(String input) {
        new LaunchController().launchScript("conf/provisioning/jaxrs/helloworld_keystore.groovy")
                .property("serverEngineClass", input)
                .inject(this).launch();
        runHttps();
    }

    @ParameterizedTest
    @ValueSource(strings = {"net.e6tech.elements.web.cxf.tomcat.TomcatEngine", "net.e6tech.elements.web.cxf.jetty.JettyEngine"})
    public void httpsKeyStoreFile(String input) {
        new LaunchController().launchScript("conf/provisioning/jaxrs/helloworld_keystore_file.groovy")
                .property("serverEngineClass", input)
                .inject(this).launch();
        runHttps();
    }

    @ParameterizedTest
    @ValueSource(strings = {"net.e6tech.elements.web.cxf.tomcat.TomcatEngine", "net.e6tech.elements.web.cxf.jetty.JettyEngine"})
    public void httpsSelfSigned(String input) {
        new LaunchController().launchScript("conf/provisioning/jaxrs/helloworld_selfsigned.groovy")
                .property("serverEngineClass", input)
                .inject(this).launch();
        runHttps();
    }

    private void runHttps() {
        RestfulProxy proxy = new RestfulProxy("https://localhost:" + 9000 + "/restful");
        RestfulClient client = proxy.getClient();
        client.setTrustStore("conf/selfsigned.jks");
        client.setTrustStorePassword("password".toCharArray());
        proxy.setSkipCertCheck(true);
        proxy.setPrinter(new PrintWriter(System.out, true));
        HelloWorldRS api = proxy.newProxy(HelloWorldRS.class);
        api.sayHi("Mr. Jones");
        PutData data = new PutData();
        data.setIntValue(10);
        data.setStringValue("Hello");
        api.putMethod("Test" , data);
        provision.getResourceManager().shutdown();
    }
}