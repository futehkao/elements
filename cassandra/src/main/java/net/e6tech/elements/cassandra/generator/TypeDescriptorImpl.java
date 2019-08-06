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

package net.e6tech.elements.cassandra.generator;

public class TypeDescriptorImpl implements TypeDescriptor {
    private boolean frozen;
    private boolean frozenKey;
    private boolean frozenValue;
    private boolean timeBased;
    private String columnName;

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    @Override
    public boolean isFrozenKey() {
        return frozenKey;
    }

    public void setFrozenKey(boolean frozenKey) {
        this.frozenKey = frozenKey;
    }

    @Override
    public boolean isFrozenValue() {
        return frozenValue;
    }

    public void setFrozenValue(boolean frozenValue) {
        this.frozenValue = frozenValue;
    }

    @Override
    public boolean isTimeBased() {
        return timeBased;
    }

    public void setTimeBased(boolean timeBased) {
        this.timeBased = timeBased;
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }
}
