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
package net.e6tech.elements.security.auth;

import java.security.Principal;
import java.util.Collections;
import java.util.Set;

/**
 * Created by futeh.
 */
public class Roles implements Principal {
    private static final Set<String> EMPTY = Collections.emptySet();

    private Set<String> roleSet;

    public Roles() {
        roleSet = EMPTY;
    }

    public Roles(Set<String> roles) {
        this.roleSet = roles;
    }

    public Set<String> getRoles() {
        return roleSet;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof Roles) {
            return roleSet.equals(object);
        }
        return false;
    }

    public int hashCode() {
        return roleSet.hashCode();
    }

    public String getName() {
        return "Roles";
    }

    public String toString() {
        return "Roles: " + roleSet;
    }
}
