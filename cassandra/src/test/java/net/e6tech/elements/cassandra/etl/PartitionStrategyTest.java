/*
 * Copyright 2015-2023 Futeh Kao
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

import net.e6tech.elements.cassandra.driver.cql.Row;
import net.e6tech.elements.cassandra.generator.Generator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PartitionStrategyTest {

    MockPartitionStrategy strategy = new MockPartitionStrategy();

    @Test
    void getAsyncRanges() {
        BigDecimal start = new BigDecimal(111111);
        BigDecimal end = new BigDecimal(222219);
        BigDecimal from = start;
        List<PartitionStrategy.Range> ranges = strategy.getAsyncRanges("query", start, end, 10, 100);
        Set<Integer> set = new LinkedHashSet<>();

        while (!ranges.isEmpty()) {
            for (PartitionStrategy.Range r : ranges) {
                for (int i = r.start.intValue(); i <= r.end.intValue(); i++)
                    set.add(i);
            }
            PartitionStrategy.Range lastRange = ranges.get(ranges.size() - 1);
            start = lastRange.end.subtract(BigDecimal.ONE);
            ranges = strategy.getAsyncRanges("query", start, end, 10, 100);
        }
        assertEquals(set.size(), end.subtract(from).intValue() + 1);
    }

    @Test
    void execAsyncQuery() {
        PartitionContext context = new MockContext();
        LastUpdate lastUpdate = new LastUpdate();
        lastUpdate.update("0");
        lastUpdate.setUnit(TimeUnit.MINUTES.toString());
        lastUpdate.setDataType("bigint");
        context.lastUpdate(lastUpdate);
        context.setAsyncTimeUnitStepSize(10);
        context.setAsyncMaxNumOfChunks(100);
        context.setStartTime(System.currentTimeMillis());
        context.setExtractorName("PartitionStrategyTest");
        context.setTimeUnit(TimeUnit.MINUTES);

        PartitionStrategy.PartitionQuery<PartitionContext> p = new PartitionStrategy.PartitionQuery<>(context);
        AtomicReference<PartitionStrategy.Range> ref = new AtomicReference<>();
        strategy.rangeConsumer = ranges -> {
            if (ref.get() != null && !ranges.isEmpty()) {
                assertTrue(ref.get().end.subtract(ranges.get(0).start).compareTo(BigDecimal.ONE) == 0);
            }
            if (!ranges.isEmpty())
                ref.set(ranges.get(ranges.size() - 1));

            PartitionStrategy.Range prev = null;
            for (PartitionStrategy.Range range : ranges) {
                if (prev != null) {
                    assertTrue(prev.end.subtract(range.start).compareTo(BigDecimal.ONE) == 0);
                }
                prev = range;
            }
        };
        strategy.asyncQuery(p, "query", row -> {});
    }

    private class MockPartitionStrategy extends PartitionStrategy<Partition, PartitionContext> {
        Consumer<List<Range>> rangeConsumer;
        @Override
        protected void execAsyncQuery(PartitionContext context, String query, List<Range> working, Consumer<Row> rowConsumer) {
            if (rangeConsumer != null)
                rangeConsumer.accept(working);
        }
    };


    private class MockContext extends PartitionContext {
        Inspector inspector = new MockInspector(null, null);

        public MockContext() {
        }

        @Override
        public TimeUnit getTimeUnit() {
            return TimeUnit.MINUTES;
        }

        @Override
        public Inspector getInspector() {
            return inspector;
        }

        @Override
        public String tableName() {
            return "test_table";
        }
    }

    private class MockInspector extends Inspector {

        public MockInspector(Class sourceClass, Generator generator) {
            super(sourceClass, generator);
        }

        public String getPartitionKeyColumn(int col) {
            return "partition_key";
        }
    }
}
