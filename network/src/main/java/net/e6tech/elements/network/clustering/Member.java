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

/**
 * Member represents a cluster node.  It contains the node's IP addresses and it's associated
 * Cluster instance's adminPort.
 *
 * Created by futeh.
 */
public class Member implements Serializable {
    private static final long serialVersionUID = -9200680916395746454L;
    private InetAddress[] addresses;
    private int adminPort = 0;
    private String uuid;

    public Member() {
    }
    
    public Member(String uuid, InetAddress[] addresses, int adminPort) {
        this.uuid = uuid;
        this.addresses = addresses;
        this.adminPort = adminPort;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public InetAddress[] getAddresses() {
        return addresses;
    }

    public void setAddresses(InetAddress[] addresses) {
        this.addresses = addresses;
    }

    public int getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(int adminPort) {
        this.adminPort = adminPort;
    }
    
    public int hashCode() {
        return uuid.hashCode();
    }
    
    public boolean equals(Object obj) {
        if (!(obj instanceof  Member)) return false;
        Member m = (Member) obj;
        return uuid.equals(m.getUuid());
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (InetAddress addr : addresses) {
            if (first) first = false;
            else builder.append(",");
            builder.append(addr);
        }
        return builder.toString();
    }
}
