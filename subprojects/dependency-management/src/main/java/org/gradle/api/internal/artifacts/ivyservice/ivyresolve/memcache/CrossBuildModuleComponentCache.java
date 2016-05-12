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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

import java.io.File;

public class CrossBuildModuleComponentCache {
    private final Cache<CacheKey<ModuleComponentIdentifier>, CachedModuleVersionResult> metadataCache = CacheBuilder.newBuilder()
        .softValues()
        .build();

    private final Cache<CacheKey<ComponentArtifactMetaData>, File> artifactCache = CacheBuilder.newBuilder()
        .softValues()
        .build();

    public boolean supplyMetaData(String repo, ModuleComponentIdentifier requested, BuildableModuleComponentMetaDataResolveResult result, boolean invalidate) {
        CacheKey<ModuleComponentIdentifier> key = new CacheKey<ModuleComponentIdentifier>(repo, requested);
        CachedModuleVersionResult fromCache = metadataCache.getIfPresent(key);
        if (fromCache == null) {
            return false;
        }
        if (invalidate) {
            metadataCache.invalidate(key);
            return false;
        }
        fromCache.supply(result);
        return true;
    }

    public boolean supplyArtifact(String repo, ComponentArtifactMetaData requested, BuildableArtifactResolveResult result, boolean invalidate) {
        CacheKey<ComponentArtifactMetaData> key = new CacheKey<ComponentArtifactMetaData>(repo, requested);
        File fromCache = artifactCache.getIfPresent(key);
        if (fromCache == null) {
            return false;
        }
        if (invalidate || !fromCache.exists()) {
            artifactCache.invalidate(key);
            return false;
        }
        result.resolved(fromCache);
        return true;
    }

    public void maybeCache(String repo, ModuleComponentIdentifier id, BuildableModuleComponentMetaDataResolveResult result) {
        if (result.hasResult() && result.getState().equals(BuildableModuleComponentMetaDataResolveResult.State.Resolved)) {
            MutableModuleComponentResolveMetaData metaData = result.getMetaData();
            if (!metaData.isChanging() && !metaData.isGenerated()) {
                metadataCache.put(new CacheKey<ModuleComponentIdentifier>(repo, id), new CachedModuleVersionResult(result));
            }
        }
    }

    public void maybeCache(String repo, ComponentArtifactMetaData artifact, BuildableArtifactResolveResult result) {
        if (result.hasResult()) {
            artifactCache.put(new CacheKey<ComponentArtifactMetaData>(repo, artifact), result.getFile());
        }
    }

    public void clearCache() {
        metadataCache.invalidateAll();
        artifactCache.invalidateAll();
    }

    private final static class CacheKey<T> {
        private final String repoId;
        private final T module;


        private CacheKey(String repoId, T componentIdentifier) {
            this.repoId = repoId;
            this.module = componentIdentifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey cacheKey = (CacheKey) o;

            if (!repoId.equals(cacheKey.repoId)) {
                return false;
            }
            return module.equals(cacheKey.module);

        }

        @Override
        public int hashCode() {
            int result = repoId.hashCode();
            result = 31 * result + module.hashCode();
            return result;
        }
    }
}
