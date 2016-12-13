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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by futeh on 1/4/16.
 */
public class VaultFormat {
    public static final String VERSION = "1.0";

    private String version = VERSION;
    private Map<String, VaultImpl> vaults = new LinkedHashMap<>();

    public VaultFormat() {
    }

    public VaultFormat(Map<String, VaultImpl> vaults) {
        this.vaults = vaults;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, VaultImpl> getVaults() {
        return vaults;
    }

    public void setVaults(Map<String, VaultImpl> vaults) {
        this.vaults = vaults;
    }

    public void checkVersion() {
        if (getVaults().size() > 0) {
            if (getVersion() == null || !VERSION.equals(getVersion())) throw new RuntimeException("Vault format version mismatch");
        }
    }
}
