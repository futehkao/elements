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
import net.e6tech.elements.web.federation.*

atom("cluster") {

    configuration = """
        _cluster:
            hostAddress: "http://127.0.0.1:3909/restful"
            seeds: 
                - "http://127.0.0.1:3909/restful"
            federation:
                hostAddress: "http://127.0.0.1:3910/restful"
                hosts:
                    - memberId: "3910"
                seeds:
                    - "http://127.0.0.1:3910/restful"
        _cluster2:
            hostAddress: "http://127.0.0.1:3911/restful"
            seeds: 
                - "http://127.0.0.1:3909/restful"
            federation:
                hostAddress: "http://127.0.0.1:3912/restful"
                hosts:
                    - memberId: "3912"
                seeds:
                    - "http://127.0.0.1:3910/restful"
            
           
"""
    _cluster = Cluster
    _cluster2 = Cluster
    List<Cluster> clusters = new ArrayList<>()
    clusters.add(_cluster)
    clusters.add(_cluster2)
    resourceManager.addBean('clusters', clusters)
}