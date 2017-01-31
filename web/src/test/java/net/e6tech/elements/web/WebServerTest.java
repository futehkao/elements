package net.e6tech.elements.web;

import org.junit.jupiter.api.Test;

public class WebServerTest {

    @Test
    public void testWebApps() throws Exception {

        WebServer server = new WebServer();
        server.setHttpPort(9091);
        server.addWebApps("./src/test/resources/webapps");
        server.start();

        //Thread.sleep(60000 * 60);
    }
}
