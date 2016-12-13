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
public class LoadBalancer implements Balancer {
    @Override
    public ClusterService select(List<ClusterService> services) {
        ClusterService clusterService = null;
        for (ClusterService s : services) {
            if (clusterService == null) {
                if (s.isHealthy()) {
                    clusterService = s;
                }
            } else if (s.isHealthy()) {
                if (s.getMeasurement().getCount() < clusterService.getMeasurement().getCount()) {
                    clusterService = s;
                } else if (s.getMeasurement().getCount() < clusterService.getMeasurement().getCount() &&
                        s.getMeasurement().getAverage() < clusterService.getMeasurement().getAverage()) {
                    clusterService = s;
                }
            }
        }
        return clusterService;
    }
}
