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

package net.e6tech.sample.federation;

import net.e6tech.elements.common.launch.LaunchController;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.network.restful.RestfulProxy;
import net.e6tech.elements.web.federation.BeaconAPI;
import net.e6tech.elements.web.federation.ClusterImpl;
import net.e6tech.elements.web.federation.CollectiveImpl;
import net.e6tech.elements.common.federation.Member;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

public class ClusterTest {

    public static Provision provision;

    @BeforeAll
    public static void launch() {
        LaunchController controller = new LaunchController();
        controller.launchScript("conf/provisioning/federation/federation.groovy")
                .addLaunchListener(p -> provision = p.getInstance(Provision.class))
                .launch();
    }

    @Test
    void basic() throws InterruptedException {
        List<ClusterImpl> clusters = provision.getResourceManager().getBean("clusters");

        BeaconAPI[] apis = new BeaconAPI[clusters.size()];
        for (int i = 0; i < clusters.size(); i ++) {
            ClusterImpl cluster = clusters.get(i);
            RestfulProxy proxy = new RestfulProxy(cluster.getHostAddress());
            apis[i] = proxy.newProxy(BeaconAPI.class);
        }

        long start = 0;
        boolean printed = false;
        int printFrequency = 50;
        while (true) {
            int total = 0;
            Collection<Member> members;
            for (int i = 0; i < clusters.size(); i++) {
                try {
                    members = apis[i].members();
                } catch (Exception ex) {
                    break;
                }

                total += members.size();

                Thread.sleep(50L);
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

            if (total == clusters.size() * clusters.size() && ! printed) {
                System.out.println("Converge in " + (System.currentTimeMillis() - start));
                printed = true;
                CollectiveImpl cluster = clusters.get(clusters.size() / 2);
                Collection<Member> m = cluster.members();
                System.out.println(m);
            }

            Thread.sleep(50L);
        }
    }
}
