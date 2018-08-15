/*
Copyright 2015 Futeh Kao

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

package net.e6tech.elements.security.vault;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.common.serialization.ObjectMapperFactory;

/**
 * Created by futeh on 1/3/16.
 */
public class Constants {

    public static final String TYPE = "type";
    public static final String KEY_TYPE = "key";
    public static final String KEY_PAIR_TYPE = "key-pair";
    public static final String USER_TYPE = "user";
    public static final String SECRET_TYPE = "secret";
    public static final String SIGNATURE_TYPE = "signature";
    public static final String PASSPHRASE = "passphrase";
    public static final String PASSPHRASE_TYPE = PASSPHRASE;

    public static final String ALIAS = "alias";
    public static final String ALGORITHM = "algorithm";
    public static final String GUARDIAN = "guardian";
    public static final String GROUP_1 = "group-1";
    public static final String GROUP_2 = "group-2";
    public static final String USERNAME = "username";

    public static final String MASTER_KEY_ALIAS = "m-key";
    public static final String ASYMMETRIC_KEY_ALIAS = "asy-key";
    public static final String AUTHORIZATION_KEY_ALIAS = "auth-key";
    public static final String CREATION_TIME = "creation-time";
    public static final String CREATION_DATE_TIME = "creation-date-time";
    public static final String VERSION = "version";
    public static final String SIGNATURE = SIGNATURE_TYPE;
    public static final String SIGNATURE_FORMAT = "signature-format";
    public static final String SIGNATURE_FORMAT_VERSION = "1.0";

    public static final ObjectMapper mapper = ObjectMapperFactory.newInstance();

    private Constants() {
    }

}
