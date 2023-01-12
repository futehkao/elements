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
    private Integer maxTimeUnitSteps = 5000;  // used by PartitionStrategy to incrementally extract and transmutate date
    private Long timeLag;
    private Long maxPast = 2 * ETLContext.YEAR;  // in case of no last update, this sets how far in the past to extract data.
    private Boolean extractAll;
    private Long startTime;
    private Integer retries = 5;
    private Long retrySleep = 100L;

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

    public Integer getMaxTimeUnitSteps() {
        return maxTimeUnitSteps;
    }

    public void setMaxTimeUnitSteps(Integer maxTimeUnitSteps) {
        this.maxTimeUnitSteps = maxTimeUnitSteps;
    }

    public ETLSettings maxTimeUnitSteps(Integer maxTimeUnitSteps) {
        setMaxTimeUnitSteps(maxTimeUnitSteps);
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

    public Long getMaxPast() {
        return maxPast;
    }

    public void setMaxPast(Long maxPast) {
        this.maxPast = maxPast;
    }

    public ETLSettings maxPast(Long maxPast) {
        setMaxPast(maxPast);
        return this;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public ETLSettings retries(Integer retries) {
        setRetries(retries);
        return this;
    }

    public Long getRetrySleep() {
        return retrySleep;
    }

    public void setRetrySleep(Long retrySleep) {
        this.retrySleep = retrySleep;
    }

    public ETLSettings retrySleep(Long sleep) {
        setRetrySleep(sleep);
        return this;
    }
}
