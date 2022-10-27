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
import net.e6tech.elements.network.cluster.ClusterNode
import net.e6tech.elements.common.actor.GenesisActor


atom("cluster") {

    configuration = """
        genesis:
            name: ${clusterName}
            workPoolConfig:
                initialCapacity: 10
           
"""
    genesis = GenesisActor
    genesis.configuration = """
akka.cluster.seed-nodes = ${clusterSeeds}
akka.remote.artery.canonical.port = ${clusterPort}
akka.remote.artery.canonical.hostname = ${clusterHost}
"""
    myCluster = ClusterNode
    postInit {
        notificationCenter.addBroadcast(myCluster.broadcast)
    }
}