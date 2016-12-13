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

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;

/**
 * Created by futeh on 1/20/16.
 */
public class ClusterServices implements Serializable {
    private static final long serialVersionUID = -5464052854109151792L;
    private Member member;
    private Map<String, ClusterService> services = new Hashtable<>();
    
    public ClusterServices(String uuid, InetAddress[] addresses, int adminPort) {
        member = new Member(uuid, addresses, adminPort);
    }

    public ClusterServices() {
        member = new Member(null, null, 0);
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public void addClusterService(ClusterService service) {
        services.put(service.getName(), service);
        service.setMember(member);
    }

    public ClusterService removeClusterService(String name) {
        return services.remove(name);
    }

    public Collection<ClusterService> getClusterServices() {
        return services.values();
    }

    public ClusterService getClusterService(String name) {
        return services.get(name);
    }
}
