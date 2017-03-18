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

package net.e6tech.elements.network.clustering;


import org.junit.jupiter.api.Test;

/**
 * Created by futeh.
 * To test, need to run basic and then basic2
 */
public class ClusterTest {
    @Test
    public void basic() throws Exception {
        Cluster cluster = tcp(9900);

        cluster.subscribe("test", (notice)-> {
            System.out.println(notice.getUserObject());
        });

        int i = 0;
        while(true) {
            cluster.publish("test", "basic1 " + i++ );
            Thread.sleep(5000L);
        }
    }

    @Test
    public void basic2() throws Exception {
        Cluster cluster = tcpLocal(9901);

        cluster.subscribe("test", (notice)-> {
            System.out.println(notice.getUserObject());
        });

        int i = 0;
        while(true) {
            cluster.publish("test", "basic2 " + i++ );
            Thread.sleep(5000L);
        }
    }

    public Cluster tcp(int adminPort) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroup.tcp.bind_addr", "192.168.1.133");
        // System.setProperty("jgroups.udp.mcast_port", "45587");
        System.setProperty("jgroups.tcpping.initial_hosts", "192.168.1.133[7800]");
        //System.setProperty("jgroups.tcpping.initial_hosts", "127.0.0.1[7800]");
        Cluster cluster = new Cluster();
        cluster.setName("h3_cluster");
        cluster.setConfigFile("jgroup-tcp.xml");
        cluster.setAdminPort(adminPort);
        cluster.initialize(null);
        return cluster;
    }

    public Cluster tcpLocal(int adminPort) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroup.tcp.bind_addr", "localhost");
        // System.setProperty("jgroups.udp.mcast_port", "45587");
        System.setProperty("jgroups.tcpping.initial_hosts", "192.168.1.133[7800]");
        //System.setProperty("jgroups.tcpping.initial_hosts", "127.0.0.1[7800]");
        Cluster cluster = new Cluster();
        cluster.setName("h3_cluster");
        cluster.setConfigFile("jgroup-tcp.xml");
        cluster.setAdminPort(adminPort);
        cluster.initialize(null);
        return cluster;
    }
}