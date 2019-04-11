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

package net.e6tech.elements.network.cluster;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionSerializer {

    public static byte[] toBytes(Object obj) throws IOException {
        if(obj != null) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 GZIPOutputStream zos = new GZIPOutputStream(bos);
                 ObjectOutputStream oos = new ObjectOutputStream(zos)) {
                oos.writeObject(obj);
                oos.flush();
                zos.finish();
                return bos.toByteArray();
            }
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromBytes(byte[] obj) throws IOException, ClassNotFoundException {
        if(obj != null) {
            try (ObjectInputStream ois =
                         new ObjectInputStream(
                                 new GZIPInputStream(
                                         new ByteArrayInputStream(obj)))) {
                return (T)ois.readObject();
            }
        } else {
            return null;
        }
    }
}
