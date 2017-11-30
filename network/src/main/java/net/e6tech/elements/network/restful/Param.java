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
package net.e6tech.elements.network.restful;

import net.e6tech.elements.common.util.SystemException;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by futeh.
 */
public class Param implements Serializable{
    private static final long serialVersionUID = 566698484644606125L;
    private String field;
    private String value;
    private String encodedValue;

    public Param(String field, String value) {
        this.field = field;
        this.value = value;
        try {
            if (value != null)
                this.encodedValue = URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new SystemException(e);
        }
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getEncodedValue() {
        return encodedValue;
    }

    public void setEncodedValue(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public String encode() {
        return field + "=" + encodedValue;
    }

    public String toString() {
        return field + "=" + value;
    }
}
