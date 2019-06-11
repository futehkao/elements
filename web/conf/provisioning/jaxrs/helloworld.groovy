/*
 * Copyright 2015-2019 Futeh Kao
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

import net.e6tech.elements.web.cxf.JaxRSServer


engine = loadClass(serverEngineClass).newInstance()

try {
    engine.maxThreads = 200
    engine.minSpareThreads = 20
    engine.baseDir = "$__dir/../../../web/tomcat"
} catch (Exception ex) {
    // ignore because Jetty Engine does have such attributes
}

atom("helloworld") {
    configuration =  """
    _helloworld.serverEngine: ^engine
    _helloworld.addresses:
        - "http://0.0.0.0:9000/restful/"
        - "http://0.0.0.0:9001/restful/"
    _helloworld.resources:
        - class: "net.e6tech.elements.web.cxf.HelloWorldRS"
          bindHeaderObserver: false
 """
    _helloworld = JaxRSServer
}

atom("helloworld2") {
    configuration =  """
    _helloworld.serverEngine: ^engine
    _helloworld.addresses:
        - "http://0.0.0.0:9002/restful/"
    _helloworld.resources:
        - class: "net.e6tech.elements.web.cxf.HelloWorldRS"
          bindHeaderObserver: false
 """
    _helloworld = JaxRSServer
}