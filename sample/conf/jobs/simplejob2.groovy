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

import net.e6tech.sample.jobs.SimpleJob
import net.e6tech.elements.common.logging.Logger;

def logger = Logger.getLogger();

atom("simpleJob2") {
    configuration = """
    job2:
        targetMethod: 'run'
        cronExpression: '0 0/5 * * * ?'
"""
    logger.info("starting job $__file")
    job2 = jobServer.registerJob("simpleJob2", SimpleJob)
}