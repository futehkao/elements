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

package net.e6tech.elements.security.hsm.thales;

import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.Hex;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("squid:S1700")
public abstract class Command {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static Map<String, Class<? extends Command>> commandClass = new HashMap<>();
    private static Map<Class<? extends Command>, String> classCommand = new HashMap<>();
    private static Map<String, Integer> keyTypes = new HashMap<>();

    static {
        commandClass.put("B2", Echo.class);
        commandClass.put("CW", GenerateCVV.class);
        commandClass.put("EA", VerifyPIN_ZPK_IBM.class);
        commandClass.put("DU", ChangePIN_IBM.class);

        for (Map.Entry<String, Class<? extends Command>> entry : commandClass.entrySet())
            classCommand.put(entry.getValue(), entry.getKey());

        keyTypes.put("Z", 16);  // single length ANSI
        keyTypes.put("U", 32);  // double length variant
        keyTypes.put("T", 48);  // triple length variant
        keyTypes.put("X", 32);  // double length ansi
        keyTypes.put("Y", 48);  // triple length ansi
        // S means thales proprietary keyblock.  An analogous structure would be Atalla keyblock.
        // we do not support atalla keyblock
    }

    private int lengthBytes = 2;
    private int headerLength = 4; // default is 4 bytes

    private String command;
    private String header = "0000";
    private String response;
    private String trailer;
    private int bufferSegmentSize = 256;
    private ByteBuffer packed;
    private ByteBuffer unpacked;
    private String lmkId;
    private boolean enveloped = false;

    public static Command fromBytes(byte[] bytes, int lengthBytes, int headerLength) {
        int offset = 1 + lengthBytes + headerLength; // first byte is stx
        // command
        byte[] commandBytes = Arrays.copyOfRange(bytes, offset, offset + 2);
        String command = new String(commandBytes, 0, 2, StandardCharsets.US_ASCII);
        Class<? extends Command> cls = commandClass.get(command);
        try {
            Command cmd = cls.getDeclaredConstructor().newInstance();
            return cmd.lengthBytes(lengthBytes)
                    .headerLength(headerLength)
                    .decode(bytes);
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public Command() {
        command = classCommand.get(getClass());
    }

    public Command command(String command) {
        this.command = command;
        return this;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Command enveloped(boolean e) {
        this.enveloped = e;
        return this;
    }

    public boolean isEnveloped() {
        return enveloped;
    }

    public void setEnveloped(boolean enveloped) {
        this.enveloped = enveloped;
    }

    public Command header(String header) {
        this.header = header;
        return this;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Command lengthBytes(int size) {
        this.lengthBytes = size;
        return this;
    }

    public int getLengthBytes() {
        return lengthBytes;
    }

    public void setLengthBytes(int lengthBytes) {
        this.lengthBytes = lengthBytes;
    }

    public Command headerLength(int length) {
        this.headerLength = length;
        return this;
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public void setHeaderLength(int headerLength) {
        this.headerLength = headerLength;
    }

    public Command lmkId(String lmkId) {
        setLmkId(lmkId);
        return this;
    }

    public String getLmkId() {
        return lmkId;
    }

    public void setLmkId(String lmkId) {
        this.lmkId = lmkId;
    }

    public Command trailer(String trailer) {
        this.trailer = trailer;
        return this;
    }

    public String getTrailer() {
        return trailer;
    }

    public void setTrailer(String trailer) {
        this.trailer = trailer;
    }

    public int getBufferSegmentSize() {
        return bufferSegmentSize;
    }

    public void setBufferSegmentSize(int bufferSegmentSize) {
        this.bufferSegmentSize = bufferSegmentSize;
    }

    public String response() {
        if (response != null)
            return response;
        char n = (char) (getCommand().charAt(1) + 1);
        response = "" + getCommand().charAt(0) + n;
        return response;
    }

    protected Command pack(Object ... objects) {
        for (Object object : objects) {
            byte[] bytes = null;
            if (object instanceof String) {
                bytes = ((String)object).getBytes(StandardCharsets.US_ASCII);
            } else if (object instanceof byte[]) {
                bytes = (byte[]) object;
            } else {
                throw new IllegalArgumentException("Cannot encode " + object.getClass());
            }
            encodeBytes(bytes);
        }
        return this;
    }

    private void encodeBytes(byte[] bytes) {
        expandBuffer(bytes.length);
        packed.put(bytes);
    }

    private void expandBuffer(int additionalSpace) {
        if (packed == null)
            packed = ByteBuffer.allocate(((additionalSpace + bufferSegmentSize / 2) / bufferSegmentSize + 1) * bufferSegmentSize);

        if (additionalSpace > packed.remaining()) {
            int size = packed.position();
            int newSize = ((size + additionalSpace + bufferSegmentSize / 2) / bufferSegmentSize + 1) * bufferSegmentSize;
            byte[] newBuff = new byte[newSize];
            packed.flip();
            packed.get(newBuff, 0, size);
            packed = ByteBuffer.wrap(newBuff);
            packed.position(size);
        }
    }

    protected void packFields() {
    }

    protected void packLmkId() {
        if (lmkId != null) {
            pack("%", lmkId);
        }
    }

    protected byte[] encode() {
        int envelopeSize = (enveloped) ? 3 : 0;
        int envelopeOffset = (enveloped) ? 1 : 0;
        packFields();
        packLmkId();
        int trailerLength = 0;
        if (trailer != null) {
            trailerLength = trailer.length() + 1;
        }

        if (packed == null)
            packed = ByteBuffer.wrap(EMPTY_BYTES);

        int bodyLength = packed.position();
        byte[] commandBytes = getCommand().getBytes(StandardCharsets.US_ASCII);

        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        int payloadSize = headerBytes.length + commandBytes.length + bodyLength + trailerLength;
        byte[] bytes = new byte[envelopeSize + lengthBytes + payloadSize];

        int lcr = 0;
        if (enveloped) {
            bytes[0] = 0x02;
            bytes[bytes.length - 1] = 0x03;
        }

        byte[] len = encodeLength(payloadSize, lengthBytes);
        int offset = envelopeOffset;
        System.arraycopy(len, 0, bytes, offset, len.length);  // encode length
        offset += len.length;

        // encode header
        System.arraycopy(headerBytes, 0, bytes, offset, headerBytes.length);  // encode header
        offset += headerBytes.length;

        // encode command
        System.arraycopy(commandBytes, 0, bytes, offset, commandBytes.length);
        offset += commandBytes.length;

        // encode body
        int size = packed.position();
        packed.flip();
        packed.get(bytes, offset, size);
        offset += size;

        if (trailer != null) {
            bytes[offset] = 0x19;
            offset ++;
            byte[] trailerBytes = trailer.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(trailerBytes, 0, bytes, offset, trailerBytes.length);
        }

        if (enveloped) {
            for (int i = lengthBytes + 1; i < bytes.length - 2 ; i++)
                lcr = lcr ^ bytes[i];
            bytes[bytes.length - 2] = (byte) lcr;
        }

        return bytes;
    }

    protected void unpackFields() {
    }

    protected String unpackString(int len) {
        byte[] buffer = new byte[len];
        unpacked.get(buffer, 0, len);
        return new String(buffer, StandardCharsets.US_ASCII);
    }

    protected String peekString(int len) {
        byte[] buffer = new byte[len];
        unpacked.mark();
        unpacked.get(buffer, 0, len);
        unpacked.reset();
        return new String(buffer, StandardCharsets.US_ASCII);
    }

    protected String unpackKey() {
        String keyType = peekString(1);
        Integer size = keyTypes.get(keyType);
        if (size != null) {
            unpackString(1);
            return unpackString(size);
        } else {
            return unpackString(32);
        }
    }

    protected String unpackDelimited(char delimiter) {
        int start = unpacked.position();
        unpacked.mark();
        int end = -1;
        while (unpacked.position() < unpacked.limit()) {
            int ch = (char) unpacked.get();
            ch = (0x000000FF & ch);
            if (ch == delimiter) {
                end = unpacked.position();
                break;
            }
        }

        unpacked.reset();

        if (end >= 0) {
            String str = unpackString(end - start - 1);
            unpacked.get(); // accounts for the delimiter
            return str;
        }
        return "";
    }

    protected byte[] unpackBytes(int len) {
        byte[] buffer = new byte[len];
        unpacked.get(buffer, 0, len);
        return buffer;
    }

    @SuppressWarnings("squid:S135")
    protected Command decode(byte[] bytes) {
        int envelopeOffset = enveloped ? 1 : 0;
        int offset = envelopeOffset; // first byte is stx
        byte[] len = Arrays.copyOfRange(bytes, offset, offset + lengthBytes);
        int length = decodeLength(len);
        offset += lengthBytes;

        // header
        byte[] headerByte = Arrays.copyOfRange(bytes, offset, offset + headerLength);
        offset += headerLength;
        header = new String(headerByte, StandardCharsets.US_ASCII);

        // command
        byte[] commandBytes = Arrays.copyOfRange(bytes, offset, offset + 2);
        offset += 2;
        command = new String(commandBytes, 0, 2, StandardCharsets.US_ASCII);

        // body
        byte[] body = Arrays.copyOfRange(bytes, offset, offset + length - headerLength - 2);  // the 2 comes from command length
        // at this point offset should add length - headerLength - 2

        int trailerIndex = body.length;
        for (int i = body.length - 1; i >= 0; i--) {
            if (i < body.length - 34)
                break;
            if (body[i] == 0x19) {
                trailerIndex = i;
                break;
            }
        }

        if (trailerIndex < body.length) {
            trailer = new String(body, trailerIndex + 1, body.length - trailerIndex - 1, StandardCharsets.US_ASCII);
        }

        unpacked = ByteBuffer.wrap(body, 0, trailerIndex);

        unpackFields();
        return this;
    }

    private static byte[] encodeLength(int len, int lengthBytes) {
        String length = Integer.toString(len);
        int diff = lengthBytes * 2 - length.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < diff; i++)
            builder.append("0");
        builder.append(length);
        return Hex.toBytes(builder.toString());
    }

    private static int decodeLength(byte[] bytes) {
       return Integer.parseInt(Hex.toString(bytes));
    }
}
