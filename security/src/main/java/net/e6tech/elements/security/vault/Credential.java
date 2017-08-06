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

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.Terminal;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;

/**
 * Created by futeh on 12/23/15.
 */
public class Credential {

    private String user;
    private char[] password;

    public Credential() {}

    public Credential(String user, char[] password) {
        this.user = user;
        this.password = password;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }

    public void run(String text) {
        Terminal term = new Terminal();
        try {
            while (user == null || password == null) {
                term.println(text);
                user = term.readLine("Username:");
                password = term.readPassword("Password:");
            }
        } finally {
            term.close();
        }
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public void run(String text, int port) {
        if (user != null && user.length() > 0 && password != null && password.length > 0)
            return;
        ServerSocket serverSocket = null;
        Terminal t = null;
        Exception exception = null;
        try (Terminal term = new Terminal()) {
            term.println(text);
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            t = new Terminal(serverSocket);
            user = t.readLine("Username: ");
            password = t.readPassword("Password: ");
            while (user.length() == 0 || password.length == 0) {
                if (user.length() == 0)
                    t.println("user name is empty...try again\n");
                else if (password.length == 0)
                    t.println("password is empty...try again\n");
                user = t.readLine("Username:");
                password = t.readPassword("Password:");
            }
        } catch (IOException e) {
            user = null;
            password = null;
            throw new SystemException(e);
        } finally {
            if (t != null)
                t.close();
            if (serverSocket != null)
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    exception = e;
                }
        }

        if (exception != null)
            throw new SystemException(exception);
    }

    public void clear() {
        user = null;
        if (password != null)
            Arrays.fill(password, 'x');
    }

}
