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

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Created by futeh.
 */
public class DataSetTest {

    @Test
    public void test() throws Exception {
        DataSet dataSet = new DataSet(new String[] {"number"}, new Class[] {Integer.class});
        List list = new ArrayList<>();
        list.add(1);
        dataSet.addRow(list);
        list.clear();
        list.add(2);
        dataSet.addRow(list);
        list.clear();
        list.add(3);
        dataSet.addRow(list);
        assertTrue(dataSet.getColumn("number").sum().equals(new BigDecimal(6)));
        assertTrue(dataSet.getColumn("number").max().equals(new BigDecimal(3)));

        dataSet = new DataSet(new String[] {"number"}, new Class[] {BigDecimal.class});
        list.clear();
        list.add(new BigDecimal("1.1"));
        dataSet.addRow(list);
        list.clear();
        list.add(new BigDecimal("2.2"));
        dataSet.addRow(list);
        list.clear();
        list.add(new BigDecimal("3.3"));
        dataSet.addRow(list);
        assertTrue(dataSet.getColumn("number").sum().equals(new BigDecimal("6.6")));
        assertTrue(dataSet.getColumn("number").max().equals(new BigDecimal("3.3")));
    }
}
