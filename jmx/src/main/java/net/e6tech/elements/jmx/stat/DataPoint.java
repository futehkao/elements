/*
Copyright 2015-2019 Futeh Kao

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
package net.e6tech.elements.jmx.stat;

/**
 * Created by futeh.
 */

import java.io.Serializable;
import java.util.Objects;

public class DataPoint implements Serializable, Comparable<DataPoint> {
    private static final long serialVersionUID = -6790697881387398412L;
    private long timestamp;
    private double value;

    public DataPoint() {}

    public DataPoint(long timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    @Override
    @SuppressWarnings("squid:S1244")
    public int compareTo(DataPoint o) {
        if (value < o.getValue())
            return -1;
        if (value == o.getValue())
            return 0;
        return 1;
    }

    @SuppressWarnings("squid:S1244")
    public boolean equals(Object object) {
        if (!(object instanceof DataPoint))
            return false;
        return timestamp == ((DataPoint) object).timestamp
                && value == ((DataPoint) object).value;
    }

    public int hashCode() {
        return Objects.hash(value, timestamp);
    }
}