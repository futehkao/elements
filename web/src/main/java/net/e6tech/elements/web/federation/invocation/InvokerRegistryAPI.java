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

package net.e6tech.elements.web.federation.invocation;

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.web.federation.SubZero;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@Path("/v1/invoker-registry")
public class InvokerRegistryAPI {

    @Inject
    private Provision provision;
    private InvokerRegistry registry;
    private SubZero subZero;

    public InvokerRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(InvokerRegistry registry) {
        this.registry = registry;
    }

    public SubZero getSubZero() {
        return subZero;
    }

    public void setSubZero(SubZero subZero) {
        this.subZero = subZero;
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("hosts")
    public Set<String> routes() {
        return registry.routes();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("invoke")
    public Response invoke(Request request) {
        if (!registry.routes().contains(request.path))
            return null;
        Response response = new Response();
        try {
             Object ret = registry.invoke(request.path, request.thaw(subZero));
             response.setFrozen(subZero.freeze(ret));
        } catch (Exception ex) {
            response.setException(ex);
        }
        return response;
    }

    public static class Request {
        private String path;
        private byte[] frozen;

        public Request() {
        }

        public Request(String path, Object[] arguments, SubZero subZero) {
            this.path = path;
            frozen = subZero.freeze(arguments);
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public byte[] getFrozen() {
            return frozen;
        }

        public void setFrozen(byte[] frozen) {
            this.frozen = frozen;
        }

        public Object[] thaw(SubZero subZero) {
            if (frozen != null) {
                return subZero.thaw(frozen);
            }
            return null;
        }
    }

    public static class Response {
        Exception exception;
        byte[] frozen;

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }

        public byte[] getFrozen() {
            return frozen;
        }

        public void setFrozen(byte[] frozen) {
            this.frozen = frozen;
        }
    }
}
