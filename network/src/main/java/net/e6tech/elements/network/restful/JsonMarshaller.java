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

package net.e6tech.elements.network.restful;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.common.serialization.ObjectMapperFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class JsonMarshaller<R> implements Marshaller<R> {
    public static final ObjectMapper mapper = ObjectMapperFactory.newInstance();

    private Class<R> errorResponseClass ;

    public JsonMarshaller() {
    }

    public JsonMarshaller(Class<R> errorResponseClass) {
        this.errorResponseClass = errorResponseClass;
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public String getAccept() {
        return "application/json";
    }

    @Override
    public String prettyPrintRequest(Object data) throws Exception {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }

    @Override
    public String encodeRequest(Object data) throws Exception {
        return mapper.writeValueAsString(data);
    }

    @Override
    public String prettyPrintResponse(String response) throws Exception {
        Object ret;
        if (response != null && response.length() > 0) {
            if (response.startsWith("[")) {
                ret = mapper.readValue(response, List.class);
            } else if (response.startsWith("{")) {
                ret = mapper.readValue(response, Map.class);
            } else if (response.startsWith("\"")){
                ret = mapper.readValue(response, String.class);
            } else if (Character.isDigit(response.charAt(0))) {
                if (response.contains(".")) {
                    ret = mapper.readValue(response, BigDecimal.class);
                } else {
                    ret = mapper.readValue(response, Long.class);
                }
            } else if ("true".equalsIgnoreCase(response) || "false".equalsIgnoreCase(response)) {
                ret = Boolean.getBoolean(response);
            } else {
                ret = response;
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ret);
        }
        return "";
    }

    public void errorResponseClass(Class errorResponseClass) {
        this.errorResponseClass = errorResponseClass;
    }

    @Override
    public R readErrorResponse(String errorResponse) throws Exception {
        return mapper.readValue(errorResponse, errorResponseClass);
    }
}
