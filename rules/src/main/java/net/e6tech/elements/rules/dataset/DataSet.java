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

import java.util.*;

/**
 * Created by futeh.
 */
public class DataSet implements Map<String, Column> {
    List<List> data = new ArrayList<>();
    Map<String, Column> columnMap = new LinkedHashMap<>();
    Column[] columns;
    transient int modCount = 0;

    public DataSet(String[] col, Class[] columnType) {
        if (col.length != columnType.length) throw new IllegalArgumentException();
        columns = new Column[col.length];
        for (int i = 0; i < col.length; i++) {
            columns[i] =  new Column(this, col[i], columnType[i], i);
            columnMap.put(col[i], columns[i]);
        }
    }

    public void addRow(List row) {
        if (row.size() != columns.length) throw new IllegalArgumentException();
        List r = new ArrayList();
        r.addAll(row);
        data.add(r);
        modCount = (modCount + 1) & Integer.MAX_VALUE;
    }


    public Object get(int row, int column) {
        if (row >= getRowSize())
            throw new NoSuchElementException();
        List list = data.get(row);
        if (column >= list.size())
            throw new NoSuchElementException();
        return list.get(column);
    }

    public void set(int row, int column, Object object) {
        if (row >= getRowSize())
            throw new NoSuchElementException();
        List list = data.get(row);
        if (column >= list.size())
            throw new NoSuchElementException();
        list.set(column, object);
        modCount = (modCount + 1) & Integer.MAX_VALUE;
    }

    public int getColumnSize() {
        return columnMap.size();
    }

    public int getRowSize() {
        return data.size();
    }

    public Column getColumn(String key) {
        return columnMap.get(key);
    }

    public Collection<Column> getColumns() {
        return columnMap.values();
    }

    @Override
    public int size() {
        return getColumnSize();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        return columnMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return columnMap.containsValue(value);
    }

    @Override
    public Column get(Object key) {
        return columnMap.get(key);
    }

    @Override
    public Column put(String key, Column value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Column remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ? extends Column> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> keySet() {
        return columnMap.keySet();
    }

    @Override
    public Collection<Column> values() {
        return getColumns();
    }

    @Override
    public Set<Entry<String, Column>> entrySet() {
        return columnMap.entrySet();
    }
}
