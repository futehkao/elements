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

package net.e6tech.elements.common.util.concurrent;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Async<U> {
    default Object invoke(Object caller, Method method, Object[] args, Supplier<Object> supplier) {
        String methodName = method.getName();
        if ("hashCode".equals(methodName) && method.getParameterCount() == 0) {
            return caller.hashCode();
        } else if ("equals".equals(methodName) && method.getParameterCount() == 1) {
            return caller.equals(args[0]);
        } else if ("toString".equals(methodName) && method.getParameterCount() == 0) {
            return caller.toString();
        }
        return supplier.get();
    }

    long getTimeout();

    void setTimeout(long timeout);

    <R> CompletionStage<R> apply(Function<U, R> function);

    CompletionStage<Void> accept(Consumer<U> consumer);
}
