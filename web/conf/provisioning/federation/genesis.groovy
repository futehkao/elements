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
import net.e6tech.elements.web.federation.GenesisImpl;

resourceManager.setupThreadPool()

atom('genesis') {
    configuration = """
        _genesis.registrations:
            - qualifier: x
              implementation: ^_x
            - qualifier: y
              interfaceClass: net.e6tech.elements.web.federation.GenesisTest\$XImpl
        _genesis.cluster.hostAddress: http://127.0.0.1:3939/restful
    """
    _x = net.e6tech.elements.web.federation.GenesisTest.XImpl
    _genesis = GenesisImpl
    _x.collective = _genesis.cluster
}