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


package net.e6tech.elements.common.util;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by futeh on 12/22/15.
 */
public class Terminal implements Closeable {

    Emulator emulator;

    public Terminal() {
        Console console =  System.console();
        if (console != null) {
            emulator = new NativeConsole(console);
        } else {
            emulator = new StreamConsole(new BufferedReader(new InputStreamReader(System.in)),
                    new PrintWriter(new OutputStreamWriter(System.out))) {
                @Override
                public void close() {
                }
            };
        }
    }

    public Terminal(InputStream in, OutputStream out) {
        emulator = new StreamConsole(new BufferedReader(new InputStreamReader(in)),
                new PrintWriter(new OutputStreamWriter(out)));
    }

    public Terminal(ServerSocket serverSocket) throws IOException {
        final Socket socket = serverSocket.accept();
        try {
            emulator = new StreamConsole(new BufferedReader(new InputStreamReader(socket.getInputStream())),
                    new PrintWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                @Override
                public void close() {
                    try {
                        super.close();
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException e) {

                        }
                    }
                }
            };
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String readLine(String text) {
        return emulator.readLine(text);
    }

    public char[] readPassword(String text)  {
        return emulator.readPassword(text);
    }

    public void println(String text) {
        emulator.println(text);
    }

    public void print(String text) {
        emulator.print(text);
    }

    @Override
    public void close() {
        emulator.close();
    }

    interface Emulator {

        String readLine(String text);

        char[] readPassword(String text);

        void println(String text);

        void print(String text);

        void close();
    }

    class StreamConsole implements Emulator {

        private BufferedReader input;
        private PrintWriter output;

        StreamConsole(BufferedReader in, PrintWriter out) {
            this.input = in;
            this.output = out;
        }

        @Override
        public String readLine(String text) {
            output.print(text); output.flush();
            try {
                return input.readLine().trim();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (NullPointerException e) {
                return null;
            }
        }

        @Override
        public char[] readPassword(String text) {
            output.println(text); output.flush();
            String str = null;
            try {
                str = input.readLine().trim();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (NullPointerException e) {
                return null;
            }
            return str.toCharArray();
        }

        @Override
        public void println(String text) {
            output.println(text); output.flush();
        }

        @Override
        public void print(String text) {
            output.print(text); output.flush();
        }

        @Override
        public void close() {
            IOException exception = null;
            try {
                input.close();
            } catch (IOException e) {
                exception = e;
            }

            output.close();

            if (exception != null) throw new RuntimeException(exception);
        }
    }

    class NativeConsole implements Emulator {
        Console console;

        NativeConsole(Console console) {
            this.console = console;
        }

        @Override
        public String readLine(String text) {
            print(text);
            try {
                return console.readLine().trim();
            } catch (NullPointerException e) {
                return "";
            }
        }

        @Override
        public char[] readPassword(String text) {
            try {
                char[] ret = console.readPassword(text);
                if (ret == null) return new char[0];
                return ret;
            } catch (NullPointerException e)  {
                return new char[0];
            }
        }

        @Override
        public void println(String text) {
            console.printf(text);
            console.printf("\n");
        }

        @Override
        public void print(String text) {
            console.printf(text);
        }

        @Override
        public void close() {
        }
    }

    public static void main(String ... args) {
        Terminal term = new Terminal();
        String user = term.readLine("Username:");
        char[] password = term.readPassword("Password:");

        System.out.println(user + ":" + new String(password));
    }
}
