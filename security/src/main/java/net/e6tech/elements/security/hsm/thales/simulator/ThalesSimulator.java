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

@SuppressWarnings("all")
public class ThalesSimulator extends Simulator {

    static Logger logger = Logger.getLogger();

    private int headerLength = 4; // default is 4 bytes
    private boolean enveloped = false;  // assumes the payload is bracketed by STX and ETX

    public ThalesSimulator() {
        setPort(1500);  // default lmk
    }

    protected void process(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] length = new byte[Command.LENGTH_BYTES];

        try (BufferedInputStream in = new BufferedInputStream(inputStream);
             BufferedOutputStream out = new BufferedOutputStream(outputStream);) {
            while (true) {
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

                short len = Command.decodeLength(length);
                byte[] buffer = new byte[len + envelopeSize + Command.LENGTH_BYTES];
                read(in, buffer, envelopeOffset + Command.LENGTH_BYTES);

                if (enveloped)
                    buffer[0] = (byte) stx;

                for (int i = 0; i < Command.LENGTH_BYTES; i++) {
                    buffer[i + envelopeOffset] = length[i];
                }

                Command cmd = Command.fromBytes(buffer, headerLength);
                CommandProcessor processor = CommandProcessor.forCommand(cmd);

            }
        }
    }

    protected void read(InputStream in, byte[] buffer, int offset) throws IOException {
        int read = offset;
        do {
            int r = in.read(buffer, read, buffer.length - read);
            read += r;
        } while (read < buffer.length);
    }

}
