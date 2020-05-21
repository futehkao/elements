package net.e6tech.elements.web.federation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Member {
    private String memberId;
    private String name;
    private String hostAddress;
    private long expiration;
    private String toString;

    public static String formatISODateTime(long timeMillis, ZoneId zoneId) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), zoneId));
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String address) {
        this.hostAddress = address.trim();
        while (hostAddress.endsWith("/"))
            hostAddress = hostAddress.substring(0, hostAddress.length() - 1);
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
        toString = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return memberId.equals(member.memberId) &&
                hostAddress.equals(member.hostAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, hostAddress);
    }

    @Override
    public String toString() {
        if (toString == null) {
            toString = "memberId=" + memberId
                    + ",hostAddress='" + hostAddress
                    + "',expiration=" + formatISODateTime(expiration, ZoneId.systemDefault());
        }
        return toString;
    }
}
