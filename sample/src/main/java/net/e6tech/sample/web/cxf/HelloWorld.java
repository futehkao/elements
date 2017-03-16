/*
 * Copyright 2016 Futeh Kao
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

package net.e6tech.sample.web.cxf;

import net.e6tech.elements.common.resources.ResourceManager;
import net.e6tech.elements.common.resources.Resources;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/helloworld")
public class HelloWorld{

    @Inject
    ResourceManager resourceManager;

    @Inject
    Resources resources;

    @PostConstruct
    public void postConstruct() {
        System.out.println("postConstruct");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("preDestroy");
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello")
    public String ping() {
        return "hello";
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello/{greeting}")
    public String sayHello(@PathParam("greeting") String greeting) {
        return "hello " + greeting;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello")
    public HelloData post(@Nonnull HelloData data) {
        if (data == null) throw new NullPointerException();
        return data;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello/badPost")
    public HelloData badPost(HelloData data) {
        throw new NullPointerException("test");
    }

}
