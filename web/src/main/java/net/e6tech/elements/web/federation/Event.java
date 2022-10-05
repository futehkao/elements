package net.e6tech.elements.web.federation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Event {

    public enum Type {
        ANNOUNCE,
        REMOVE
    }

    private String clusterName;

    private UUID uuid = UUID.randomUUID();
    private Set<String> visited = new HashSet<>();
    private List<Member> members;
    private Type type;
    private Collective.Type collectiveType;
    private int cycle = 4;

    public Event() {
    }

    public Event(String clusterName, Type type, Collective.Type collectiveType, List<Member> members, int cycle) {
        this.clusterName = clusterName;
        this.type = type;
        this.collectiveType = collectiveType;
        this.members = members;
        members.forEach(m -> visited.add(m.getMemberId()));
        this.cycle = cycle;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public synchronized Set<String> getVisited() {
        return new HashSet<>(visited);
    }

    public synchronized void setVisited(Set<String> visited) {
        this.visited = visited;
    }

    public synchronized Event addVisited(Set<String> visited) {
        this.visited.addAll(visited);
        return this;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMember(List<Member> members) {
        this.members = members;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Collective.Type getCollectiveType() {
        return collectiveType;
    }

    public void setCollectiveType(Collective.Type collectiveType) {
        this.collectiveType = collectiveType;
    }

    public int getCycle() {
        return cycle;
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
    }

    @Override
    public String toString() {
        return "collective=" + collectiveType + ",type=" + type + "," + members.toString();
    }
}
