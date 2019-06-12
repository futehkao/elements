/*
Copyright 2015-2019 Futeh Kao

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
package net.e6tech.elements.common.logging;

/**
 * Created by futeh.
 */
public enum LogLevel {
    OFF(0), FATAL(100), ERROR(200), WARN(300), INFO(400), DEBUG(500), TRACE(600), ALL(Integer.MAX_VALUE);
    private int level;

    private LogLevel(int value) {
        this.level = value;
    }

    public int getLevel() {
        return level;
    }
}
