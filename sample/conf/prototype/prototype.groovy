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


import net.e6tech.sample.prototype.*

prototype("prototype")
        .settings([ _name : 'default', _description : 'default', _other : 'default'])
        .build {
    configuration = """
    _dependent:
        name: ${_name}
        description: ${_description}
        other: ${_other}
"""
    _dependent = Dependent

    preInit {
        print "${clusterHost}"  // see if we can access global var
        _dependent.preInit = _dependent.preInit + 1
    }

    postInit {
        _dependent.postInit = _dependent.postInit + 1
    }

    after {
        _dependent.after = _dependent.after + 1
    }

    waitFor.concrete_owner { c ->
        c.waitFor = "${_name}"
    }

    launched {
        _dependent.launched = _dependent.launched + 1
    }
}