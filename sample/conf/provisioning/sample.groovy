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

import net.e6tech.elements.jobs.JobServer


registerBean('jobServer', JobServer)

bootstrap.with {
    dir = "$__dir"
    initBoot = ["$__dir/boot_init.groovy"]
    main = [
            { variables && cluster }: {
                println "booting variables and cluster"
            },
            variables: "$__dir/../variables.groovy",
            cluster: "$__dir/../cluster.groovy"
    ]
    after = [{true}: "$__dir/boot_final.groovy"]
    // defaultEnvironmentFile = ...
    // defaultSystemProperties = ...
}

preBoot = [{ println 'hello world'}, 'variables']
postBoot = [{ println 'boot completed!'}]
boot('cluster', 'trivial')
exec "$__dir/../persist.groovy",
        "$__dir/../notification.groovy",
        "$__dir/../prototype/concrete.groovy",
        "$__dir/../restful/**"


