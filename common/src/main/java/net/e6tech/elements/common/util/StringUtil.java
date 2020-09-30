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

package net.e6tech.elements.common.util;

import java.util.Arrays;

/**
 * Created by futeh.
 */
public class StringUtil {

    private StringUtil() {
    }

    public static boolean isNullOrEmpty(String p) {
        return (p == null) || p.trim().isEmpty();
    }

    public static String padLeft(String content, int width, char padding) {
        return pad(content, width, padding, false);
    }

    public static String padRight(String content, int width, char padding) {
        return pad(content, width, padding, true);
    }

    public static String pad(String content, int width, char padding, boolean leftAligned) {
        String str = "";
        if (content != null)
            str = content;
        int diff = width - str.length();
        if (diff <= 0)
            return str;
        char[] chars = new char[diff];
        Arrays.fill(chars, padding);
        if (leftAligned)
            return str + new String(chars);
        else return new String(chars) + str;
    }

    public static String trim(String s) {
        if (s == null)
            return null;

        int start = 0;
        int end = s.length() - 1;

        while (start <= end && Character.isWhitespace(s.charAt(start)))
            start++;

        while (end > start && Character.isWhitespace(s.charAt(end)))
            end--;

        return s.substring(start, end + 1);
    }

    public static boolean isNotBlank(String str) {
        return !StringUtil.isBlank(str);
    }

    public static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if ((Character.isWhitespace(str.charAt(i)) == false)) {
                return false;
            }
        }
        return true;
    }
}
