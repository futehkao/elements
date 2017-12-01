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

import java.io.Serializable;
import java.util.*;

/**
 * Created by futeh on 1/4/16.
 */
public class VaultImpl implements Vault, Serializable, Cloneable {
    private static final long serialVersionUID = 3384640081249910052L;

    // map of aliases to a SortedMap.  The SortedMap contain versioned Secrets
    private Map<String, SortedMap<Long, Secret>> secrets = new LinkedHashMap<>();

    private String name;
    private transient VersionComparator comparator = new VersionComparator();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Secret getSecret(String alias, String version) {
        SortedMap<Long, Secret> versions = secrets.get(alias);
        if (versions == null) {
            return null;
        }
        Secret secret = null;
        if (version == null) {
            secret = versions.get(versions.lastKey());
        } else {
            secret = versions.get(new Long(version));
        }
        return secret;
    }

    public void addSecret(Secret secret) {
        String alias = secret.alias();
        SortedMap<Long, Secret> versions  = secrets.computeIfAbsent(alias, key -> new TreeMap<>(comparator));
        String version = secret.version();
        if (version == null)
            version = "0";
        versions.put(new Long(version), secret);
    }

    public void removeSecret(String alias, String version) {
        SortedMap<Long, Secret> versions = secrets.get(alias);
        if (versions == null) {
            return;
        }
        if (version == null) {
            secrets.remove(alias);
        } else {
            versions.remove(new Long(version));
        }
    }

    public Set<String> aliases() {
        Set<String> aliases = new HashSet<>();
        aliases.addAll(secrets.keySet());
        return aliases;
    }

    public Set<Long> versions(String alias) {
        SortedMap<Long, Secret> versions = secrets.get(alias);
        if (versions == null)
            return new HashSet<>();
        return versions.keySet();
    }

    public int size() {
        return secrets.size();
    }

    @SuppressWarnings({"squid:S1182", "squid:S2975"})
    public VaultImpl clone() {
        VaultImpl vault = new VaultImpl();
        Map<String, SortedMap<Long, Secret>> cloneSecrets = new LinkedHashMap<>();
        for (Map.Entry<String, SortedMap<Long, Secret>> entry : secrets.entrySet()) {
            SortedMap<Long, Secret> versions = entry.getValue();
            SortedMap<Long, Secret> cloneVersions = new TreeMap<>(comparator);
            for (Map.Entry<Long, Secret> e : versions.entrySet()) {
                cloneVersions.put(e.getKey(), new Secret(e.getValue()));
            }
            cloneSecrets.put(entry.getKey(), cloneVersions);
        }

        vault.secrets = cloneSecrets;
        return vault;
    }

    /*
     * We have a getter here so that ObjectMapper can get the property name and then use field
     * to set addedSecrets during deserialization.
     */
    public Map<String, SortedMap<Long, Secret>> getSecrets() {
        return secrets;
    }

    public class VersionComparator implements Comparator<Long> {
        @Override
        public int compare(Long o1, Long o2) {
            if (o1 > o2)
                return 1;
            else if (o1 < o2)
                return -1;
            else return 0;
        }
    }
}