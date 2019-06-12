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
import net.e6tech.elements.common.resources.*
import static org.junit.jupiter.api.Assertions.assertTrue


// use by ProvisionTestCase

atom("sample") {
    configuration = """
    sample.name = sample
"""
    sample = AtomTestSample
}

// The atom below showcases how within an atom and external object can be
// used for injection
// _simple2 should be injected with simple from the atom above
atom("sample2") {
    _sample = sample
    _sample2 = AtomTestSample2

    // runs after script all instances are initialized
    postInit {
        assertTrue(_sample.name == 'sample')
    }
}