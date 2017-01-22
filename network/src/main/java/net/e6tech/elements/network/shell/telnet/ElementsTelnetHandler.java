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

package net.e6tech.elements.network.shell.telnet;

import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionEvent;
import net.wimpi.telnetd.shell.Shell;
import org.crsh.telnet.term.TelnetIO;
import org.crsh.telnet.term.spi.TermIOHandler;

/**
 * Created by futeh.
 */
public class ElementsTelnetHandler implements Shell {

    public void run(Connection conn) {

        // Prevent screen flickering
        conn.getTerminalIO().setAutoflushing(false);

        //
        TelnetIO io = new TelnetIO(conn);
        ElementsTelnetLifeCycle lifeCycle = ElementsTelnetLifeCycle.getLifeCycle(conn);
        TermIOHandler handler = lifeCycle.getHandler();
        handler.handle(io, null);
    }

    public void connectionIdle(ConnectionEvent connectionEvent) {
    }

    public void connectionTimedOut(ConnectionEvent connectionEvent) {
    }

    public void connectionLogoutRequest(ConnectionEvent connectionEvent) {
    }

    public void connectionSentBreak(ConnectionEvent connectionEvent) {
    }

    public static Shell createShell() {
        return new ElementsTelnetHandler();
    }
}
