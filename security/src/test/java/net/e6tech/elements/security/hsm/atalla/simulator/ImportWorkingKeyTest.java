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
import net.e6tech.elements.security.hsm.atalla.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by futeh.
 */
public class ImportWorkingKeyTest extends CommandTest<ImportWorkingKey> {

    @Test
    public void importWorkingKey() throws Exception {
        String clearKeK ="1CDNN0I0,0123456789ABCDEFFEDCBA9876543210";
        String plainWorkingKey = "0123456789ABCDEFFEDCBA9876543210";
        AKB kek = simulator.asAKB(clearKeK);
        byte[] encryptedWorkingKey = simulator.encrypt(kek, plainWorkingKey);
        String[] fields = new String[4];
        fields[0] = "11B";
        fields[1] = "0"; // variant
        fields[2] = Hex.toString(encryptedWorkingKey);
        fields[3] = kek.getKeyBlock();
        getCommand().setFields(fields);
        Message message = getCommand().process();
        message.getField(1).equals("1CDNN000,64A883D036BBEF32BF146E43A1BC6DF0B1264D674A68E267,88D88EA266E7D54F");
        message.getField(2).equals("08D7");
    }
}
