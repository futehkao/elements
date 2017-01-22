/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.network.shell.telnet;

import net.e6tech.elements.common.logging.Logger;
import org.crsh.plugin.PluginContext;
import org.crsh.plugin.PropertyDescriptor;
import org.crsh.plugin.ResourceKind;
import org.crsh.telnet.TelnetPlugin;
import org.crsh.vfs.Resource;

import java.io.IOException;
import java.net.URL;

/**
 * Created by futeh.
 */
public class ElementsTelnetPlugin extends TelnetPlugin {

    private static Logger logger = Logger.getLogger();

    public static final PropertyDescriptor<String> BIND_ADDRESS = PropertyDescriptor.create("telnet.bind_address", "localhost", "The telnet port");

    private ElementsTelnetLifeCycle lifeCycle;

    @Override
    public void init() {
        PluginContext context = getContext();

        //
        Resource config = null;

        //
        URL configURL = TelnetPlugin.class.getResource("/crash/telnet.properties");
        if (configURL != null) {
            try {
                logger.debug("Found embedded telnet config url " + configURL);
                config = new Resource("telnet.properties", configURL);
            }
            catch (IOException e) {
                logger.debug("Could not load embedded telnet config url " + configURL + " will bypass it", e);
            }
        }

        // Override from config if any
        Resource res = getContext().loadResource("telnet.properties", ResourceKind.CONFIG);
        if (res != null) {
            config = res;
            logger.debug("Found telnet config url " + configURL);
        }

        //
        if (configURL == null) {
            logger.info("Could not boot Telnet due to missing config");
            return;
        }

        //
        ElementsTelnetLifeCycle lifeCycle = new ElementsTelnetLifeCycle(context);
        lifeCycle.setConfig(config);
        Integer port = context.getProperty(TELNET_PORT);
        if (port == null) {
            port = TELNET_PORT.defaultValue;
        }
        lifeCycle.setPort(port);

        String bindAddress = context.getProperty(BIND_ADDRESS);
        lifeCycle.setBindAddress(bindAddress);

        //
        lifeCycle.init();
    }

    @Override
    public void destroy() {
        if (lifeCycle != null) {
            lifeCycle.destroy();
        }
    }
}
