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

package net.e6tech.elements.security.hsm.atalla;

/**
 * Created by futeh.
 */
public class Message {
    String[] fields;

    public Message() {
    }

    public Message(String message) {
        parse(message);
    }

    protected void parse(String message) {
        int beginIndex = message.indexOf("<");
        int endIndex = message.lastIndexOf(">");
        if (beginIndex < 0 || endIndex < 0) throw new BadMessageException("Cannot find message delimiter");
        if (beginIndex >= endIndex) throw new BadMessageException("end delimiter is found before begin delimiter.");  // todo
        message = message.substring(beginIndex + 1, endIndex);
        String[] tokens = message.split("#");
        for (int i = 0; i < tokens.length; i++) tokens[i] = tokens[i].trim();
        fields = tokens;
    }

    public String getField(int index) {
        return fields[index];
    }

    public String[] getFields() {
        if (fields == null) return null;
        String[] flds = new String[fields.length];
        System.arraycopy(fields, 0, flds, 0, fields.length);
        return flds;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<");
        if (fields != null) {
            for (String f : fields) {
                builder.append(f);
                builder.append("#");
            }
        }
        builder.append(">");
        return builder.toString();
    }
}
