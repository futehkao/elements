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
package net.e6tech.elements.network.restful;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.common.util.concurrent.ObjectPool;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by futeh.
 */
public class Response implements Serializable {
    private static final long serialVersionUID = 775319303475963086L;
    public static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public static final ObjectPool<TypeReferenceImpl> objectPool = new ObjectPool<TypeReferenceImpl>() {
    }.limit(200).build();

    private int responseCode;
    private Map<String, List<String>> headerFields = new HashMap<>();
    private String result;

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Map<String, List<String>> getHeaderFields() {
        return headerFields;
    }

    public void setHeaderFields(Map<String, List<String>> headerFields) {
        this.headerFields = headerFields;
    }

    @SuppressWarnings("unchecked")
    public <T> T read(Class<T> cls) throws IOException {
        if (result == null || cls.isAssignableFrom(String.class))
            return (T) result;
        return mapper.readValue(result, cls);
    }

    public <T> T read(Type type) throws IOException {
        if (result == null || (type instanceof Class && ((Class) type).isAssignableFrom(String.class)))
            return (T) result;

        try {
            return objectPool.apply(impl -> {
                try (TypeReferenceImpl i = impl) {
                    i.setType(type);
                    return (T) mapper.readValue(result, i);
                } catch (Exception e) {
                    throw new SystemException(e);
                }
            });
        } catch (Exception ex) {
            Throwable th = ExceptionMapper.unwrap(ex);
            if (th instanceof IOException)
                throw (IOException) th;
            throw new IOException(th);
        }
    }

    public String toString() {
        return "responseCode=" + responseCode + " headers=" + headerFields + " result=" + result;
    }

    public boolean isSuccess() {
        return !(getResponseCode() < 200 || getResponseCode() > 202);
    }

    // need to be public for ObjectPool to access
    public static class TypeReferenceImpl extends TypeReference<Object> implements AutoCloseable {
        private Type type;

        @Override
        public Type getType() {
            return type;
        }

        protected void setType(Type type) {
            this.type = type;
        }

        @Override
        public void close() {
            type = null;
        }
    }
}
