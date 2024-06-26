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

package net.e6tech.elements.web.federation;

import net.e6tech.elements.common.federation.Federation;

import javax.annotation.Nonnull;

public class FederationImpl extends CollectiveImpl implements Federation {

    private ClusterImpl cluster;

    public FederationImpl() {
    }

    public FederationImpl(ClusterImpl cluster) {
        this.cluster = cluster;
    }

    public ClusterImpl getCluster() {
        return cluster;
    }

    public void setCluster(ClusterImpl cluster) {
        this.cluster = cluster;
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public Type getType() {
        return Type.FEDERATION;
    }

    @Override
    public void onEvent(@Nonnull Event event) {
        beacon.onEvent(event);
        if (cluster != null) {
            cluster.beacon.onEvent(event);
        }
    }

    public void onAnnounced(@Nonnull Event event) {
        if (cluster != null) {
            cluster.beacon.onEvent(event);
        }
    }
}
