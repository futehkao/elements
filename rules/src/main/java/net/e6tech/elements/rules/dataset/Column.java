/*
Copyright 2015 Futeh Kao

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
package net.e6tech.elements.rules.dataset;

import java.math.BigDecimal;
import java.util.*;

/**
 * Created by futeh.
 */
public class Column implements Iterable {
    DataSet dataSet;
    String name;
    int columnIndex;
    Class dataType;

    public Column(DataSet dataSet, String name, Class type, int index) {
        this.dataSet = dataSet;
        this.name = name;
        this.columnIndex = index;
        this.dataType = type;
    }

    public String getName() {
        return name;
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    void setDataType(Class cls) {
        this.dataType = cls;
    }

    public Iterator iterator() {
        return new Itr();
    }

    public BigDecimal sum() {
        Calculate calculate = new Calculate(dataType) {
            @Override
            public long calculateLong(long current, long value) {
                return current + value;
            }

            @Override
            public double calculateDouble(double current, double value) {
                return current + value;
            }

            @Override
            public BigDecimal calculateBigDecimal(BigDecimal current, BigDecimal value) {
                return current.add(value);
            }
        };
        return calculate.calculate(this);
    }

    public BigDecimal max() {
        Number number = (Number) Collections.max(getData());
        if (number == null) return null;
        return new BigDecimal(number.toString());
    }

    public List getData() {
        List list = new ArrayList<>();
        for (Object obj : this) list.add(obj);
        return list;
    }

    private class Itr implements Iterator {
        int cursor;       // index of next element to return
        int lastRet = -1; // index of last element returned; -1 if no such
        int myModCount = dataSet.modCount;

        public boolean hasNext() {
            return cursor != dataSet.getRowSize();
        }

        @SuppressWarnings("unchecked")
        public Object next() {
            if (myModCount != dataSet.modCount) throw new ConcurrentModificationException();
            int i = cursor;
            if (i >= dataSet.getRowSize())
                throw new NoSuchElementException();
            cursor = i + 1;
            return dataSet.get(lastRet = i, columnIndex);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
