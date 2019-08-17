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

package net.e6tech.elements.cassandra.etl;

import java.io.Serializable;

public class ETLSettings implements Serializable {

    private static final long serialVersionUID = 2451748267020918041L;
    private Integer batchSize;
    private Long timeLag;
    private Boolean extractAll;
    private Long startTime;

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public ETLSettings batchSize(Integer batchSize) {
        setBatchSize(batchSize);
        return this;
    }

    public Long getTimeLag() {
        return timeLag;
    }

    public void setTimeLag(Long timeLag) {
        this.timeLag = timeLag;
    }

    public ETLSettings timeLag(Long timeLag) {
        setTimeLag(timeLag);
        return this;
    }

    public Boolean getExtractAll() {
        return extractAll;
    }

    public void setExtractAll(Boolean extractAll) {
        this.extractAll = extractAll;
    }

    public ETLSettings extractAll(Boolean extractAll) {
        setExtractAll(extractAll);
        return this;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public ETLSettings startTime(Long startTime) {
        setStartTime(startTime);
        return this;
    }
}
