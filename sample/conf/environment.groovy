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
import  net.e6tech.elements.common.logging.Logger

dataSourceMaxPoolSize = 10
entityManagerTxTimeout = 5000L
entityManagerLongTransaction = 5000L
entityManagerMonitorTransaction = false
hibernateShowSQL = false
hibernateGenerateStatistics = false
hibernateCacheUseSecondLevelCache = true
clusterName = 'h3_cluster'
clusterHost = '127.0.0.1'
clusterPort = 2552
clusterSeeds = "[\"akka://${clusterName}@${clusterHost}:${clusterPort}\"]"
logConfigFile = "$__dir/log4j2.yaml"

// setting System properties
systemProperties {
    'jmx.mxbean.multiname'          'true'
    'hibernate.show_sql'            "${hibernateShowSQL}"
    'hibernate.generate_statistics' "${hibernateGenerateStatistics}"
    'hibernate.cache.use_second_level_cache' "${hibernateCacheUseSecondLevelCache}"
    'log4j.configurationFile'       "${logConfigFile}"
    'log4j2.contextSelector'        "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
    "${Logger.logDir}"              "$__dir/../logs"                        // log4j output directory
    'isThreadContextMapInheritable' 'true'                                  // for log4j ThreadContext
    'java.util.logging.manager'     "net.e6tech.elements.common.logging.jul.LogManager"
    'org.apache.cxf.Logger'         'org.apache.cxf.common.logging.Slf4jLogger'
}