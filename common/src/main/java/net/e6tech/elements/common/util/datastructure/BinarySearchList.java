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
package net.e6tech.elements.common.util.datastructure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * BinarySearchList supports binary search within a list.
 * Created by futeh.
 */
@SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134"})
public class BinarySearchList<T extends Comparable> implements Iterable<T> {

    private List<T> sortedList = new ArrayList<>(); // Needs ArrayList for it O(1) get

    public Iterator<T> iterator() {
        return sortedList.iterator();
    }

    public int size() {
        return sortedList.size();
    }

    public boolean add(T cmp) {
        privateAdd(0, sortedList.size() - 1, cmp);
        return true;
    }

    public void check() {
        Comparable prev = null;
        for (Comparable c : sortedList) {
            if (prev == null) {
                prev = c;
            }
            if (prev.compareTo(c) > 0) {
                throw new IllegalStateException();
            }
        }
    }

    public T get(int i) {
        return sortedList.get(i);
    }

    // binary search insert.
    private void privateAdd(int min, int max, T cmp) {
        if (size() == 0) {
            sortedList.add(cmp);
            return;
        }

        if (min == max) {
            Comparable candidate = get(min);
            int order = candidate.compareTo(cmp);
            if (order == 0 || order > 0) {
                sortedList.add(min, cmp);
            } else if (candidate.compareTo(cmp) < 0) { // next candiate needs to be of higher index
                sortedList.add(min + 1, cmp);
            }
            return;
        }

        int index = (max + min + 1) / 2;
        Comparable candidate = get(index);
        if (candidate.compareTo(cmp) == 0) {
            sortedList.add(index, cmp);
        } else if (candidate.compareTo(cmp) > 0) { // next candidate needs to be of lower index
            privateAdd(min, (max + min) / 2, cmp); // because (max + min + 1)/2 is biased toward upper value which may end up
                                             // being equal to max
        } else if (candidate.compareTo(cmp) < 0) { // next candidate needs to be of higher index
            privateAdd(index, max, cmp);
        }
    }

    public boolean removeAll(T cmp) {
        return _remove(0, sortedList.size() - 1, cmp, false);
    }

    public boolean removeFirst(T cmp) {
        return _remove(0, sortedList.size() - 1, cmp, true);
    }

    @SuppressWarnings({"squid:S3776", "squid:ForLoopCounterChangedCheck", "squid:S00100"})
    private boolean _remove(int min, int max, T cmp, boolean onlyOne) {
        if (size() == 0) {
            return false;
        }

        if (min == max) {
            Comparable candidate = get(min);
            int order = candidate.compareTo(cmp);
            if (order == 0) {
                int modifiableSize = size();
                boolean found = false;
                for (int i = min; i < modifiableSize; i++) {
                    Comparable right = get(i);
                    if (right.compareTo(cmp) == 0) {
                        if (right.equals(cmp)) {
                            sortedList.remove(i);
                            i --;
                            modifiableSize --;
                            found = true;
                            if (onlyOne)
                                return true;
                        }
                    } else {
                        break;
                    }
                }
                for (int i = min - 1; i >= 0; i--) {
                    Comparable left = get(i);
                    if (left.compareTo(cmp) == 0) {
                        if (left.equals(cmp)) {
                            sortedList.remove(i);
                            found = true;
                            if (onlyOne)
                                return true;
                        }
                    } else {
                        break;
                    }
                }
                return found;
            }
            return false;
        }

        int index = (max + min + 1) / 2;
        Comparable candidate = get(index);
        if (candidate.compareTo(cmp) == 0) {
            return _remove(index, index, cmp, onlyOne);
        } else if (candidate.compareTo(cmp) > 0) { // next candidate needs to be of lower index
            return _remove(min, (max + min) / 2, cmp, onlyOne);
        } else if (candidate.compareTo(cmp) < 0) { // next candidate needs to be of higher index
            return _remove(index, max, cmp, onlyOne);
        }
        return false;
    }
}
