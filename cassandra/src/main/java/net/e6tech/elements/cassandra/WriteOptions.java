/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.cassandra;

import net.e6tech.elements.common.util.SystemException;

import java.util.Objects;

public class WriteOptions implements Cloneable {
    public Consistency consistency;
    public Integer ttl;
    public Boolean saveNullFields;
    public Boolean ifNotExists;
    public Long timeout; // use for save

    public static WriteOptions from(WriteOptions from) {
        if (from == null)
            return new WriteOptions();
        else
            return from.clone();
    }

    public WriteOptions merge(WriteOptions options) {
        if (options == null)
            return this;
        if (options.ttl != null)
            ttl = options.ttl;
        if (options.consistency != null)
            consistency = options.consistency;
        if (options.saveNullFields != null)
            saveNullFields = options.saveNullFields;
        if (options.ifNotExists != null)
            ifNotExists = options.ifNotExists;
        return this;
    }

    public WriteOptions clone() {
        try {
            return (WriteOptions) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new SystemException(e);
        }
    }

    public Consistency getConsistency() {
        return consistency;
    }

    public void setConsistency(Consistency consistency) {
        this.consistency = consistency;
    }

    public WriteOptions consistency(Consistency c) {
        this.consistency = c;
        return this;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public WriteOptions ttl(Integer ttl) {
        this.ttl = ttl;
        return this;
    }

    public Boolean getSaveNullFields() {
        return saveNullFields;
    }

    public void setSaveNullFields(Boolean saveNullFields) {
        this.saveNullFields = saveNullFields;
    }

    public WriteOptions saveNullFields(Boolean b) {
        this.saveNullFields = b;
        return this;
    }

    public Boolean getIfNotExists() {
        return ifNotExists;
    }

    public void setIfNotExists(Boolean ifNotExists) {
        this.ifNotExists = ifNotExists;
    }

    public WriteOptions ifNotExists(Boolean b) {
        this.ifNotExists = b;
        return this;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    @Override
    public int hashCode() {
        return Objects.hash(consistency, ttl, saveNullFields, ifNotExists);
    }

    public boolean equals(Object obj) {
        if (obj instanceof WriteOptions) {
            WriteOptions other = (WriteOptions) obj;
            return Objects.equals(consistency, other.consistency)
                    && Objects.equals(ttl, other.ttl)
                    && Objects.equals(saveNullFields, other.saveNullFields)
                    && Objects.equals(ifNotExists, other.ifNotExists);
        }
        return false;
    }
}
