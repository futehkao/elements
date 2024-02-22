/*
 * Copyright 2015-2020 Futeh Kao
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

class PostData {
    private Object data;
    private boolean specified;
    private RequestEncoder encoder;

    public PostData() {
    }

    public PostData(Object data) {
        this.data = data;
        this.specified = true;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
        this.specified = true;
    }

    public boolean isSpecified() {
        return specified;
    }

    public void setSpecified(boolean specified) {
        this.specified = specified;
    }

    public RequestEncoder getEncoder() {
        return encoder;
    }

    public void setEncoder(RequestEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     *
     * @param encoder external encoder.  If PostData's encoder is null, the external encoder will be used.
     * @return
     * @throws Exception general exception if there is encoding issue.  The exact exception depends oon the actual implementation
     * of the encoding.
     */
    public String encode(RequestEncoder encoder) throws Exception {
        if (this.encoder != null)
            return this.encoder.encodeRequest(getData());
        return encoder.encodeRequest(getData());
    }
}
