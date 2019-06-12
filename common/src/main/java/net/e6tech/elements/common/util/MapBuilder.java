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

package net.e6tech.elements.common.util;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("squid:S00107")
public class MapBuilder<K,V> {

    private Map<K, V> map = new HashMap<>();

    public static <K, V> MapBuilder<K, V> builder() {
        return new MapBuilder<>();
    }

    public static <K, V> Map<K, V> of(K key, V value) {
        Map<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        Map<K, V> map = of(k2, v2);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = of(k2, v2, k3, v3);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4, k5, v5);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14, K k15, V v15) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14, k15, v15);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19, K k20, V v20) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19, k20, v20);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19,
                                      K k20, V v20, K k21, V v21) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19,
                                      K k20, V v20, K k21, V v21, K k22, V v22) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19,
                                      K k20, V v20, K k21, V v21, K k22, V v22, K k23, V v23) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22, k23, v23);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19,
                                      K k20, V v20, K k21, V v21, K k22, V v22, K k23, V v23, K k24, V v24) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22, k23, v23, k24, v24);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4,
                                      K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9,
                                      K k10, V v10, K k11, V v11, K k12, V v12, K k13, V v13, K k14, V v14,
                                      K k15, V v15, K k16, V v16, K k17, V v17, K k18, V v18, K k19, V v19,
                                      K k20, V v20, K k21, V v21, K k22, V v22, K k23, V v23, K k24, V v24, K k25, V v25) {
        Map<K, V> map = of(k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22, k23, v23, k24, v24, k25, v25);
        map.put(k1, v1);
        return map;
    }

    public MapBuilder<K, V> put(K key, V value) {
        map.put(key, value);
        return this;
    }

    public Map<K, V> build() {
        return map;
    }
}
