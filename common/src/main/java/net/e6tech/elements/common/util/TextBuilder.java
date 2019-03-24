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

import java.util.Map;

public class TextBuilder {

    private TextSubstitution textSubstitution;

    public TextBuilder(String template) {
        textSubstitution = new TextSubstitution(template);
    }

    public static TextBuilder using(String template) {
        return new TextBuilder(template);
    }

    public String build(Map<String, ?> map) {
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1) {
        Map<String, Object> map = MapBuilder.of(k1, v1);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9, String k10, Object v10) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14, String k15, Object v15) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14, k15, v15);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19, String k20, Object v20) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19, k20, v20);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19,
                                      String k20, Object v20, String k21, Object v21) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19,
                                      String k20, Object v20, String k21, Object v21, String k22, Object v22) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19,
                                      String k20, Object v20, String k21, Object v21, String k22, Object v22, String k23, Object v23) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22, k23, v23);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19,
                                      String k20, Object v20, String k21, Object v21, String k22, Object v22, String k23, Object v23, String k24, Object v24) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22, k23, v23, k24, v24);
        return textSubstitution.build(map);
    }

    public String build(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4,
                                      String k5, Object v5, String k6, Object v6, String k7, Object v7, String k8, Object v8, String k9, Object v9,
                                      String k10, Object v10, String k11, Object v11, String k12, Object v12, String k13, Object v13, String k14, Object v14,
                                      String k15, Object v15, String k16, Object v16, String k17, Object v17, String k18, Object v18, String k19, Object v19,
                                      String k20, Object v20, String k21, Object v21, String k22, Object v22, String k23, Object v23, String k24, Object v24, String k25, Object v25) {
        Map<String, Object> map = MapBuilder.of(k1, v1, k2, v2, k3, v3, k4, v4,
                k5, v5, k6, v6, k7, v7, k8, v8, k9, v9,
                k10, v10, k11, v11, k12, v12, k13, v13, k14, v14,
                k15, v15, k16, v16, k17, v17, k18, v18, k19, v19,
                k20, v20, k21, v21, k22, v22, k23, v23, k24, v24, k25, v25);
        return textSubstitution.build(map);
    }
}
