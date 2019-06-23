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

package net.e6tech.elements.web.webserver;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebServerTest {

    @Inject
    public Provision provision;

    @ParameterizedTest
    @ValueSource(strings = {"net.e6tech.elements.web.webserver.tomcat.TomcatWebEngine", "net.e6tech.elements.web.webserver.jetty.JettyWebEngine"})
    public void testServlet(String input) throws Exception {

        new LaunchController().launchScript("conf/provisioning/webserver/servlet.groovy")
                .property("serverEngineClass", input)
                .inject(this).launch();

        WebServer server = provision.getComponentResource("servlet", "_server");
        assertEquals(server.getEngine().getClass().getName(), input);
        provision.getResourceManager().shutdown();
    }

    public static class MyServlet extends HttpServlet {
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException {
            System.out.println("Got request");
        }
    }
}
