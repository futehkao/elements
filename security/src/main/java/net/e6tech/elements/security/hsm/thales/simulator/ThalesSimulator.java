/*
 * Copyright 2015-2019 Futeh Kao
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

package net.e6tech.elements.security.hsm.thales.simulator;

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.security.hsm.Simulator;
import net.e6tech.elements.security.hsm.thales.Command;

import java.io.*;

public class ThalesSimulator extends Simulator {

    static Logger logger = Logger.getLogger();

    private int lengthBytes = 2;
    private int headerLength = 4; // default is 4 bytes
    private boolean enveloped = false;  // assumes the payload is bracketed by STX and ETX

    public ThalesSimulator() {
        setPort(1500);  // default lmk
    }

    protected void process(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] length = new byte[lengthBytes];
        while (true) {
            try (BufferedInputStream in = new BufferedInputStream(inputStream);
                 BufferedOutputStream out = new BufferedOutputStream(outputStream)) {

                int envelopeSize = 0;
                int envelopeOffset = 0;
                int stx = 0x02;
                if (enveloped) {
                    stx = in.read(); // STX
                    envelopeSize = 3;
                    envelopeOffset = 1;
                }

                // read length
                read(in, length, 0);

                int len = bcd(length);
                byte[] buffer = new byte[len + envelopeSize + lengthBytes];
                read(in, buffer, envelopeOffset + lengthBytes);

                if (enveloped)
                    buffer[0] = (byte) stx;

                for (int i = 0; i < lengthBytes; i++) {
                    buffer[i + envelopeOffset] = length[i];
                }

                Command cmd = Command.fromBytes(buffer, lengthBytes, headerLength);
                CommandProcessor processor = CommandProcessor.forCommand(cmd);
            }
        }
    }

    protected int bcd(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append((b & 0x00f0) >>> 4)
                    .append(b & 0x000f);
        }
        return Integer.parseInt(builder.toString());
    }

    protected void read(InputStream in, byte[] buffer, int offset) throws IOException {
        int read = offset;
        do {
            int r = in.read(buffer, read, buffer.length - read);
            read += r;
        } while (read < buffer.length);
    }

}
