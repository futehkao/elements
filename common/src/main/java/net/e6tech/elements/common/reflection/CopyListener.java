/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.common.reflection;

import java.beans.PropertyDescriptor;
import java.beans.PropertyVetoException;

/**
 * Created by futeh on 11/25/15.
 */
@FunctionalInterface
public interface CopyListener {
    /**
     *
     * @param target the target to be copied
     * @param targetDescriptor  The PropertyDescriptor of the property to be copied.
     *                          Typically, one may use its write method to set the target
     * @param owner             The owner which has the properties to be copied over to the target.
     * @param ownerDescriptor   The PropertyDescriptor of the owner's property to be copied over to the target.
     *                          One may use its read method to retrieve the property's value.
     * @return
     * @throws PropertyVetoException
     */
    boolean copy(Object target, PropertyDescriptor targetDescriptor,
                 Object owner, PropertyDescriptor ownerDescriptor) throws PropertyVetoException;
}
