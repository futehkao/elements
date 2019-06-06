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

import org.junit.jupiter.api.Test;

public class GenerateCVVTest {

    @Test
    void unpacking() throws Exception {
        GenerateCVV c1 = new GenerateCVV();
        c1.setCvk("11111111ffffffff22222222eeeeeeee");
        c1.setAccountNumber("1234567890123456");
        c1.setExpiry("1903");
        c1.setServiceCode("123");
        byte[] encoded = c1.encode();

        GenerateCVV c2 = new GenerateCVV();
        c2.decode(encoded);

        /*
        ThalesSimulator simulator = new ThalesSimulator();
        simulator.start();

        Socket socket = new Socket("localhost", 1500);
        InputStream in = new BufferedInputStream(socket.getInputStream());
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        out.write(encoded);
        out.flush();

        Thread.sleep(10000L);
        simulator.stop(); */
    }
}
