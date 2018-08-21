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
package net.e6tech.elements.jmx;


import com.sun.jdmk.comm.CommunicationException;
import com.sun.jdmk.comm.HtmlAdaptorServer;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;

/**
 * Created by futeh.
 */
public class JMXHtmlServer extends HtmlAdaptorServer {

    private static final String INTERRUPT_SYS_CALL_MSG = "Interrupted system call";

    private Logger myLogger = Logger.getLogger();
    private InetAddress bindAddress;

    public JMXHtmlServer () {
        super();
    }

    /**
     * Constructs the <CODE>HtmlAdaptorServer</CODE> that will use the specified port.
     *
     * @param port An integer representing a valid port number.
     */
    public JMXHtmlServer(int port) {
        super(port);
    }

    public InetAddress getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(InetAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    @Override
    @SuppressWarnings("squid:S2095")
    protected void doBind() throws InterruptedException {

        int port = getPort();
        int maxActiveClientCount = getMaxActiveClientCount();
        myLogger.info("doBind: Bind the socket listener to [Port={}, MaxActiveClientCount={}]", port, maxActiveClientCount);

        try {
            ServerSocket serverSocket = new ServerSocket(port, 2 * maxActiveClientCount, getBindAddress());
            // we need set set super class sockListen to this
            Field field = HtmlAdaptorServer.class.getDeclaredField("sockListen");
            field.setAccessible(true);
            field.set(this, serverSocket);
            myLogger.info("doBind: Bound to [Address="+serverSocket.getInetAddress()+", Port="+serverSocket.getLocalPort()+"]");
        } catch (SocketException e) {
            if (e.getMessage().equals(INTERRUPT_SYS_CALL_MSG))
                throw new InterruptedException(e.toString()) ;
            else
                throw new CommunicationException(e) ;
        } catch (InterruptedIOException e) {
            Logger.suppress(e);
            throw new InterruptedException(e.toString()) ;
        } catch (IOException e) {
            throw new CommunicationException(e) ;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new SystemException(e);
        }
    }
}
