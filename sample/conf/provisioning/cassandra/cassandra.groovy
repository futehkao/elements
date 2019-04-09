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

import net.e6tech.elements.cassandra.SessionProvider
import net.e6tech.elements.cassandra.Schema

atom("cassandra_session") {
    configuration = """
    _provider:
      host: "${cassandraHost}"
      port: "${cassandraPort}"
      keyspace: ${cassandraKeyspace}
      coreConnections: ${cassandraCoreConnections}
      maxConnections: ${cassandraMaxConnections}
      maxRequests: ${cassandraMaxRequests}
      lastUpdateClass: net.e6tech.elements.cassandra.etl.LastUpdate
"""
    _provider = SessionProvider
    _schema = Schema

    postInit {
        _schema.createKeyspace("${cassandraKeyspace}", 3)
        _schema.createTables(null, cassandraTables.toArray(new String[0]))
    }
}