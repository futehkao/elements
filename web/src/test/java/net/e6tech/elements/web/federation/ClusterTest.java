/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.logging.ConsoleLogger;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.network.restful.RestfulProxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ClusterTest {

    private static final int SERVERS = 5;
    private static final List<Cluster> clusters = Collections.synchronizedList(new ArrayList<>(SERVERS));

    @BeforeAll
    public static void setup() {
        Beacon.logger = Logger.from(new ConsoleLogger().traceEnabled().debugEnabled());
        new Thread(()->{
            for (int i = 0; i < 2 * SERVERS; i += 2) {
                try {
                    if (i >= 2 * (SERVERS - 2)) {
                        Cluster c3 = configServer(3909 + i, 3910 + i);
                        c3.getFederation().setSeeds(new String[0]);
                        c3.start();
                    } else {
                        setupServer(3909 + i, 3910 + i);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void setupServer(int port, int port2) throws Exception {
        Cluster cluster = configServer(port, port2);
        cluster.start();
    }

    private static Cluster configServer(int port, int port2) throws Exception {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);

        Cluster cluster = rm.newInstance(Cluster.class);
        cluster.setHostAddress("http://127.0.0.1:" + port + "/restful");
        cluster.setSeeds(new String[] { "http://127.0.0.1:3909/restful"});

        cluster.getFederation().setHostAddress("http://127.0.0.1:" + port2 + "/restful");
        cluster.getFederation().setHosts(new Host[] { new Host("" + port2)});
        cluster.getFederation().setSeeds(new String[] {"http://127.0.0.1:3910/restful"});
        clusters.add(cluster);
        return cluster;
    }

    @AfterAll
    public static void tearDown() {
        try {
            clusters.forEach(Cluster::shutdown);
        } finally {
            clusters.clear();
        }
    }

    @Test
    void basic() throws InterruptedException {
        BeaconAPI[] apis = new BeaconAPI[SERVERS];
        for (int i = 0; i < SERVERS; i ++) {
            int port = 3909 + 2 * i;
            RestfulProxy proxy = new RestfulProxy("http://localhost:" + port + "/restful");
            apis[i] = proxy.newProxy(BeaconAPI.class);
        }

        long start = 0;
        boolean printed = false;
        int printFrequency = 50;
        while (true) {
            int total = 0;
            Collection<Member> members;
            for (int i = 0; i < SERVERS; i++) {
                try {
                    members = apis[i].members();
                } catch (Exception ex) {
                    break;
                }

                total += members.size();
            }

            if (start == 0)
                start = System.currentTimeMillis();

            printFrequency --;
            if (printFrequency == 0) {
                printFrequency = 50;
                System.out.println();
                System.out.println("FEDERATIONS MEMBERS " + clusters.get(0).getFederation().members().size());
                System.out.println();
            }

            if (total == SERVERS * SERVERS && ! printed) {
                System.out.println("Converge in " + (System.currentTimeMillis() - start));
                printed = true;
                Collective cluster = clusters.get(SERVERS / 2);
                Collection<Member> m = cluster.members();
                System.out.println(m);
            }

            Thread.sleep(50L);
        }
    }
}
