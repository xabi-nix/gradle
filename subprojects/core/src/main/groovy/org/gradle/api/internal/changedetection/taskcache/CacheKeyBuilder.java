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

package org.gradle.api.internal.changedetection.taskcache;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import org.gradle.api.file.FileCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Callable;

public class CacheKeyBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheKeyBuilder.class);

    private static final long NULL = 3260143849197285584L;
    private static final long COLLECTION = 8625366162395921885L;
    private static final long MAP = 4507968025271448341L;

    private final Hasher hasher;
    private final OutputStream hasherStream;
    private ByteArrayOutputStream objectBuffer;
    private ObjectOutputStream hasherObjectStream;

    public CacheKeyBuilder() {
        this.hasher = Hashing.md5().newHasher();
        this.hasherStream = Funnels.asOutputStream(hasher);
    }

    public void put(Object value) {
        try {
            if (value instanceof Callable) {
                put(((Callable<?>) value).call());
            } else if (value instanceof File) {
                // TODO:LPTR Maybe we should warn if this happens?
                put(((File) value).getAbsolutePath());
            } else if (value instanceof FileCollection) {
                Set<File> files = ((FileCollection) value).getFiles();
                if (!(files instanceof SortedSet)) {
                    files = ImmutableSortedSet.copyOf(files);
                }
                putCollection(files);
            } else if (value instanceof Collection) {
                putCollection((Collection) value);
            } else if (value instanceof Map) {
                putMap((Map<?, ?>) value);
            } else {
                putInternal(value);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private void putInternal(Object value) throws IOException {
        LOGGER.debug("Digesting {} for cache key", value);
        if (value == null) {
            hasher.putLong(NULL);
        } else if (value instanceof String) {
            putString((String) value);
        } else if (value instanceof Boolean) {
            hasher.putBoolean((Boolean) value);
        } else if (value instanceof byte[]) {
            hasher.putBytes((byte[]) value);
        } else if (value instanceof Byte) {
            hasher.putShort((Byte) value);
        } else if (value instanceof Short) {
            hasher.putShort((Short) value);
        } else if (value instanceof Integer) {
            hasher.putInt((Integer) value);
        } else if (value instanceof Long) {
            hasher.putLong((Long) value);
        } else if (value instanceof Float) {
            hasher.putFloat((Float) value);
        } else if (value instanceof Double) {
            hasher.putDouble((Double) value);
        } else if (value instanceof ByteSource) {
            if (LOGGER.isDebugEnabled()) {
                Hasher debugHasher = Hashing.md5().newHasher();
                ((ByteSource) value).copyTo(Funnels.asOutputStream(debugHasher));
                LOGGER.debug("  -> MD5 hash of ByteSource is {}", debugHasher.hash());
            }
            ((ByteSource) value).copyTo(hasherStream);
        } else {
            if (hasherObjectStream == null) {
                objectBuffer = new ByteArrayOutputStream();
                hasherObjectStream = new ObjectOutputStream(objectBuffer);
                hasherObjectStream.flush();
                objectBuffer.reset();
            }

            // Cache key elements must be serializable
            hasherObjectStream.writeObject(value);
            hasherObjectStream.flush();

            objectBuffer.writeTo(hasherStream);
            objectBuffer.reset();
        }
    }

    public HashCode build() {
        return hasher.hash();
    }

    private void putCollection(Collection<?> collection) {
        // TODO:LPTR Make sure self-referencing collections don't create infinite loops
        hasher.putLong(COLLECTION);
        hasher.putInt(collection.size());
        for (Object item : collection) {
            put(item);
        }
    }

    private void putMap(Map<?, ?> map) {
        // TODO:LPTR Make sure self-referencing maps don't create infinite loops
        hasher.putLong(MAP);
        hasher.putInt(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            put(entry.getKey());
            put(entry.getValue());
        }
    }

    private void putString(String value) {
        // Use consistent encoding to ensure match between platforms
        hasher.putString(value, Charsets.UTF_16);
    }
}
