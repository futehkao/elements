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
import net.e6tech.elements.network.shell.PasswordAuthenticationPlugin
import org.crsh.auth.AuthenticationPlugin
import org.crsh.cli.Argument
import org.crsh.cli.Command
import org.crsh.cli.Required
import org.crsh.cli.Usage

import java.security.Principal

@Usage("User management - add user, change password")
class user {

    @Usage("change password")
    @Command
    public void pwd() {
        Principal principal = context.session.principal

        if (principal == null) {
            out.println("Cannot retrieve user name")
            return;
        }

        String username = principal.getName()
        chgpwd(username)
    }

    private void chgpwd(String username) {

        PasswordAuthenticationPlugin auth
        for (Object o : crash.context.getPlugins(AuthenticationPlugin)) {
            if (o instanceof PasswordAuthenticationPlugin) auth = o;
        }

        if (auth.hasPassword(username)) {
            String line = context.readLine("enter your current password:", false);
            if (auth != null && line != null) {
                if (!auth.authenticate(username, line)) return;
            } else {
                return
            }
        }

        String pwd1 = ""
        String pwd2 = ""
        while (!Thread.currentThread().interrupted()) {
            pwd1 = context.readLine("enter your new password:", false);
            if (pwd1 == null || pwd1.length() < 7) {
                out.println("password must be 7 characters or more");
                continue
            }
            pwd2 = context.readLine("enter your new password again:", false);

            if (!pwd1.equals(pwd2)) {
                out.println("passwords don't match")
            } else {
                break;
            }
        }

        if (!pwd1.equals(pwd2)) {
            return
        }

        try {
            auth.savePassword(username, pwd1);
        } catch (Exception ex) {
            out.println("unable to save password: " + ex.getMessage())
        }
    }

    @Usage("new user")
    @Command
    public void add(@Usage("user name") @Required @Argument String username) {
        chgpwd(username)
    }
}