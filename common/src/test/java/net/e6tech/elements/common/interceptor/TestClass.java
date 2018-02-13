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

package net.e6tech.elements.common.interceptor;

import javax.annotation.Nonnull;

public class TestClass {
    private int value = 0;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public TestClass() {
        System.out.println("TestClass constructor");
    }

    @Nonnull
    public void methodA() {
        System.out.println("methodA");
    }

    public String methodB(String arg) {
        System.out.println("methodB: " + arg);
        return arg;
    }

    public String methodC(String arg, int arg2) {
        System.out.println("methodC: " + arg + ", "+ arg2);
        return arg;
    }

    public String methodD(String arg) {
        System.out.println("methodD: " + arg);
        protectedMethod(arg);
        privateMethod(arg);
        return arg;
    }

    protected void protectedMethod(String arg) {
        System.out.println("protected method: " + arg);
    }

    private void privateMethod(String arg) {
        System.out.println("private method: " + arg);
    }
}
