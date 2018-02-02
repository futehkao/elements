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
import net.e6tech.elements.security.hsm.atalla.Message;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by futeh.
 */
@SuppressWarnings({"squid:S3008", "squid:S2278"})
public class AtallaSimulator {

    static String MASTER_KEY = "2ABC3DEF4567018998107645FED3CBA20123456789ABCDEF";

    // test keys
    static String IMK_ARQC = "1mENE000,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String IMK_SM_MAC = "1mENN00M,ABCD ABCD EF01 EF01 10FE 10FE DCBA DCBA";
    static String IMK_SM_ENC = "1mENN00E,1234 1234 5678 5678 8765 8765 4321 4321";
    static String KPV_VISA = "1VVNE000,3333 3333 3333 3333 4444 4444 4444 4444";
    static String KPV_IBM3624 = "1V3NE000,3333 3333 3333 3333 4444 4444 4444 4444";
    static String KPE_INTERCHANGE = "1PUNE000,5555 5555 5555 5555 6666 6666 6666 6666";
    static String KPE_BANK = "1PUNE000,1111 1111 1111 1111 2222 2222 2222 2222";
    static String KCVV = "1CDNE000,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String KEK_KPE = "1PUNE000,0123 4567 89AB CDEF FEDC BA98 7654 3210";
    static String DEC = "1nCNE000,0123456789012345";

    static Logger logger = Logger.getLogger();

    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private int port = 7000;
    private byte[] masterKey = Hex.toBytes(MASTER_KEY); // triple des is 24 bytes,
    private boolean stopped = true;
    protected Map<String, String> keys = new HashMap<>();

    public AtallaSimulator() throws GeneralSecurityException {
        Field[] fields = AtallaSimulator.class.getDeclaredFields();
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())
                    && f.getType().isAssignableFrom(String.class)) {
                f.setAccessible(true);
                try {
                    keys.put(f.getName(), (String) f.get(null));
                } catch (IllegalAccessException e) {
                    Logger.suppress(e);
                }
            }
        }
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

    public boolean isStopped() {
        return stopped;
    }

    public void start() {
        if (threadPool == null) {
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            threadPool = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(group, runnable, "AtallaSimulator");
                thread.setName("AtallaSimulator-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            });
        }
        Thread thread = new Thread(this::startServer);
        thread.start();
    }

    @SuppressWarnings({"squid:S134", "squid:S1141"})
    protected void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            stopped = false;
            while (! stopped) {
                final Socket socket = serverSocket.accept();
                threadPool.execute(()-> {
                    try {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                             PrintWriter writer =  new PrintWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                Command request = Command.createInstance(line, this);
                                Message response = request.process();
                                writer.println(response);
                                writer.flush();
                            }
                            logger.info("Atalla client exited");
                        }
                    } catch (IOException e) {
                        logger.trace(e.getMessage(), e);
                    }
                });
            }
        } catch (Exception th) {
            throw logger.systemException(th);
        }
    }

    public void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.suppress(e);
            } finally {
                stopped = true;
            }
        }
    }

    public static void main(String ... args) throws Exception  {
        byte[] kekKey = Hex.toBytes("0123456789ABCDEFFEDCBA9876543210");
        String header = "1KDEE000";
        AtallaSimulator atalla = (new AtallaSimulator());
        AKB akb = new AKB(header, atalla.masterKey, kekKey);
        akb.decryptKey(atalla.masterKey);
    }
}
