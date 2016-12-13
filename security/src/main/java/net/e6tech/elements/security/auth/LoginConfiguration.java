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
package net.e6tech.elements.security.auth;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class LoginConfiguration extends Configuration {

    Map<String, ?> options = new HashMap<>();
    String loginModuleClass;
    AppConfigurationEntry.LoginModuleControlFlag controlFlag = AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;

    public Map<String, ?> getOptions() {
        return options;
    }

    public void setOptions(Map<String, ?> options) {
        this.options = options;
    }

    public String getLoginModuleClass() {
        return loginModuleClass;
    }

    public void setLoginModuleClass(String loginModuleClass) {
        this.loginModuleClass = loginModuleClass;
    }

    public AppConfigurationEntry.LoginModuleControlFlag getControlFlag() {
        return controlFlag;
    }

    public void setControlFlag(AppConfigurationEntry.LoginModuleControlFlag controlFlag) {
        this.controlFlag = controlFlag;
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        return new AppConfigurationEntry[] { new AppConfigurationEntry(loginModuleClass,
                controlFlag, options)};
    }
}
