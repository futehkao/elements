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

import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.resources.InstanceNotFoundException;
import net.e6tech.elements.common.resources.Provision;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.persist.EntityManagerConfig;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.persistence.EntityManager;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/helloworld")
@SuppressWarnings("all") // it is a test case
public class HelloWorld {

    @Inject
    Provision provision;

    @Inject
    Resources resources;

    private String extraMessage;

    public String getExtraMessage() {
        return extraMessage;
    }

    public void setExtraMessage(String extraMessage) {
        this.extraMessage = extraMessage;
    }

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
    @EntityManagerConfig(disable = true)
    public String ping() {
        try {
            resources.getInstance(EntityManager.class);
        } catch (InstanceNotFoundException ex) {
            System.out.println("No transaction");
        }
        return "ping ...";
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello/withParam")
    @EntityManagerConfig(disable = true)
    public String withParam(@QueryParam("param") String param, String post) {
        String str = "hello " + param;
        if (extraMessage != null)
            str += " " + extraMessage;
        return str;
    }

    @PermitAll
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello/{greeting}")
    public String sayHello(@PathParam("greeting") String greeting) {
        return provision.open()
                .annotate(EntityManagerConfig.class, EntityManagerConfig::names, new String[] {"sample-rw"})
                .apply(EntityManager.class, Resources.class,  (em, res) -> {
                    EntityManager byName = res.getMapVariable(EntityManager.class).get("sample-rw");
                    assert em == byName;

                    String str = "hello " + greeting;
                    if (extraMessage != null)
                        str += " " + extraMessage;
                    return str;
                });
    }

    @DenyAll
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello/security/{greeting}")
    public String withSecurity(@PathParam("greeting") String greeting) {
        String str = "hello " + greeting;
        if (extraMessage != null)
            str += " " + extraMessage;
        return str;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("hello/2/{greeting}")
    public String sayHello2(@PathParam("greeting") String greeting, @QueryParam("hello2") @Nonnull String arg) {
        return "hello " + greeting + " " +  arg;
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
