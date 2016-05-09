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

import com.google.common.base.Charsets;
import org.gradle.api.internal.changedetection.taskcache.CacheKeyBuilder;

public class MissingFileState implements FileState {
    public static final MissingFileState INSTANCE = new MissingFileState();

    private static final byte[] MISSING_FILE_CODE = "MISSING_FILE".getBytes(Charsets.UTF_8);

    private MissingFileState() {}

    @Override
    public boolean isContentUpToDate(FileState previousState) {
        return previousState instanceof MissingFileState;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileState previousState) {
        return isContentUpToDate(previousState);
    }

    @Override
    public void appendToCacheKey(CacheKeyBuilder keyBuilder) {
        keyBuilder.put(MISSING_FILE_CODE);
    }
}
