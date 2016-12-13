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

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginException;
import java.util.Map;

/**
 * Created by futeh.
 */
public class UsernamePasswordLoginModule implements javax.security.auth.spi.LoginModule {
    CallbackHandler handler;
    Subject subject;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.handler = callbackHandler;
    }

    @Override
    public boolean login() throws LoginException {
        NameCallback name = new NameCallback("username");
        PasswordCallback password = new PasswordCallback("password", false);
        try {
            handler.handle(new Callback[] {name, password});
        } catch (Exception e) {
            throw new LoginException(e.getMessage());
        }
        subject.getPrincipals().add(new UserPrincipal(name.getName()));
        subject.getPrivateCredentials().add(name);
        subject.getPrivateCredentials().add(password);
        return true;
    }

    @Override
    public boolean commit() throws LoginException {
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        return true;
    }
}
