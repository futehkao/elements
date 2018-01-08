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

package net.e6tech.elements.common.resources;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Allow binding properties.  This does not recursively bind sub-properties.
 * <pre> <code>
 * Example
 *
 * {@literal @}BindProperties("y")
 * public class X {
 *      public Y getY() { ... }
 *      public void setY(Y y) { ... }
 * }
 *
 * public class Y {
 *     ...
 * }
 *
 * X x = ...
 * resources.bind(x) will also bind Y.  However, if Y is also annotated with BindProperties, it will not attempt to
 * bind Y's properties.
 * </code></pre>
 */
@Target({ TYPE })
@Retention(RUNTIME)
public @interface BindProperties {
    String[] value();
}

