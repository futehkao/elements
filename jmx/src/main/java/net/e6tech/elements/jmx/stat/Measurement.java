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

package net.e6tech.elements.jmx.stat;

import net.e6tech.elements.common.util.datastructure.BinarySearchList;

import java.io.Serializable;
import java.util.LinkedList;


/**
 * Created by futeh.
 */
public class Measurement implements Serializable, MeasurementMXBean {

    private static final long serialVersionUID = -5888966219198957050L;
    private String name;
    private String unit;
    private long count = 0;
    private long total = 0;
    private double average = 0.0;
    private double median = 0.0;
    private double sum =0.0;
    private double sum_x_2 = 0.0;  //i.e. sum of x^2, which is not sum^2!!!
    private double stdDev = 0.0;
    private long windowSize = 300000l;  // default is 5 minutes
    // private long windowSize = 10000l;  // 10 seconds
    private long lastUpdate = 0l;
    private long firstUpdate = 0l;
    private boolean dirty = false;
    private boolean enabled = true;
    protected transient LinkedList<DataPoint> sortedByTime = new LinkedList<>(); // sorted by timestamp
    protected transient LinkedList<Long> failures = new LinkedList<>();
    protected transient BinarySearchList sortedByValue = new BinarySearchList(); // sorted by value

    public Measurement() {}

    public Measurement(boolean enabled) {
        this.enabled = enabled;
    }

    public Measurement(String name, String unit, boolean enabled) {
        this.name = name;
        this.unit = unit;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public long getTotal() {
        return total;
    }

    public long getCount() {
        if (dirty) recalculate();
        return count;
    }

    public double getAverage() {
        if (dirty) recalculate();
        return average;
    }

    public double getMedian() {
        if (dirty) recalculate();
        return median;
    }

    public double getSum() {
        if (dirty) recalculate();
        return sum;
    }

    public double getStdDev() {
        if (dirty) recalculate();
        return stdDev;
    }

    public long getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(long windowSize) {
        this.windowSize = windowSize;
    }

    public long getFailureCount() {
        if (dirty) recalculate();
        return failures.size();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void fail() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        if (firstUpdate == 0L) firstUpdate = System.currentTimeMillis();
        failures.add(now);
        dirty = true;
        if (System.currentTimeMillis() - firstUpdate > windowSize) recalculate();
    }

    /**
     * Because dataPoints is transient, we have to record everything.
     * @param value a measurement
     */
    public synchronized void add(double value) {
        if (!isEnabled()) return;
        total ++;
        long now = System.currentTimeMillis();
        if (firstUpdate == 0L) {
            firstUpdate = System.currentTimeMillis();
        }
        add(new DataPoint(now, value));
    }

    public Measurement append(double value) {
        if (!isEnabled()) return this;
        add(value);
        return this;
    }

    protected synchronized void recalculate() {
        lastUpdate = System.currentTimeMillis();
        long expire = lastUpdate - windowSize;
        while (failures.size() > 0 && failures.getFirst() < expire) {
            failures.remove();
        }

        long firstFailure = 0l;
        if (failures.size() > 0) {
            firstFailure = failures.getFirst();
        }

        if (sortedByTime.size() == 0) {
            firstUpdate = firstFailure;
            return;
        }

        while (sortedByTime.size() > 0 && sortedByTime.getFirst().getTimestamp() < expire) {
            remove();
        }

        count = sortedByTime.size();

        // calculating average and median
        if (count == 0) {
            average = 0.0;
            median = 0.0;
        } else {
            average = sum / count;
            int index = (int) count / 2;
            if (count == 2 * index) {
                DataPoint dp1 = (DataPoint) sortedByValue.get(index);
                DataPoint dp2 = (DataPoint) sortedByValue.get(index - 1);
                median = (dp1.getValue() + dp2.getValue()) / 2f;
            } else {
                median = ((DataPoint) sortedByValue.get(index)).getValue();
            }
        }

        // Sample standard deviation
        if (count > 1) {
            double n_ave_2 = count * average * average;
            double var = (sum_x_2 - n_ave_2) / (double)(count - 1);
            stdDev = (double) Math.sqrt(var);
        } else {
            stdDev = 0.0;
        }

        long firstDataUpdate = 0l;
        if (sortedByTime.size() > 0) {
            firstDataUpdate = sortedByTime.getFirst().getTimestamp();
        }

        firstUpdate = 0L;
        if (firstFailure > 0) firstUpdate = firstFailure;
        if (firstDataUpdate > 0) firstUpdate = Math.min(firstUpdate, firstDataUpdate);

        dirty = false;
    }

    protected void add(DataPoint dp) {
        if (!isEnabled()) return;
        sortedByTime.add(dp);
        sortedByValue.add(dp);
        double value = dp.getValue();
        sum += value;
        sum_x_2 += (value * value);
        dirty = true;
        if (System.currentTimeMillis() - firstUpdate > windowSize) recalculate();
    }

    protected DataPoint remove() {
        if (sortedByTime.size() > 0) {
            DataPoint dp = sortedByTime.removeFirst();
            sortedByValue.remove(dp);
            double removed = dp.getValue();
            sum -= removed;
            sum_x_2 = sum_x_2 - (removed * removed);
            dirty = true;
            return dp;
        }
        return null;
    }

    public String toString() {
        if (dirty) recalculate();

        StringBuilder builder = new StringBuilder();
        builder.append("count=" + count + " ");
        builder.append("average=" + average + " ");
        builder.append("stddev=" + stdDev + " ");
        builder.append("failureCount=" + failures.size() + " ");
        builder.append("windowSize=" + windowSize + " ");
        return builder.toString();
    }
}
