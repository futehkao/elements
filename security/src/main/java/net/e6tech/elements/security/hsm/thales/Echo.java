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

import net.e6tech.elements.common.util.StringUtil;

import java.nio.charset.StandardCharsets;

public class Echo extends Command {
    private byte[] length;
    private String data;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    protected void packFields() {
        int len = data.length();
        String hex = Integer.toHexString(len);
        length = StringUtil.padLeft(hex, 4, '0').getBytes(StandardCharsets.US_ASCII);
        pack(length, data);
    }

    @Override
    protected void unpackFields() {
        length = unpackBytes(4);
        String hex = new String(length, StandardCharsets.US_ASCII);
        int len = Integer.parseInt(hex, 16);
        data = unpackString(len);
    }
}
