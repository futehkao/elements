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


package net.e6tech.elements.network.proxy;

import net.e6tech.elements.common.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

/**
 * Created by futeh on 1/21/16.
 */
@SuppressWarnings("squid:S1141")
public class Transfer implements Runnable {
    private static Logger logger = Logger.getLogger();

    String host;
    int port;
    Socket client;
    ExecutorService threadPool;

    public Transfer(String host, int port, Socket client, ExecutorService threadPool) {
        this.host = host;
        this.port = port;
        this.client = client;
        this.threadPool = threadPool;
    }

    public void run() {
        final byte[] request = new byte[4096];
        byte[] response = new byte[4096];
        Socket remote = null;
        try {
            final InputStream clientInputStream = client.getInputStream();
            final OutputStream clientOutputStream = client.getOutputStream();

            try {
                remote = new Socket(host, port);
            } catch (IOException e) {
                logger.warn("Cannot connect to " + host + " on port " + port, e);
                client.close();
                return;
            }

            final InputStream remoteInputStream = remote.getInputStream();
            final OutputStream remoteOutputStream = remote.getOutputStream();

            // reading client input in a separate threads so that both remote
            // and client can independently read and write.
            threadPool.execute(() -> {
                int read;
                try {
                    while ((read = clientInputStream.read(request)) != -1) {
                        remoteOutputStream.write(request, 0, read);
                        remoteOutputStream.flush();
                    }
                } catch (IOException e) {
                    Logger.suppress(e);
                }

                // done reading, close server output stream
                try {
                    remoteOutputStream.close();
                } catch (IOException e) {
                    Logger.suppress(e);
                }
            });


            // Reads remote server's bytes and forwards to client
            int read;
            try {
                while ((read = remoteInputStream.read(response)) != -1) {
                    clientOutputStream.write(response, 0, read);
                    clientOutputStream.flush();
                }
            } catch (IOException e) {
                Logger.suppress(e);
            }

            clientOutputStream.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                if (remote != null)
                    remote.close();
                if (client != null)
                    client.close();
            } catch (IOException e) {
                Logger.suppress(e);
            }
        }
    }
}
