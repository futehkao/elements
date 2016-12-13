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

import net.e6tech.elements.jmx.stat.Measurement;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;

/**
 * The class represents a running named service that has registered with the Cluster instance.
 * Client application can query the a Cluster member to get the named services running on various
 * cluster members.
 *
 * The class contains information that enables a client to use it: member, urls and port.  It also
 * contains measurement information so that client can employ a load balancing strategy.
 *
 * Created by futeh on 1/20/16.
 */
public class ClusterService implements Serializable {
    private String name;
    private Member member;
    private URL[] urls;
    private int port;
    private Measurement measurement = new Measurement();
    private int hashcode = 0;
    private transient boolean healthy = true;
    private transient Map<URL, Boolean> reachableURLs = new Hashtable<>();

    public static ClusterService newInstance(String name, String address) throws MalformedURLException {
        URL url = new URL(address);
        ClusterService service = new ClusterService();
        service.setName(name);
        service.setPort(url.getPort());
        try {
            if (url.getHost().equals("0.0.0.0")) {
                InetAddress[] addresses = Cluster.getHostAddresses();
                URL[] urls = new URL[addresses.length];
                for (int i = 0; i< addresses.length; i++) {
                    urls[i] = new URL(url.getProtocol(), addresses[i].getHostAddress(), url.getPort(), url.getFile());
                }
                service.setUrls(urls);
            } else {
                service.setUrls(new URL[]{url});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return service;
    }

    public ClusterService() {
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("name=" + name + " ");
        builder.append("member=" + member + " ");
        builder.append(measurement);
        return builder.toString();
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        measurement.setName(name);
        measurement.setUnit("ms");
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public URL[] getUrls() {
        return urls;
    }

    public void setUrls(URL[] urls) {
        this.urls = urls;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean hasReachableURLs() {
        if (urls == null) return false;
        for (URL u : urls) {
            Boolean reacheable = reachableURLs.get(u);
            if (reacheable == null) return true;
            if (reacheable) return true;
        }
        return false;
    }

    public URL getReachableURL() {
        if (urls == null) return null;
        for (URL u : urls) {
            Boolean reacheable = reachableURLs.get(u);
            if (reacheable == null) return u;
            if (reacheable) return u;
        }
        return null;
    }

    public void setReachable(URL url, boolean reacheable) {
        if (urls == null) return;
        for (URL u : urls) {
            if (u.equals(url)) {
                reachableURLs.put(u, reacheable);
            }
        }
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    public void setMeasurement(Measurement measurement) {
        this.measurement = measurement;
    }

    public int hashCode() {
        if (hashcode == 0) hashcode = (member.getUuid() + name).hashCode();
        return hashcode;
    }
    
    public boolean equals(Object obj) {
        if (!(obj instanceof ClusterService)) return false;
        ClusterService s = (ClusterService) obj;
        if (name == null || s.getName() == null) return false;
        if (member == null || s.getMember() == null) return false;
        if (member.getUuid() == null) return false;
        return name.equals(s.getName()) && member.getUuid().equals(s.getMember().getUuid());
    }
}
