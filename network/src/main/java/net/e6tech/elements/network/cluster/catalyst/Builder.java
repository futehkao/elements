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

package net.e6tech.elements.network.cluster.catalyst;

import net.e6tech.elements.network.cluster.catalyst.dataset.CollectionDataSet;
import net.e6tech.elements.network.cluster.catalyst.dataset.DataSet;
import net.e6tech.elements.network.cluster.catalyst.transform.Transform;

public class Builder<Re extends Reactor, T, R> {
    private Catalyst<Re> catalyst;
    private DataSet<T> dataSet;
    private Series<Re, T, R> series = new Series<>();

    public Builder(Catalyst<Re> catalyst, DataSet<T> dataSet) {
        this.catalyst = catalyst;
        this.dataSet = dataSet;
    }

    public Builder(Catalyst<Re> catalyst, Series<Re, T, T> series, DataSet<T> dataSet) {
        this.catalyst = catalyst;
        this.series = (Series) series;
        this.dataSet = dataSet;
    }

    public <U> Builder<Re, T, U> add(Transform<Re, R, U> transform) {
        series = (Series) series.add(transform);
        return (Builder) this;
    }

    public DataSet<R> transform() {
        return new CollectionDataSet(catalyst.transform(series, dataSet));
    }

    public R scalar(Scalar<Re, T, R> scalar) {
        scalar.setSeries(series);
        return catalyst.scalar(scalar, dataSet);
    }
}