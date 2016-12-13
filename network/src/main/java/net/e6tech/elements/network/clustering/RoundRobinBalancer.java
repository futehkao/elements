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


package net.e6tech.elements.network.clustering;

import java.util.List;

/**
 * Created by futeh on 1/21/16.
 */
public class RoundRobinBalancer implements Balancer {

    String uuid;

    public ClusterService select(List<ClusterService> services) {
        if (services.size() == 0) return null;
        ClusterService service = null;
        if (uuid == null) {
            for (int i = 0; i < services.size(); i++ ) {
                ClusterService s = services.get(i);
                if (s.isHealthy()) service = s;
            }
        } else {
            for (int i = 0; i < services.size(); i++ ) {
                ClusterService s = services.get(i);
                if (uuid.equals(s.getMember().getUuid())) {
                    int idx = i + 1;
                    if (idx == services.size()) idx = 0;
                    s = services.get(idx);
                    while (!s.isHealthy()) {
                        idx ++;
                        if (idx == services.size()) idx = 0;
                        if (idx == i) { // we wrap around 
                            s = services.get(idx);
                            if (!s.isHealthy()) s = null;
                            break;
                        }
                        s = services.get(idx);
                    }
                    service = s;
                    break;
                }
            }
            if (service == null) {
                service = services.get(0);
            }
        }
        if (service != null) uuid = service.getMember().getUuid();
        if (!service.isHealthy()) service = null;
        return service;
    }
}
