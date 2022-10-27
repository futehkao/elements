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

package net.e6tech.elements.common.federation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Member {
    private String memberId;
    private String name;
    private String address;
    private long expiration;
    private String toString;
    private List<String> services = new LinkedList<>();

    public Member() {
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address.trim();
        while (this.address.endsWith("/"))
            this.address = this.address.substring(0, this.address.length() - 1);
    }

    public List<String> getServices() {
        return services;
    }

    public void setServices(List<String> services) {
        this.services = services;
    }


    public static String formatISODateTime(long timeMillis, ZoneId zoneId) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), zoneId));
    }

    public Member memberId(String memberId) {
        setMemberId(memberId);
        return this;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Member name(String name) {
        setName(name);
        return this;
    }

    public Member address(String address) {
        setAddress(address);
        return this;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
        toString = null;
    }

    public Member expiration(long exp) {
        setExpiration(exp);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return getMemberId().equals(member.getMemberId()) &&
                getAddress().equals(member.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMemberId(), getAddress());
    }

    @Override
    public String toString() {
        if (toString == null) {
            toString = "memberId=" + getMemberId()
                    + ",hostAddress='" + getAddress()
                    + "',expiration=" + formatISODateTime(expiration, ZoneId.systemDefault());
        }
        return toString;
    }
}
