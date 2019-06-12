/*
Copyright 2015-2019 Futeh Kao

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
package net.e6tech.elements.security;

import net.e6tech.elements.common.logging.Logger;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Created by futeh.
 */
public class RNG {
    private static String rngAlgorithm = "SHA1PRNG";

    private RNG() {
    }

    static {
        try {
            rngAlgorithm = "NativePRNGNonBlocking";
            SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (NoSuchAlgorithmException e) {
            Logger.suppress(e);
            try {
                rngAlgorithm = "SHA1PRNG";
                SecureRandom.getInstance(rngAlgorithm);
            } catch (NoSuchAlgorithmException e2) {
                Logger.suppress(e2);
                rngAlgorithm = null;
            }
        }
    }

    public static SecureRandom getSecureRandom() {
        if (rngAlgorithm == null)
            return new SecureRandom();
        try {
            return SecureRandom.getInstance(rngAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            Logger.suppress(e);
            return new SecureRandom();
        }
    }

    public static byte[] generateSeed(int len) {
        return getSecureRandom().generateSeed(len);
    }

    public static void nextBytes(byte[] bytes) {
        getSecureRandom().nextBytes(bytes);
    }

}
