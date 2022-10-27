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

package net.e6tech.elements.common.util.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.common.Tags;
import net.e6tech.elements.common.reflection.ObjectConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by futeh.
 */
@Tags.Common
public class ObjectConverterTest {
    @Test
    @SuppressWarnings("unchecked")
    public void basic() throws Exception {
        ObjectMapper mapper = new ObjectConverter(k -> 1L).createObjectMapper();
        String str = mapper.writeValueAsString("^abc");
        Object value = mapper.readValue(str, Long.class);
        assertEquals(1L, value);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void convert() throws Exception {
        ObjectConverter converter = new ObjectConverter();
        List<Long> list = new ArrayList<>();
        list.add(1L);
        list.add(2L);
        Object value = converter.convert(list, int[].class);
        int[] array = (int[]) value;
        assertTrue(array[0] ==  1);
        assertTrue(array[1] ==  2);
        List<Long> converted = new ArrayList<>();

        converted = (List) converter.convert(list, converted.getClass());
        // Unfortunately due to type erasure the list won't contain Integers.
        // assertTrue(converted.get(0).getClass().equals(Integer.class));
        assertTrue(converted.get(0)  == 1);
        assertTrue(converted.get(1) ==  2);

    }

}