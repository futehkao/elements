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

@SuppressWarnings({"squid:S2975", "squid:S1845", "squid:ClassVariableVisibilityCheck"})
public class ReadOptions implements Cloneable {
    public Consistency consistency;

    public static ReadOptions from(ReadOptions from) {
        if (from == null)
            return new ReadOptions();
        else
            return from.clone();
    }

    public ReadOptions merge(ReadOptions options) {
        if (options == null)
            return this;
        if (options.consistency != null)
            consistency = options.consistency;
        return this;
    }

    public ReadOptions clone() {
        try {
            return (ReadOptions) super.clone();
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

    public ReadOptions consistency(Consistency c) {
        this.consistency = c;
        return this;
    }
}
