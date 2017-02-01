/*
 * Copyright 2015 Futeh Kao
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

import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.network.restful.RestfulProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import java.io.PrintWriter;

/**
 * Created by futeh.
 */
public class JaxRSServerTest {

    @Inject
    public Provision provision;

    @BeforeEach
    public void boot() {
        new LaunchController().launchScript("classpath://net/e6tech/elements/web/cxf/JaxRSServerTest.groovy")
                .property("home", System.getProperty("home", "."))
                .property("env", System.getProperty("env", "dev"))
                .inject(this).launch();
    }

    @Test
    public void hello() {
        RestfulProxy proxy = new RestfulProxy("http://localhost:" + 9000 + "/restful");
        proxy.setPrinter(new PrintWriter(System.out, true));
        HelloWorldRS api = proxy.newProxy(HelloWorldRS.class);
        String reply = api.sayHi("Mr. Jones");
        JaxRSServer server = (JaxRSServer) provision.getComponentResource("helloworld", "_helloworld");
        server.stop();

        try {
            reply = api.sayHi("Mr. Jones");
        } catch (NotFoundException ex) {
            System.out.println("caught expected exception: " + ex);
        }

        server.start();
        reply = api.sayHi("Mr. Anderson");
    }

}