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

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by futeh.
 */
public class MeasurementTest {

    @Test
    @SuppressWarnings("squid:S2925")
    public void rollingWindow() throws InterruptedException {
        Measurement m = new Measurement("A", "ms", true);
        m.setWindowWidth(50); // 20 millis
        long start = System.currentTimeMillis();
        for (int i = 0; i < 70; i++) {
            m.append(i);
            long wait = (start + (i + 1) * 2) - System.currentTimeMillis();
            if (wait > 0)
                Thread.sleep(wait);
        }

        System.out.println(m.dump());
        System.out.println(m);
    }

    @Test
    public void minMax() {
        Measurement m = new Measurement("A", "ms", true);
        m.setWindowMaxCount(10);
        System.out.println(m.dump());
        for (int i = 0; i < 70; i++) {
            m.append(i);
        }

        System.out.println(m.dump());
        System.out.println(m);
    }

    @Test
    public void basic() {
        Measurement m = new Measurement();
        m.append(3.0).append(3.0).append(4.0).append(4.0).append(5.0).append(5.5).append(6.0);
        // check sortedByValue is indeed sorted
        m.sortedByValue.check();
        m.remove();
        m.remove();
        m.remove();
        m.remove();
        m.sortedByValue.check();

        // check sortedByValue is indeed sorted after remove
        m.append(3.0).append(4.0).append(5.0).append(6.0).append(8.0).append(8.0).append(6.5)
                .append(8.0).append(4.5).append(5.0).append(3.0).append(4.3);
        m.sortedByValue.check();
        m.remove();
        m.remove();
        m.remove();
        m.remove();
        m.remove();
        m.remove();
        m.sortedByValue.check();

        for (int count = 1; count < 1000; count ++) {
            m = new Measurement();
            Random random = new Random();
            for (int i = 0; i < count; i++) {
                double data = random.nextInt(100);
                m.append(data);
            }
            m.sortedByValue.check();
            if (m.sortedByValue.size() != m.getCount()) {
                throw new IllegalStateException();
            }

            double sum = 0.0;
            double sum_2 = 0.0;
            for (Comparable c : m.sortedByValue) {
                DataPoint dp = (DataPoint) c;
                sum += dp.getValue();
                sum_2 += dp.getValue() * dp.getValue();
            }

            double average = sum / m.sortedByValue.size();
            double var = 0.0;
            for (Comparable c : m.sortedByValue) {
                DataPoint dp = (DataPoint) c;
                double v = dp.getValue() - average;
                var += v * v;
            }
            double stddev = Math.sqrt(var / (m.sortedByValue.size() - 1));
            if ((average - m.getAverage()) * (average - m.getAverage()) >
                    0.0001f * (average + m.getAverage()) * (average + m.getAverage()) / 4f) {
                throw new IllegalStateException();
            }
            if ((sum - m.getSum()) * (sum - m.getSum()) > 0.0001f * (sum + m.getSum()) * (sum + m.getSum()) / 4f) {
                throw new IllegalStateException();
            }
            if ((stddev - m.getStdDev()) * (stddev - m.getStdDev()) > 0.0001f * (stddev + m.getStdDev()) * (stddev + m.getStdDev()) / 4f) {
                throw new IllegalStateException();
            }

            for (int i = 2; i < count; i++) {
                m.remove();
                m.sortedByValue.check();
                m.recalculate();
                if (m.sortedByValue.size() != m.getCount()) {
                    throw new IllegalStateException();
                }
                sum = 0.0;
                sum_2 = 0.0;
                for (Comparable c : m.sortedByValue) {
                    DataPoint dp = (DataPoint) c;
                    sum += dp.getValue();
                    sum_2 += dp.getValue() * dp.getValue();
                }

                average = sum / m.sortedByValue.size();
                var = 0.0;
                for (Comparable c : m.sortedByValue) {
                    DataPoint dp = (DataPoint) c;
                    double v = dp.getValue() - average;
                    var += v * v;
                }
                stddev = Math.sqrt(var /(double) (m.sortedByValue.size() - 1));

                if ((average - m.getAverage()) * (average - m.getAverage()) >
                        0.0001f * (average + m.getAverage()) * (average + m.getAverage()) / 4f) {
                    throw new IllegalStateException();
                }
                if ((sum - m.getSum()) * (sum - m.getSum()) > 0.0001f * (sum + m.getSum()) * (sum + m.getSum()) / 4f) {
                    throw new IllegalStateException();
                }
                if ((stddev - m.getStdDev()) * (stddev - m.getStdDev()) > 0.0001f * (stddev + m.getStdDev()) * (stddev + m.getStdDev()) / 4f) {
                    throw new IllegalStateException();
                }
            }
            System.out.println("done count = " + count);
        }
    }
}
