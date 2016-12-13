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
import com.j256.simplejmx.common.JmxAttributeMethod;
import com.j256.simplejmx.common.JmxResource;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.serialization.ObjectMapperFactory;
import net.e6tech.elements.common.subscribe.Broadcast;
import net.e6tech.elements.common.subscribe.Notice;
import net.e6tech.elements.common.subscribe.Subscriber;
import net.e6tech.elements.jmx.JMXService;
import org.jgroups.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A Cluster instance listens for other members to advertise their services.  When a client connects to it
 * via a socket, it can provide information regarding all of the services provided by members of the cluster.
 *
 * Created by futeh on 1/18/16.
 */
@JmxResource(description = "Cluster")
public class Cluster extends ReceiverAdapter implements Initializable, Broadcast {

    private static Logger logger = Logger.getLogger();
    public static ObjectMapper mapper = ObjectMapperFactory.newInstance();

    private long broadcastPeriod = 30000L;
    private String name;
    private JChannel channel;
    private Map<String, List<Subscriber>> topicSubscribers = new Hashtable<>();
    private Map<Address, ClusterServices> members = new Hashtable<>();  // services provided by other cluster members.
    private Map<String, Map<Address, ClusterService>> directory = new Hashtable<>(); // mapping of service name to address/ClusterService
    private ExecutorService threadPool;
    private ClusterServices myServices;     // contains only services for this particular instance.
    private Balancer defaultBalancer = new LoadBalancer();
    private int adminPort;  // for exposing info to other network
    private String configFile = "udp.xml";

    public Cluster(String name) {
        this();
        this.name = name;
    }

    public Cluster() {
        try {
            myServices = new ClusterServices();
            myServices.getMember().setAdminPort(adminPort);
            myServices.getMember().setAddresses(getHostAddresses());
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @JmxAttributeMethod
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JmxAttributeMethod
    public String[] getMyServicesDescription() {
        List<String> list = new ArrayList<>();
        myServices.getClusterServices().forEach((clusterService -> {
            list.add(clusterService.toString());
        }));
        return list.toArray(new String[list.size()]);
    }

    public ClusterServices getMyServices() {
        return myServices;
    }

    public Map<Address, ClusterServices> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    @JmxAttributeMethod
    public String[] getMemberAddresses() {
        List<String> list = new ArrayList<>();
        for (Address addr : members.keySet()) {
            list.add(addr.toString());
        }
        return list.toArray(new String[list.size()]);
    }

    @JmxAttributeMethod
    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }

    @JmxAttributeMethod
    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void ExecutorService(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    public ClusterService getClusterService(String name) {
        return getClusterService(name, null);
    }
    
    public ClusterService getClusterService(String name, Balancer balancer) {
        List<ClusterService> services = new ArrayList<>();
        if (myServices.getClusterService(name) != null) services.add(myServices.getClusterService(name));
        Map<Address, ClusterService> map = directory.get(name);
        if (map != null) {
            services.addAll(map.values());
        }
        if (balancer == null) return defaultBalancer.select(services);
        return balancer.select(services);
    }

    public List<ClusterService> getClusterServiceList(String name) {
        List<ClusterService> services = new ArrayList<>();
        if (myServices.getClusterService(name) != null) services.add(myServices.getClusterService(name));
        Map<Address, ClusterService> map = directory.get(name);
        if (map != null) {
            services.addAll(map.values());
        }
        return services;
    }

    public void addClusterService(ClusterService service) {
        myServices.addClusterService(service);
        if (channel != null && channel.isConnected()) {
            try {
                myServices.getMember().setUuid(channel.getView().getViewId().getCreator().toString());
                myServices.getMember().setAdminPort(adminPort);
                channel.send(null, myServices);
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    public void initialize(Resources resources) {
        if (threadPool == null) {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(group, runnable, "Cluster");
                thread.setName("Cluster-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }
        try {
            channel = new JChannel(configFile);
            channel.setReceiver(this);
            channel.setDiscardOwnMessages(true);
            logger.info("Clustering, connecting with name=" + name);
            channel.connect(name);
            myServices.getMember().setUuid(channel.getView().getViewId().getCreator().toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        threadPool.execute(()-> {
            // broadcast myServices to other members
            try {
                Thread.sleep(broadcastPeriod);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                try {
                    channel.send(null, myServices);
                } catch (Exception e) {

                }
                try {
                    Thread.sleep(broadcastPeriod);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        initSocketServer();

        JMXService.registerMBean(this, "net.e6tech:type=Cluster,name=" + name);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (channel != null) {
                    try { channel.close();} catch (Throwable e) {}
                }
            }
        });
    }
    
    protected void initSocketServer() {
        try {
            ServerSocket server = new ServerSocket(adminPort);
            adminPort = server.getLocalPort();
            myServices.getMember().setAdminPort(adminPort);
            threadPool.execute(()-> {
                while (true) {
                    Socket socket = null;
                    try {
                        socket = server.accept();
                        try {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                            String[] args = reader.readLine().split(",");
                            if (args != null && args.length > 0)
                                switch (args[0].trim()) {
                                    case ClusterClient.SERVICES_CMD :
                                        if (args.length > 1) {
                                            String name = args[1].trim();
                                            List<ClusterService> services = getClusterServiceList(name);
                                            String str = mapper.writeValueAsString(services.toArray(new ClusterService[services.size()]));
                                            writer.write(str);
                                            writer.flush();
                                        }
                                        break;
                                }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (Throwable e) {
                    } finally {
                        if (socket != null) try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void viewAccepted(View newView) {
        List<Address> addresses = newView.getMembers();
        Map<Address, ClusterServices> newTable = new Hashtable<>();
        Map<String, Map<Address, ClusterService>> newDirectory = new Hashtable<>();
        for (Address addr : addresses) {
            /* PhysicalAddress obj = (PhysicalAddress) channel.down(new Event(Event.GET_PHYSICAL_ADDRESS, addr));
            if (obj instanceof IpAddress) {
                IpAddress ipAddress = (IpAddress) obj;
                InetAddress inetAddress = ipAddress.getIpAddress();
                System.out.println(inetAddress.getHostAddress());
                System.out.println(inetAddress.getHostName());
            }*/
            // detect missing Address
            ClusterServices services = members.get(addr);
            if (services != null) {
                newTable.put(addr, services);
                for (ClusterService service : services.getClusterServices()) {
                    Map<Address, ClusterService> map = newDirectory.computeIfAbsent(service.getName(), key -> new Hashtable<>());
                    map.put(addr, service);
                }
            } else {
                // new member
                threadPool.execute(()-> {
                    try {
                        // broadcast my addresses
                        channel.send(addr, myServices);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        synchronized (members) {
            members.clear();
            members.putAll(newTable);
            directory.clear();
            directory.putAll(newDirectory);
        }
        logger.debug(members.toString());
    }

    public void receive(Message msg) {
        threadPool.execute(()-> {
            Object obj = msg.getObject();
            if (obj != null) {
                Address src = msg.getSrc();
                if (obj instanceof ClusterServices) {
                    Address self = channel.getAddress();
                    ClusterServices clusterServices = (ClusterServices) obj;
                    for (ClusterService service : clusterServices.getClusterServices()) {
                        Map<Address, ClusterService> map = directory.computeIfAbsent(service.getName(), key -> new Hashtable<>());
                       
                        if (self.equals(msg.getDest())) {
                            // for myself
                        } else {
                            map.put(src, service);
                        }
                    }
                    
                    
                    if (self.equals(msg.getDest())) {
                        // todo update my own cluster 
                    } else {
                        members.put(src, clusterServices);
                    }
                } else if (obj instanceof Notice) {
                    Notice notice = (Notice) obj;
                    String topic = notice.getTopic();
                    List<Subscriber> lis = topicSubscribers.get(topic);
                    if (lis != null) {
                        try {
                            lis.forEach(ml -> ml.receive(notice));
                        } catch (Throwable th) {
                            logger.warn(th.getMessage(), th);
                        }
                    }
                } else {
                    System.out.println(msg.getSrc() + " " + obj);
                }
            }
        });
    }

    public  Map<String, List<Subscriber>> getSubscribers() {
        return Collections.unmodifiableMap(topicSubscribers);
    }

    public void subscribe(String topic, Subscriber listener) {
        List<Subscriber> subscriber =  topicSubscribers.computeIfAbsent(topic, key -> new Vector<Subscriber>());
        subscriber.add(listener);
    }

    public <T extends Serializable> void subscribe(Class<T> topic, Subscriber<T> listener) {
        subscribe(topic.getName(), listener);
    }

    public void unsubscribe(String topic, Subscriber subscriber) {
        List<Subscriber> sub = topicSubscribers.get(topic);
        if (sub != null) sub.remove(subscriber);
    }

    public void unsubscribe(Class topic, Subscriber subscriber) {
        unsubscribe(topic.getName(), subscriber);
    }

    public void publish(String topic, Serializable object) {
        threadPool.execute(()-> {
            try {
                channel.send(null, new Notice(topic, object));
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        });
    }
    public void publish(Class cls, Serializable object) {
        publish(cls.getName(), object);
    }

    public static InetAddress[] getHostAddresses() throws SocketException {
        List<InetAddress> addresses = new ArrayList<>();
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            if (!netint.isLoopback()) {
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        addresses.add(inetAddress);
                    }
                }
            }
        }
        return addresses.toArray(new InetAddress[addresses.size()]);
    }

}
