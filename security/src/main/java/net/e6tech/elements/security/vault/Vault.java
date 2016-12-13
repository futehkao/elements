/*
Copyright 2015 Futeh Kao

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
package net.e6tech.elements.security.vault;

import java.util.Set;

/**
 * Created by futeh.
 */
public interface Vault  {

    String getName();

    Secret getSecret(String alias, String version);

    void addSecret(Secret secret);

    void removeSecret(String alias, String version);

    Set<String> aliases();

    Set<Long> versions(String alias);

    int size();

}
