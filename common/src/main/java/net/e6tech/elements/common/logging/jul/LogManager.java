/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.common.logging.jul;

import java.util.logging.Logger;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S2176")
public class LogManager extends java.util.logging.LogManager {

    public LogManager() {
        //
    }

    @Override
    public boolean addLogger(final Logger logger) {
        // in order to prevent non-bridged loggers from being registered, we always return false to indicate that
        // the named logger should be obtained through getLogger(name)
        return false;
    }

    @Override
    public Logger getLogger(final String name) {
        return new LoggerAdapter(name);
    }
}
