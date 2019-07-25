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

package net.e6tech.sample.prototype;

import net.e6tech.elements.common.resources.Initializable;
import net.e6tech.elements.common.resources.Resources;
import net.e6tech.elements.common.resources.Startable;

/**
 * Created by futeh.
 */
public class Dependent implements Initializable, Startable {

    private String name;
    private String description;
    private String other;
    private int preInit;
    private int postInit;
    private int after;
    private int initialized = 0;
    private int started = 0;
    private int launched = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOther() {
        return other;
    }

    public void setOther(String other) {
        this.other = other;
    }

    public int getPreInit() {
        return preInit;
    }

    public void setPreInit(int preInit) {
        this.preInit = preInit;
    }

    public int getPostInit() {
        return postInit;
    }

    public void setPostInit(int postInit) {
        this.postInit = postInit;
    }

    public int getInitialized() {
        return initialized;
    }

    public void setInitialized(int initialized) {
        this.initialized = initialized;
    }

    public int getStarted() {
        return started;
    }

    public void setStarted(int started) {
        this.started = started;
    }

    public int getAfter() {
        return after;
    }

    public void setAfter(int after) {
        this.after = after;
    }

    @Override
    public void initialize(Resources resources) {
        initialized ++;
    }

    @Override
    public void start() {
        started ++;
    }

    public int getLaunched() {
        return launched;
    }

    public void setLaunched(int launched) {
        this.launched = launched;
    }
}
