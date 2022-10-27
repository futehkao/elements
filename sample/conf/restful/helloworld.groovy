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
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean

helloWorldPort = 19001
roleMap = [(HelloWorld.getName()): HelloWorldRoles]

atom("helloWorld") {
    configuration =  """
    _prototype.extraMessage: '...What a sunny day!'
    _helloWorld:
        testClass: net.e6tech.elements.web.cxf.JaxRSServer
    _helloWorld.addresses:
        - "http://0.0.0.0:${helloWorldPort}/restful/"
    _helloWorld.resources:
        - class: "net.e6tech.sample.web.cxf.HelloWorld"
          singleton: false
          prototype: ^_prototype
          classLoader: ^_classLoader
    _helloWorld.responseHeaders:
        'X' : 'X val'
        'Y' : 'Y val'
    # _helloWorld.serverEngineClass: net.e6tech.elements.web.cxf.tomcat.TomcatEngine
    _securityAnnotation.securityProviders: ^roleMap
 """
    _classLoader = this.getClass().getClassLoader()
    _prototype = HelloWorld
    _securityAnnotation = SecurityAnnotationEngine
    _helloWorld = JaxRSServer
    _helloWorld.with {
        addresses = ['' + "http://0.0.0.0:${helloWorldPort}/restful/"]
        prototype = _prototype
        singleton = false
    }
    _helloWorld.customizer = (JAXRSServerFactoryBean b) -> {
        b.getInInterceptors()
    }
}

