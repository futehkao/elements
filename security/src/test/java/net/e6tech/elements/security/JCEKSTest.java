/*
 * Copyright 2015 Futeh Kao
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

package net.e6tech.elements.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

/**
 * Created by futeh.
 */
public class JCEKSTest {

    @Test
    public void basic() throws Exception {
        char[] password = "password".toCharArray();
        char[] password2 = "password2".toCharArray();
        JCEKS jceks = new JCEKS();
        jceks.createSelfSignedCertificate("alias", "CN=www.nowhere.com,OU=IT,O=No Where,L=Austin,ST=Texas,C=US",
                password, 3);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        char[] filePassword = "password".toCharArray();
        jceks.save(output, filePassword);
        byte[]  bytes = output.toByteArray();
        System.out.println(bytes.length);
        jceks.init(password);

    }
}
