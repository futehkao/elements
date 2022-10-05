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

import net.e6tech.elements.common.util.SystemException;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URL;

public class Cluster extends Collective {

    Federation federation = new Federation(this);

    public Federation getFederation() {
        return federation;
    }

    public void setFederation(Federation federation) {
        this.federation = federation;
        if (federation != null)
            federation.setCluster(this);
    }

    public void setHostAddress(String address) {
        super.setHostAddress(address);
        URL url;
        try {
            url = new URL(address);
        } catch (MalformedURLException e) {
            throw new SystemException(e);
        }
        addHostedMember(url.getHost() + ":" + url.getPort());
    }

    @Override
    public void start() {
        if (getHostAddress() != null) {
            if (federation != null)
                federation.setCluster(this);
            super.start();
        } else {
             if (federation != null)
                 federation.setCluster(null);
        }

        if (federation != null && federation.getHostAddress() != null) {
            provision.inject(federation);
            federation.start();
        }
    }

    @Override
    public Type getType() {
        return Type.CLUSTER;
    }

    public void shutdown() {
        super.shutdown();
        if (federation != null)
            federation.shutdown();
    }

    @Override
    public void onEvent(@Nonnull Event event) {
        beacon.onEvent(event); // this will send to other cluster nodes
        if (federation != null && event.getCollectiveType() == Type.FEDERATION) // inform federation,
            federation.beacon.onEvent(event);  // shouldn't call federation.onEvent to prevent infinite recursion because
                                               // the federation will call cluster.onEvent.
    }
}
