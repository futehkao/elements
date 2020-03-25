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
package net.e6tech.elements.security;

import net.e6tech.elements.common.util.SystemException;

import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;

/**
 * Created by futeh.
 */
public class Hex {

    private Hex() {
    }

    public static byte[] toBytes(String str) {
        String hexString = str.replaceAll("\\s","");
        return DatatypeConverter.parseHexBinary(hexString);
    }

    public static char toNumeric(int number) {
        if (number > 15 || number < 0)
            throw new IllegalArgumentException("number must be between 0 and 15, inclusively.");
        if (number < 10)
            return (char) ('0' + number);
        return (char) ('A' + (number - 10));
    }

    // turn 0 into 1 and 1 to 0.
    @SuppressWarnings("squid:S1905")
    public static byte[] invert(byte[] bytes) {
        byte[] inverted = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            inverted[i] = (byte) ~bytes[i];
        }
        return inverted;
    }

    public static byte[] xor(byte[] left, byte[] right) {
        if (left == null || right == null || left.length != right.length)
            throw new SystemException("Needs 2 arrays with same length");
        byte[] result = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = (byte)(left[i] ^ right[i]);
        }
        return result;
    }

    public static byte[] concat(byte[] left, byte[] right) {
        if (left == null || right == null)
            throw new SystemException("2 non-null arrays are needed");
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left,  0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    public static byte[] leftPad(byte[] data, int length, byte padByte) {
        if (data == null)
            throw new SystemException("A non-null array is needed");
        byte[] result = new byte[length];
        Arrays.fill(result, 0, length - data.length, padByte);
        System.arraycopy(data,  0, result, length - data.length, data.length);
        return result;
    }

    public static byte[] rightPad(byte[] data, int length, byte padByte) {
        if (data == null)
            throw new SystemException("Needs a non-null array");
        byte[] result = new byte[length];
        System.arraycopy(data,  0, result, 0, data.length);
        Arrays.fill(result, data.length, length, padByte);
        return result;
    }

    public static String toString(byte[] bytes) {
        return DatatypeConverter.printHexBinary(bytes);
    }
}
