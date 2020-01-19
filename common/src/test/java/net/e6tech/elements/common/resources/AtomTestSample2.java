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

package net.e6tech.elements.common.resources;

import net.e6tech.elements.common.inject.Inject;

/**
 * Created by futeh.
 */
public class AtomTestSample2 {
    private String sampleName;
    private AtomTestSample sample;

    @Inject(type=AtomTestSample.class, property = "name")
    private String sampleName2;
    @Inject
    private AtomTestSample sample2;

    public String getSampleName() {
        return sampleName;
    }

    @Inject(type=AtomTestSample.class, property = "name")
    public void setSampleName(String sampleName) {
        this.sampleName = sampleName;
    }

    public AtomTestSample getSample() {
        return sample;
    }

    @Inject
    public void setSample(AtomTestSample sample) {
        this.sample = sample;
    }

    public String getSampleName2() {
        return sampleName2;
    }

    public void setSampleName2(String sampleName2) {
        this.sampleName2 = sampleName2;
    }

    public AtomTestSample getSample2() {
        return sample2;
    }

    public void setSample2(AtomTestSample sample2) {
        this.sample2 = sample2;
    }
}
