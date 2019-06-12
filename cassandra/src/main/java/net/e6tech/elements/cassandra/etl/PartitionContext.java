/*
 * Copyright 2017 Futeh Kao
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

package net.e6tech.elements.cassandra.etl;

import com.datastax.driver.core.PreparedStatement;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.util.SystemException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public class PartitionContext extends ETLContext {
    private List<Comparable> partitions = new ArrayList<>();
    private Map<String, PreparedStatement> preparedStatements = new HashMap<>();

    private ToIntFunction<List> loadDelegate;

    public static PartitionContext createContext(Provision provision, Class<? extends Partition> cls) {
        Partition partition = null;
        try {
            partition = cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(e);
        }
        PartitionContext context = partition.createContext();
        context.setSourceClass(cls);
        if (provision != null) {
            provision.inject(context);
            context.initialize();
        }
        return context;
    }

    public PartitionContext createContext(Class<? extends Partition> cls) {
        return createContext(getProvision(), cls);
    }

    public List<Comparable> getPartitions() {
        return partitions;
    }

    public void setPartitions(List<Comparable> partitions) {
        this.partitions = partitions;
    }

    public PartitionStrategy createStrategy() {
        return new PartitionStrategy();
    }

    public ToIntFunction<List> getLoadDelegate() {
        return loadDelegate;
    }

    public void setLoadDelegate(ToIntFunction<List> loadDelegate) {
        this.loadDelegate = loadDelegate;
    }

    public Map<String, PreparedStatement> getPreparedStatements() {
        return preparedStatements;
    }

    @Override
    public void reset() {
        super.reset();
        partitions.clear();
        preparedStatements.clear();
    }

    public PartitionContext run(Class<? extends PartitionStrategy> cls ) {
        try {
            PartitionStrategy strategy = cls.getDeclaredConstructor().newInstance();
            setImportedCount(strategy.run(this));
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
        return this;
    }
}
