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

    private UUID uuid = UUID.randomUUID();
    private Set<String> visited = new HashSet<>();
    private List<Member> members;
    private Type type;
    private int cycle = 4;

    public Event() {
    }

    public Event(Type type, List<Member> members, int cycle) {
        this.type = type;
        this.members = members;
        members.forEach(m -> visited.add(m.getMemberId()));
        this.cycle = cycle;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Set<String> getVisited() {
        return visited;
    }

    public void setVisited(Set<String> visited) {
        this.visited = visited;
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

    public int getCycle() {
        return cycle;
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
    }

    @Override
    public String toString() {
        return "type=" + type + "," + members.toString();
    }
}
