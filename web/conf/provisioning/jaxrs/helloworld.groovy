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
import net.e6tech.elements.network.cluster.ClusterNode
import net.e6tech.elements.common.actor.Genesis

/*
atom("cluster") {

    configuration = """
        genesis:
            name: hello
            workPoolConfig:
                initialCapacity: 10
           
"""
    genesis = Genesis
    genesis.configuration = """
akka.cluster.seed-nodes = ["akka://hello@127.0.0.1:2552"]
akka.remote.artery.canonical.port = 2552
akka.remote.artery.canonical.hostname = 127.0.0.1
"""
    myCluster = ClusterNode
    postInit {
        notificationCenter.addBroadcast(myCluster.broadcast)
    }
}

 */

atom("serverEngine") {
    configuration = """
    engine:
        maxThread: 200
        baseDir: $__dir/../../../web/tomcat
"""
    engine = serverEngineClass
    rebind engine
}

atom("helloworld") {
    configuration =  """
    _helloworld.addresses:
        - "http://0.0.0.0:9000/restful/"
        - "http://0.0.0.0:9001/restful/"
    _helloworld.resources:
        - class: "net.e6tech.elements.web.cxf.HelloWorldRS"
          bindHeaderObserver: false
 """
    _helloworld = JaxRSServer
}


atom("helloworld1") {
    configuration =  """
    _helloworld.addresses:
        - "http://0.0.0.0:9002/restful/"
    _helloworld.resources:
        - class: "net.e6tech.elements.web.cxf.HelloWorldRS"
          bindHeaderObserver: false
 """
    _helloworld = JaxRSServer
}

// share same port as helloworld but add another service HelloWorldRS2
atom("helloworld2") {
    configuration =  """
    _helloworld.addresses:
        - "http://0.0.0.0:9000/restful/"
    _helloworld.resources:
        - class: "net.e6tech.elements.web.cxf.HelloWorldRS2"
          bindHeaderObserver: false
 """
    _helloworld = JaxRSServer
}