/*
Copyright 2015-2019 Futeh Kao

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
package net.e6tech.elements.rules.dataset;

import java.math.BigDecimal;

/**
 * Created by futeh.
 */
@SuppressWarnings("unchecked")
public abstract class Calculate {

    Class dataType;
    long initLong = 0;
    double initDouble = 0;
    BigDecimal initBigDecimal = BigDecimal.ZERO;

    public Calculate(Class dataType) {
        this.dataType = dataType;
    }

    public Calculate(Class dataType, long initLong, double initDouble, BigDecimal initBigDecimal) {
        this.dataType = dataType;
        this.initLong = initLong;
        this.initDouble = initDouble;
        this.initBigDecimal = initBigDecimal;
    }

    abstract long calculateLong(long current, long value);
    abstract double calculateDouble(double current, double value);
    abstract BigDecimal calculateBigDecimal(BigDecimal current, BigDecimal value);

    @SuppressWarnings({"squid:S3776", "squid:MethodCyclomaticComplexity"})
    public BigDecimal calculate(Iterable iterable) {
        BigDecimal result = null;
        if (dataType.isAssignableFrom(Integer.class)
                || dataType.isAssignableFrom(Long.class)
                || dataType.isAssignableFrom(Short.class)
                || dataType.isAssignableFrom(Byte.class)) {
            long value = initLong;
            for (Object obj : iterable) {
                Number number = (Number) obj;
                if (number != null)
                    value = calculateLong(value, number.longValue());
            }
            result = new BigDecimal(value);
        } else if (dataType.isAssignableFrom(Float.class)
                || dataType.isAssignableFrom(Double.class)) {
            double value = initDouble;
            for (Object obj : iterable) {
                Number number = (Number) obj;
                if (number != null)
                    value = calculateDouble(value, number.doubleValue());
            }
            result = BigDecimal.valueOf(value);
        } else if (dataType.isAssignableFrom(BigDecimal.class)) {
            BigDecimal value = initBigDecimal;
            for (Object obj : iterable) {
                BigDecimal number = (BigDecimal) obj;
                if (number != null)
                    value = calculateBigDecimal(value, number);
            }
            result = value;
        }
        return result;
    }
}
