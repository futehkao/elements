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

import com.google.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.notification.NotificationCenter;
import net.e6tech.elements.common.resources.Startable;
import net.e6tech.elements.network.clustering.Cluster;
import net.e6tech.elements.network.clustering.ClusterService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * Created by futeh on 1/21/16.
 */
public class ClusterSocketProxyServer implements Startable, Runnable {

    private static Logger logger = Logger.getLogger();

    @Inject
    private NotificationCenter notificationCenter;

    private int port;
    private int servicePort;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private String serviceName;
    private Cluster cluster;
    private ClusterService service = new ClusterService();

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getServicePort() {
        return servicePort;
    }

    public void setServicePort(int servicePort) {
        this.servicePort = servicePort;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    public void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public void start() {
        if (threadPool == null) {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(group, runnable, "ClusterSocketProxyServer");
                thread.setName("ClusterSocketProxyServer-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }
        service.setName(serviceName);
        service.setPort(servicePort);
        cluster.addClusterService(service);
        threadPool.execute(this);
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (true) {
                try {
                    Transfer transfer = getTransfer();
                    threadPool.execute(transfer);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        } catch (Throwable th) {
            throw logger.runtimeException(th);
        }
    }

    protected Transfer getTransfer() throws IOException {
        Socket socket = serverSocket.accept();
        ClusterService service = cluster.getClusterService(serviceName);
        if (service == null) throw new RuntimeException("Service not found: " + serviceName);
        InetAddress inetAddress = service.getMember().getAddresses()[0];
        int port = service.getPort();
        Transfer transfer = new Transfer(inetAddress.getHostAddress(), port, socket, threadPool);
        return transfer;
    }
}
