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

import net.e6tech.elements.jobs.JobServer


// setup boot profile: directory, init, main components, after etc.
// For main components, only those listed in the boot components are executed.
bootstrap.with {
    dir = "$__dir"
    init = ["$__dir/../bootstrap/boot_init.groovy",
            { println resourceManager }]
    preBoot = [ defaultThreadPool: { setupThreadPool('DefaultThreadPool') } ]
    main = [
            { variables && cluster }: {
                println "booting variables and cluster"
            },
            variables: "$__dir/../../environment.groovy",
            cluster: "$__dir/../../cluster.groovy"
    ]
    after = [{ jobs || jobServer }: {
                registerBean('jobServer', JobServer)
             },
             jobServer: [],
             jobs: "$__dir/../../jobs/**",
             {true}: "$__dir/../bootstrap/boot_final.groovy"]
    defaultEnvironmentFile = "$__dir/../../environment.groovy"
    // defaultSystemProperties = ...
}

// see below .preBoot and .postBoot
// bootDisableList = ['cluster']
// preBoot = [ hello: { println 'hello world'}, variables: true ]
// postBoot = [{ println 'boot completed!'}]

// booting up
// the first parameter is the boot script to set up bootstrap profile.  Since the profile set up is in this script, we
// pass null to skip this part.
// the following string parameters are use to indicated which main components should be started.  The main boot order is determined
// by the profile.
// If the parameter is a map, its components are added to the boot after map.
bootstrap
        .disable('cluster')
        .preBoot([ hello: { println 'hello world'},   // since value is a closure, simply runs closure
                   variables: true ]) // set variables component to true.  This has the effect of turning main component named variables.
        .preBoot({ println 'hello world'}) // runs an anonymous block
        .postBoot([{ println 'boot completed!'}])
        .boot(null, 'cluster', 'trivial', 'jobs')
        .after([persist: "$__dir/../../persist.groovy",
            notification: "$__dir/../../notification.groovy",
            concrete: "$__dir/../../prototype/concrete.groovy",
            restful: "$__dir/../../restful/**"])

// The boot order is
// exec boot script
// boot env
// boot provision
// boot init
// pre boot
// boot main
// post boot
// boot after
