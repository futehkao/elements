/*
 * Copyright 2015-2025 Futeh Kao
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

package net.e6tech.elements.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;


class StringUtilTest {


    @ParameterizedTest
    @CsvSource({
            "test, 10, test",
            "test, 4, test",
            "test, 3, tes",
            "test, 2, te",
            "test, 1, t",
            "test, 0, ''",
            "helloworld, 5, hello",
            "username@domain.com, 16, username@domain.",
            "a, 1, a",
            "ab, 1, a",
            "'', 5, ''",
            "longusername, 8, longuser"
    })
    void testTrimToLength(String input, int maxLen, String expected) {
        String result = StringUtil.trimToLength(input, maxLen);
        assertEquals(expected, result);
    }

    @Test
    void testTrimToLengthWithNull() {
        String result = StringUtil.trimToLength(null, 10);
        assertEquals("", result);
    }

    @Test
    void testTrimToLengthWithNegativeMaxLen() {
        String result = StringUtil.trimToLength("test", -1);
        assertEquals("", result);
    }

    @Test
    void testTrimToLengthWithZeroMaxLen() {
        String result = StringUtil.trimToLength("test", 0);
        assertEquals("", result);
    }

    @Test
    void testTrimToLengthExactLength() {
        String result = StringUtil.trimToLength("exactly16chars!!", 16);
        assertEquals("exactly16chars!!", result);
    }

}
