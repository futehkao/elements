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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.Terminal;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by futeh on 12/22/15.
 */
public class DualEntry {

    Credential user1;
    Credential user2;
    boolean newUserMode = false;

    public DualEntry() {
    }

    public DualEntry(String user, char[] password) {
        user1 = new Credential(user, password);
    }

    public DualEntry(String user, char[] password, String user2, char[] password2) {
        this.user1 = new Credential(user, password);
        this.user2 = new Credential(user2, password2);
    }

    public boolean isNewUserMode() {
        return newUserMode;
    }

    public void setNewUserMode(boolean newUserMode) {
        this.newUserMode = newUserMode;
    }

    public void setUser1(Credential user1) {
        this.user1 = user1;
    }

    public void setUser2(Credential user2) {
        this.user2 = user2;
    }

    public List<String> run(String text, int port, List<String> questions) {
        return nestedRun(text, port, questions);
    }

    public void run(String text, ServerSocket serverSocket) {
        run(text, serverSocket, Collections.emptyList());
    }

    public List<String> run(String text, ServerSocket serverSocket, List<String> questions) {
        Terminal terminal = new Terminal();

        boolean confirmed;
        do {
            confirmed = _user1(terminal, text, serverSocket);
        } while (!confirmed);

        Response response;
        do {
            response = _user2(terminal, serverSocket, questions);
        } while (!response.confirmed);
        return response.answers;
    }

    @SuppressWarnings({"squid:S3776", "squid:S2589"})
    private List<String> nestedRun(String text, int port, List<String> questions) {
        ServerSocket s = null;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            s = serverSocket;
            serverSocket.setReuseAddress(true);
            return run(text, serverSocket, questions);
        } catch (IOException e) {
            try {
                if (s != null)
                    s.close();
            } catch (Exception ex) {
                Logger.suppress(ex);
            }
            throw new SystemException(e);
        }
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134", "squid:S00100", "squid:S2093",
            "squid:S3776"})
    private boolean _user1(Terminal terminal, String text, ServerSocket serverSocket) {
        String askConnect =  serverSocket.getLocalPort() + " to provide user name and password.";
        if (user1 == null || user2 == null || user1.getUser() == null || user2.getUser() == null) {
            terminal.println(text);
            if (user1 != null && user1.getUser() != null && user1.getPassword() != null) {
                terminal.println("Please have user2 connect to port " + askConnect);
            } else {
                terminal.println("Please have user1 connect to port " + askConnect);
            }
        }

        if (user1 == null || user1.getUser() == null) {
            Terminal t = null;
            try {
                t = new Terminal(serverSocket);
                String u1 = t.readLine("Username: ");
                char[] pwd = t.readPassword("Password: ");
                while (u1.length() == 0 || pwd.length == 0) {
                    if (u1.length() == 0)
                        t.println("user name is empty...try again\n");
                    else if (pwd.length == 0)
                        t.println("password is empty...try again\n");
                    u1 = t.readLine("Username: ");
                    pwd = t.readPassword("Password: ");
                }
                if (!confirmPassword(t, pwd))
                    return false;
                user1 = new Credential(u1, pwd);
                t.println("Please have user2 connect to port " + askConnect);
            } catch (Exception ex) {
                Logger.suppress(ex);
                terminal.println("Error getting user1 name and password: " + ex.getMessage());
                return false;
            } finally {
                if (t != null)
                    t.close();
            }
        }
        return true;
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134", "squid:S00100", "squid:S2093", "squid:S3776"})
    private Response _user2(Terminal terminal, ServerSocket serverSocket, List<String> questions) {
        if (questions == null)
            questions = Collections.emptyList();
        Response response = new Response();
        while (user2 == null || user2.getUser() == null) {
            Terminal t = null;
            try {
                t = new Terminal(serverSocket);
                String u2 = t.readLine("Username:");
                char[] pwd = t.readPassword("Password:");
                if (u2 == null || pwd == null) {
                    terminal.println("Connection to user2 reset.");
                    continue;
                }
                while (u2.equalsIgnoreCase(user1.getUser()) || u2.length() == 0 || pwd.length == 0) {
                    if (u2.equalsIgnoreCase(user1.getUser()))
                        t.println("user1 cannot be the same as user2...try again\n");
                    else if (u2.length() == 0)
                        t.println("user name is empty...try again\n");
                    else if (pwd.length == 0)
                        t.println("password is empty...try again\n");
                    u2 = t.readLine("Username:");
                    pwd = t.readPassword("Password:");
                }
                if (!confirmPassword(t, pwd))
                    return response;
                user2 = new Credential(u2, pwd);
                response.answers = ask(t, questions);
            } catch (Exception e) {
                Logger.suppress(e);
                terminal.println("Error getting user2 name and password: " + e.getMessage());
                return response;
            } finally {
                if (t != null)
                    t.close();
            }
        }
        response.confirmed = true;
        return response;
    }

    private List<String> ask(Terminal terminal, List<String> questions) {
        List<String> answers = new ArrayList<>();
        for (String question : questions) {
            answers.add(terminal.readLine(question));
        }
        return answers;
    }

    private boolean confirmPassword(Terminal terminal, char[] pwd) {
        if (newUserMode) {
            char[] pwd2 = terminal.readPassword("Retype password: ");
            if (!Arrays.equals(pwd, pwd2)) {
                terminal.println("Passwords do not match");
                return false;
            }
        }
        return true;
    }

    public Credential getUser1() {
        return user1;
    }

    public Credential getUser2() {
        return user2;
    }

    public void clear() {
        if (user1 != null)
            user1.clear();
        if (user2 != null)
            user2.clear();
        user1 = user2 = null;
    }

    private static class Response {
        boolean confirmed = false;
        List<String> answers = Collections.emptyList();
    }
}
