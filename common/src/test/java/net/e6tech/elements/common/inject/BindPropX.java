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

package net.e6tech.elements.common.inject;

public class BindPropX {

    private BindPropA a;
    private BindPropB b;
    private String description;

    public BindPropA getA() {
        return a;
    }

    @Inject(optional = true, type = BindPropB.class, property = "description")
    public void setDescription(String desc) {
        this.description = desc;
    }

    public String getDescription() {
        return description;
    }

    @Inject
    public void setA(BindPropA a) {
        this.a = a;
    }

    public BindPropB getB() {
        return b;
    }

    @Inject(optional = true)
    public void setB(BindPropB b) {
        this.b = b;
    }
}
