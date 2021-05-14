/*
 * Copyright 2015-2021 Futeh Kao
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

package net.e6tech.elements.jmx.stat;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GaugeTest {
    @Test
    void basic() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("kk:mm:ss.SSS");
        Gauge gauge = new Gauge();
        gauge.setPeriod(1000L);
        gauge.setWindowWidth(3000L);
        gauge.setFormat((key, measurement) -> String.format(format.format(new Date(System.currentTimeMillis())) + " key=%s: %s", key, measurement));
        gauge.initialize(null);

        gauge.add("test", 1000L);

        Thread.sleep(2000L);
        gauge.add("test", 500L);
        Thread.sleep(100L);
        gauge.add("test", 300L);

        Thread.sleep(1000L);
        System.out.println("done");

        assertEquals(2, gauge.getMeasurement("test").getCount());
    }
}
