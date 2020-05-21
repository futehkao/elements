
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

public class FederationTest {

    private static final int SERVERS = 10;
    private static final List<Federation> federations = Collections.synchronizedList(new ArrayList<>(SERVERS));

    @BeforeAll
    public static void setup() throws Exception {
        Beacon.logger = Logger.from(new ConsoleLogger().traceEnabled().debugEnabled());
        new Thread(()->{
            for (int i = 0; i < SERVERS; i++) {
                try {
                    setupServer(3909 + i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void setupServer(int port) throws Exception {
        ResourceManager rm = new ResourceManager();
        rm.loadProvision(Provision.class);

        Federation federation = rm.newInstance(Federation.class);
        federation.setHostAddress("http://127.0.0.1:" + port + "/restful");

        federation.getHostedMembers().computeIfAbsent("" + port,
                key -> {
                    Member member = new Member();
                    member.setName("" + port);
                    member.setMemberId("" + port);
                    return member;
                });

        Member member = new Member();
        member.setHostAddress("http://127.0.0.1:3909/restful");
        member.setMemberId("3909");
        federation.getSeeds().add(member);

        federation.start();
        federations.add(federation);
    }

    @AfterAll
    public static void tearDown() {
        try {
            federations.forEach(Federation::shutdown);
        } finally {
            federations.clear();
        }
    }

    @Test
    void basic() throws InterruptedException {
        BeaconAPI[] apis = new BeaconAPI[SERVERS];
        for (int i = 0; i < SERVERS; i ++) {
            int port = 3909 + i;
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
                System.out.println("Getting members");
                System.out.println();
            }

            if (total == SERVERS * SERVERS && ! printed) {
                System.out.println("Converge in " + (System.currentTimeMillis() - start));
                printed = true;
            }

            Thread.sleep(50L);
        }
    }
}
