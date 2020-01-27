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

import net.e6tech.elements.cassandra.driver.v4.SessionProviderV4
import net.e6tech.elements.cassandra.Schema

import java.util.function.Consumer

atom("cassandra_session") {
    configuration = """
    _provider:
      v3Annotation: false
      host: "${cassandraHost}"
      port: "${cassandraPort}"
      keyspace: ${cassandraKeyspace}
      coreConnections: ${cassandraCoreConnections}
      maxConnections: ${cassandraMaxConnections}
      maxRequests: ${cassandraMaxRequests}
      sharedSession: true
      # builderOptions: ^_options
      lastUpdateClass: net.e6tech.elements.cassandra.etl.LastUpdate
      createKeyspaceArguments:
        replication: 1
      driverOptions:
        REQUEST_TIMEOUT: '10 seconds'
        CONNECTION_MAX_REQUESTS: '32768'
        SOCKET_KEEP_ALIVE: 'true'
        LOAD_BALANCING_LOCAL_DATACENTER: 'datacenter1'
"""
    _options = { t ->   } as Consumer
    _provider = SessionProviderV4
    _schema = Schema

    postInit {
        _schema.createTables('elements', cassandraTables.toArray(new String[0]))
    }
}