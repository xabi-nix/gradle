/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.cache;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.gradle.internal.Cast;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DiffUtil {
    public interface Listener<K> {
        boolean added(K key);
        boolean removed(K key);
        boolean changed(K key);
    }

    public interface OrderingAwareListener<K> extends Listener<K> {
        boolean reordered(K key);
    }

    public static <K> boolean diff(Map<? extends K, ?> a, Map<? extends K, ?> b, Listener<? super K> listener) {
        return findAdditionsRemovalsAndChanges(a, b, listener, new MutableBoolean(false));
    }

    public static <K> boolean diffUnordered(Map<? extends K, ?> a, Map<? extends K, ?> b, OrderingAwareListener<? super K> listener) {
        MutableBoolean foundChanges = new MutableBoolean(false);
        if (!findAdditionsRemovalsAndChanges(a, b, listener, foundChanges)) {
            return false;
        }
        if (!foundChanges.booleanValue()) {
            Iterator<? extends K> aIterator = a.keySet().iterator();
            Iterator<? extends K> bIterator = b.keySet().iterator();
            // Size must match as there are no added or removed elements
            while (aIterator.hasNext()) {
                K aKey = aIterator.next();
                K bKey = bIterator.next();
                if (!aKey.equals(bKey)) {
                    return listener.reordered(aKey);
                }
            }
        }
        return true;
    }

    private static <K> boolean findAdditionsRemovalsAndChanges(Map<? extends K, ?> a, Map<? extends K, ?> b, Listener<? super K> listener, MutableBoolean foundChanges) {
        LinkedHashSet<K> aKeys = Sets.newLinkedHashSet(a.keySet());
        if (findAdditionsAndRemovals(aKeys, b.keySet(), listener, foundChanges)) {
            return false;
        }

        aKeys.retainAll(b.keySet());
        for (K key : aKeys) {
            if (!Objects.equal(b.get(key), a.get(key))) {
                foundChanges.setValue(true);
                if (!listener.changed(key)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static <K> boolean findAdditionsAndRemovals(LinkedHashSet<? extends K> aKeys, Set<? extends K> bKeys, Listener<? super K> listener, MutableBoolean foundChanges) {
        Set<K> added = Cast.uncheckedCast(aKeys.clone());
        added.removeAll(bKeys);
        if (!added.isEmpty()) {
            foundChanges.setValue(true);
        }
        for (K key : added) {
            if (!listener.added(key)) {
                return false;
            }
        }

        Set<K> removed = Cast.uncheckedCast(aKeys.clone());
        removed.removeAll(bKeys);
        if (!removed.isEmpty()) {
            foundChanges.setValue(true);
        }
        for (K key : removed) {
            if (!listener.removed(key)) {
                return false;
            }
        }

        return true;
    }
}
