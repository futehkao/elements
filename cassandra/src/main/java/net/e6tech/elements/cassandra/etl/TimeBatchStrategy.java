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

import net.e6tech.elements.cassandra.Sibyl;

import java.util.List;

/**
 * This interface is designed to extract data from relation database into Cassandra
 * @param <S> source table from relation database
 */
public interface TimeBatchStrategy<S> extends BatchStrategy<S, TimeBatch> {

    /**
     * Subclasses should not override this method.  Instead, they should override
     * extractUpdate.
     *
     */
    @Override
    default List<S> extract(TimeBatch context) {
        LastUpdate lastUpdate = context.getLastUpdate();
        return extractUpdate(context, lastUpdate);
    }

    /**
     * When overriding this method, the implementation MUST update the context's start and lastUpdate's lastUpdate
     * with the last entry's modified time.  For example,
     *     <code>context.setStart(s.getModifiedTime());
     *     lastUpdate.update(s.getModifiedTime());</code>
     */
    List<S> extractUpdate(TimeBatch context, LastUpdate lastUpdate);

    @Override
    default int run(TimeBatch context) {
        if (context.getSourceClass() == null)
            throw new IllegalStateException("sourceClass not set in context");
        return context.open().apply(Sibyl.class, sibyl -> {
            context.initialize();
            LastUpdate lastUpdate = context.getLastUpdate();
            long lastImport = Long.parseLong(lastUpdate.getLastUpdate());

            long start = System.currentTimeMillis();
            context.setEnd(start - context.getTimeLag());
            context.setStart(lastImport);
            int importedCount = 0;
            List<S> batchResults = null;

            // NOTE, extract must call lastUpdate.update
            logger.info("Loading Class {}", getClass());
            while (!(batchResults = extract(context)).isEmpty()) {
                int processedCount = load(context, batchResults);
                context.saveLastUpdate(lastUpdate);
                logger.info("Processed {} instance of {}", processedCount, getClass());
                importedCount += processedCount;
            }
            logger.info("Done loading {} instance of {}", importedCount, getClass());
            return importedCount;
        });

    }
}
