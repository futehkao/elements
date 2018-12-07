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

package  net.e6tech.sample.web.cxf

import net.e6tech.elements.web.cxf.JaxRSServer
import net.e6tech.elements.web.cxf.SecurityAnnotationEngine
import net.e6tech.sample.web.cxf.HelloWorld
import net.e6tech.sample.web.cxf.HelloWorldRoles
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

roleMap = [(HelloWorld.getName()): HelloWorldRoles]

atom("helloWorld") {
    configuration =  """
    _prototype.extraMessage: '...What a sunny day!'
    _helloWorld.addresses:
        - "http://0.0.0.0:19001/restful/"
    _helloWorld.resources:
        - class: "net.e6tech.sample.web.cxf.HelloWorld"
          singleton: false
          prototype: ^_prototype
        - class: "net.e6tech.sample.web.cxf.HelloWorld2"
          classLoader: ^_classLoader
    _helloWorld.responseHeaders:
        'X' : 'X val'
        'Y' : 'Y val'
    _securityAnnotation.securityProviders: ^roleMap
 """
    _classLoader = HelloWorld2.class.getClassLoader()
    _prototype = HelloWorld
    _securityAnnotation = SecurityAnnotationEngine
    _helloWorld = JaxRSServer
}

@Path("/helloworld")
class HelloWorld2 {
    @GET
    @Produces("application/json")
    @Path("hello2")
    String ping() {
        return "ping ...";
    }
}
