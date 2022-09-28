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

import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.security.Hex;
import net.e6tech.elements.security.hsm.Simulator;
import net.e6tech.elements.security.hsm.atalla.Message;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S3008", "squid:S2278"})
public class AtallaSimulator extends Simulator {

    static String MASTER_KEY = "2ABC3DEF4567018998107645FED3CBA20123456789ABCDEF";

    // test keys
    static String IMK_ARQC = "1mENE000,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String IMK_SM_MAC = "1mENE00M,ABCD ABCD EF01 EF01 10FE 10FE DCBA DCBA";
    static String IMK_SM_ENC = "1mENE00E,1234 1234 5678 5678 8765 8765 4321 4321";
    static String KPV_VISA = "1VVNE000,3333 3333 3333 3333 4444 4444 4444 4444";
    static String KPV_IBM3624 = "1V3NE000,3333 3333 3333 3333 4444 4444 4444 4444";
    static String KPE_INTERCHANGE = "1PUNE000,5555 5555 5555 5555 6666 6666 6666 6666";
    static String KPE_BANK = "1PUNE000,1111 1111 1111 1111 2222 2222 2222 2222";
    static String KCVV = "1CDNE000,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String KMAC = "1M2NE000,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String KEK_KPE = "1PUNE0I0,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String DEC = "1nCNE000,0123456789012345";

    static Logger logger = Logger.getLogger();

    private byte[] masterKey = Hex.toBytes(MASTER_KEY); // triple des is 24 bytes,
    protected Map<String, String> keys = new LinkedHashMap<>();

    public AtallaSimulator() throws GeneralSecurityException {
        Field[] fields = AtallaSimulator.class.getDeclaredFields();
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())
                    && f.getType().isAssignableFrom(String.class)) {
                f.setAccessible(true);
                try {
                    String value = (String) f.get(null);
                    String[] keyComponents = value.split(",");
                    if (keyComponents.length == 2)
                        keys.put(f.getName(), value);
                } catch (IllegalAccessException e) {
                    Logger.suppress(e);
                }
            }
        }
        setPort(7000);
    }

    public String getMasterKey() {
        return Hex.toString(masterKey);
    }

    public void setMasterKey(String mkey) {
        masterKey = Hex.toBytes(mkey);
    }

    byte[] masterKeyBytes() {
        return masterKey;
    }

    public AKB getKey(String keyType) throws GeneralSecurityException {
        String key = keys.get(keyType);
        if (key == null)
            return null;
        return asAKB(key);
    }

    public AKB asAKB(String headerAndKey) throws GeneralSecurityException {
        String[] fields = headerAndKey.split(",");
        return  new AKB(fields[0].trim(), masterKey, Hex.toBytes(fields[1]));
    }

    public AKB asAKB(String header, byte[] key) throws GeneralSecurityException {
        return new AKB(header, masterKey, key);
    }

    public byte[] decryptKey(AKB akb) throws GeneralSecurityException {
        return akb.decryptKey(masterKey);
    }

    public byte[] decrypt(AKB akb, String encrypted) throws GeneralSecurityException {
        return decrypt(akb, Hex.toBytes(encrypted));
    }

    public byte[] decrypt(AKB akb, byte[] encrypted) throws GeneralSecurityException {
        byte[] key =  akb.decryptKey(masterKey);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        SecretKey secretKey = new SecretKeySpec(AKB.normalizeKey(key), "DESede");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encrypted);
    }

    public byte[] encrypt(AKB akb, String clearText) throws GeneralSecurityException {
        return encrypt(akb, Hex.toBytes(clearText));
    }

    public byte[] encrypt(AKB akb, byte[] clearText) throws GeneralSecurityException {
        byte[] key =  akb.decryptKey(masterKey);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        SecretKey secretKey = new SecretKeySpec(AKB.normalizeKey(key), "DESede");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(clearText);
    }

    /*
     * Imports a key encrypted under kek. Returns an AKB that is encrypted under master key.
     */
    public AKB importKey(AKB akb, byte[] encryptedKey) throws GeneralSecurityException {
        String header = akb.getHeader().substring(0, 5) + "000";
        byte[] plainKey =  decrypt(akb, encryptedKey);
        return asAKB(header, plainKey);
    }

    protected void process(InputStream inputStream, OutputStream outputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             PrintWriter writer =  new PrintWriter(new OutputStreamWriter(outputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                Command request = Command.createInstance(line, this);
                Message response = request.process();
                writer.println(response);
                writer.flush();
            }
            logger.info("{} client exited", getClass().getSimpleName());
        }
    }

    @SuppressWarnings("squid:S106")
    public static void main(String ... args) throws Exception  {
        AtallaSimulator simulator = new AtallaSimulator();
        for (String keyType : simulator.keys.keySet()) {
            AKB akb = simulator.getKey(keyType);
            String headerAndKey = simulator.keys.get(keyType);
            String[] fields = headerAndKey.split(",");
            System.out.println("Key: " + keyType);
            System.out.println("Key 1: " + fields[1]);
            System.out.println("Check Digits: " + akb.getCheckDigits());
            System.out.println();
        }

        AKB akb = simulator.asAKB("1mENE000,9E15204313F7318A CB79B90BD986AD29");
        System.out.println("Key: " + "9E15204313F7318A CB79B90BD986AD29");
        System.out.println("Check Digits: " + akb.getCheckDigits());
        System.out.println();

        simulator.start();
    }
}
