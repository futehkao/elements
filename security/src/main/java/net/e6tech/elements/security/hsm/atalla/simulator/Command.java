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

package net.e6tech.elements.security.hsm.atalla.simulator;

import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.hsm.AnsiPinBlock;
import net.e6tech.elements.security.hsm.atalla.Message;


import java.security.GeneralSecurityException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
public abstract class Command extends Message {
    private static Map<String, Class<? extends Command>> commands = new Hashtable<>();

    static {
        commands.put("00", Echo.class);
        commands.put("5D", GenerateCVV.class);
        commands.put("30", EncryptPIN.class);
        commands.put("37", ChangePIN.class);
        commands.put("32", VerifyPIN.class);
        commands.put("31", TranslatePIN.class);
    }

    protected AtallaSimulator simulator;
    protected String[] fields;

    public Command() {
    }

    public Command(String response) {
        super(response);
    }

    public static Command createInstance(String message, AtallaSimulator simulator) {
        try {
            Message msg = new Message(message);
            Command request = commands.get(msg.getField(0)).newInstance();
            request.simulator = simulator;
            request.fields = msg.getFields();
            return request;
        } catch (Throwable e) {

        }
        return null;
    }

    public String getField(int index) {
        return fields[index];
    }

    public Message process() {
        try {
            return new Message("<" + doProcess() + ">");
        } catch (CommandException e) {
            return new Message("<00#" + e.error() + ">");
        }
    }

    protected abstract String doProcess() throws CommandException;

    public static <T> T run(int fieldNumber, Callable<T> call) throws CommandException {
        try {
            return call.call();
        } catch (Throwable th) {
            throw new CommandException(fieldNumber, th);
        }
    }

    protected void setDecimalizationTable(IBM3624PINOffset ibm, int fieldNo) throws CommandException {
        String decTab = getField(fieldNo);
        if (decTab.indexOf(",") > 0) { // akb
            try {
                decTab = Hex.toString(simulator.decryptKey(new AKB(decTab)));
            } catch (GeneralSecurityException e) {
                throw new CommandException(fieldNo, e);
            }
        } else if (decTab.length() == 16) { //

        } else {
            throw new CommandException(fieldNo, new IllegalArgumentException());
        }

        try {
            ibm.setDecimalizationTable(decTab);
        } catch (GeneralSecurityException e) {
            throw new CommandException(fieldNo, e);
        }
    }

    protected AnsiPinBlock getPinBlock(int kpeField, int pinBlockField, int partialPanField) throws CommandException {
        AKB kpe = run(kpeField, () -> new AKB(getField(kpeField)));
        byte[] pinBlock = run(pinBlockField, () -> simulator.decrypt(kpe, getField(pinBlockField)));
        return new AnsiPinBlock(pinBlock, getField(partialPanField));
    }

    protected byte[] decryptKey(int keyField) throws CommandException {
        try {
            return simulator.decryptKey(new AKB(getField(keyField)));
        } catch (GeneralSecurityException e) {
            throw new CommandException(keyField, e);
        }
    }

    protected byte[] decrypt(int keyField, int dataField) throws CommandException {
        AKB akb = run(keyField, ()-> new AKB(getField(keyField)));
        return run(dataField, ()-> simulator.decrypt(akb, getField(dataField)));
    }
}
