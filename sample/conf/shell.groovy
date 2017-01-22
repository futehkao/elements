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

import  net.e6tech.elements.network.shell.Shelld

atom("shell") {
    configuration = """
_shell:
    fileRootDir:  ${__dir}
    cmdMountPointConfig: "file:commands"
    confMountPointConfig: "file:"
    sshPort: 2000
    properties:
        crash.auth: sshkey,password
        crash.ssh.auth_timeout: 300000
        crash.ssh.idle_timeout: 300000
        crash.auth.sshkey.path: ${->System.getProperty("user.home")}/.ssh
        crash.auth.password.file: ${->System.getProperty("user.home")}/.crash/passwd
    telnetPort: 2100
    telnetBindAddress: localhost
"""
    _shell = Shelld
    _shell.addAttribute('resourceManager', resourceManager)
    postInit { _shell.start() }
}