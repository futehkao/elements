/*
 * Copyright 2015-2022 Futeh Kao
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

package net.e6tech.elements.web.federation;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.util.Pool;
import net.e6tech.elements.common.util.SystemException;
import org.objenesis.strategy.SerializingInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.SerializedLambda;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SubZero {
    private Pool<Kryo> pool;
    int compressionLevel = Deflater.BEST_SPEED;

    public SubZero() {
        pool = new Pool<Kryo>(true, false, 64) {
            protected Kryo create() {
                Kryo kryo = new Kryo();
                kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new SerializingInstantiatorStrategy()));
                kryo.setRegistrationRequired(false);
                kryo.register(Object[].class);
                kryo.register(Class.class);
                kryo.register(SerializedLambda.class);
                kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());
                return kryo;
            }
        };
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public byte[] freeze(Object obj) {
        Kryo kryo = pool.obtain();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream zos = new GZIPOutputStreamExt(bos)) {
            Output output = new Output(zos);
            kryo.writeClassAndObject(output, obj);
            output.close();
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new SystemException(ex);
        } finally {
            pool.free(kryo);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T thaw(byte[] bytes) {
        if (bytes == null)
            return null;
        Kryo kryo = pool.obtain();
        try (GZIPInputStream zin = new GZIPInputStream(new ByteArrayInputStream(bytes));
             Input input = new Input(zin)) {
            return (T) kryo.readClassAndObject(input);
        } catch (IOException ex) {
            throw new SystemException(ex);
        } finally {
            pool.free(kryo);
        }
    }

    class GZIPOutputStreamExt extends GZIPOutputStream {
        GZIPOutputStreamExt(OutputStream out) throws IOException {
            super(out);
            this.def.setLevel(compressionLevel);
        }
    }
}
