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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;

public class HashedFileState implements FileState {
    private final HashCode hashCode;
    private final long timestamp;

    public HashedFileState(long timestamp, HashCode hashCode) {
        this.timestamp = timestamp;
        this.hashCode = hashCode;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileState previousState) {
        if (!(previousState instanceof HashedFileState)) {
            return false;
        }
        HashedFileState previous = (HashedFileState) previousState;
        return timestamp == previous.timestamp
            && isContentUpToDateInternal(previous);
    }

    @Override
    public boolean isContentUpToDate(FileState previousState) {
        if (!(previousState instanceof HashedFileState)) {
            return false;
        }
        HashedFileState previous = (HashedFileState) previousState;
        return isContentUpToDateInternal(previous);
    }

    private boolean isContentUpToDateInternal(HashedFileState previous) {
        return hashCode.equals(previous.hashCode);
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        keyBuilder.put(hashCode.asBytes());
    }

    public HashCode getHashCode() {
        return hashCode;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
