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


package net.e6tech.elements.network.clustering;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.common.serialization.ObjectMapperFactory;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by futeh.
 */
public class ClusterClient {

    public static ObjectMapper mapper = ObjectMapperFactory.newInstance();
    static final String SERVICES_CMD = "services";
    
    private long firstContact = 10 * 1000L;
    private long renewalPeriod = 60 * 1000L;
    private List<ClusterService> services = new ArrayList<>();
    private String host;
    private int adminPort;
    private String serviceName;
    private ClusterService current;
    private Thread renewalThread;
    private Renewal renewal;
    private Balancer balancer = new LoadBalancer();

    public long getRenewalPeriod() {
        return renewalPeriod;
    }

    public void setRenewalPeriod(long renewalPeriod) {
        this.renewalPeriod = renewalPeriod;
    }
    
    public List<ClusterService> getServices() {
        return services;
    }

    public void setServices(List<ClusterService> services) {
        this.services = services;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * connects to Cluster's admin port.  The format is host:port/serviceName
     * @param clusterAddress
     */
    public synchronized void connect(String clusterAddress) {
        if (clusterAddress == null) return;
        stop();
        String host = clusterAddress.substring(0, clusterAddress.indexOf(":"));
        if (host == null || host.length() == 0) throw new IllegalArgumentException("Null cluster host");
        int port = Integer.parseInt(clusterAddress.substring(clusterAddress.indexOf(":") + 1, clusterAddress.indexOf("/")));
        String serviceName = clusterAddress.substring(clusterAddress.indexOf("/") + 1);
        while (serviceName.startsWith("/")) serviceName = serviceName.substring(1);
        if (serviceName == null || serviceName.length() == 0) throw new IllegalArgumentException("Null cluster serviceName");
        setAdminPort(port);
        setServiceName(serviceName);
        setHost(host);
        start();
    }
    
    public ClusterService select() {
        return balancer.select(services);    
    }

    /**
     * Stops the renewal thread.
     */
    public synchronized void stop() {
        if (renewalThread != null) {
            renewal.stopped = true;
            renewalThread.interrupt();
            renewal = null;
            renewalThread = null;
        }
    }

    /**
     * Starts the renewal thread.
     */
    public void start() {
        if (renewalThread != null) return;
        renewal = new Renewal();
        renewalThread = new Thread(renewal);
        renewalThread.setDaemon(true);
        renewalThread.start();
    }

    /**
     * Renew services.  It would query a host to get a list of ClusterServices with serviceName.
     */
    protected void renewal() throws IOException {
        while (true) {
            Socket socket = null;
            try {
                socket = selectSocket();
            } catch (IOException ex) {
                if (current != null) current.setHealthy(false);
                throw ex;
            }

            try {
                _renewal(socket);
                break;
            } catch (IOException ex) {
                if(current != null) current.setHealthy(false);
                else throw ex; // no available members
            } finally {
                if (socket != null) try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    protected void _renewal(Socket socket) throws IOException {
        if (socket == null) return;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        writer.write(SERVICES_CMD + "," + serviceName + "\n");
        writer.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        ClusterService[] list = mapper.readValue(reader, ClusterService[].class);
        services.clear();
        for (ClusterService cs : list) services.add(cs);

    }

    /**
     * Select a socket from a list of hosts.  Initially there is only one host.  However, once
     * we retrieve services from the host, we will get a list of hosts.
     *
     * @return
     * @throws IOException
     */
    protected Socket selectSocket() throws IOException {
        if (services.size() == 0) {
            current = null;
            return new Socket(host, adminPort);
        }
        for (ClusterService cs : services) {
            if (!cs.isHealthy()) continue;
            try {
                current = cs;
                return new Socket(cs.getMember().getAddresses()[0], cs.getMember().getAdminPort());
            } catch (IOException | NullPointerException ex) {
                cs.setHealthy(false);
            }
        }
        
        current = null;
        return new Socket(host, adminPort);
    }
    
    class Renewal implements Runnable {
        boolean stopped = false;
        @Override
        public void run() {
            int count = 0;
            while (!stopped) {
                try {
                    renewal();
                    break;
                } catch (IOException e) {
                }
                count ++;
                try {
                    if (count <= 6) Thread.sleep(firstContact); 
                    else Thread.sleep((count - 5) * firstContact);
                    if (count == 10) count = 6;
                } catch (InterruptedException ex) {
                    
                }
            }

            try {Thread.sleep(renewalPeriod); } catch (InterruptedException ex) {}
            while (!stopped) {
                try { renewal(); } catch (IOException ex) {}
                try {
                    boolean healthy = false;
                    for (ClusterService cs : services) {
                        if (cs.isHealthy()) {
                            healthy = true;
                            break;
                        }
                    }

                    if (healthy) Thread.sleep(renewalPeriod);
                    else Thread.sleep(firstContact);
                } catch (InterruptedException ex) {}
            }
        }
    }
}
