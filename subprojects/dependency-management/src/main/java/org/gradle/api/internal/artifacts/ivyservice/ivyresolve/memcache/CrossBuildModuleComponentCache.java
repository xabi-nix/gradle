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
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.CachingModuleComponentRepository;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

import java.io.File;
import java.math.BigInteger;

public class CrossBuildModuleComponentCache {
    private final Cache<CacheKey<ModuleComponentIdentifier>, CachedModule> metadataCache = CacheBuilder.newBuilder()
        .softValues()
        .build();

    private final Cache<CacheKey<ComponentArtifactMetaData>, CachedArtifact> artifactCache = CacheBuilder.newBuilder()
        .softValues()
        .build();

    public boolean supplyMetaData(String repo,
                                  ModuleComponentIdentifier requested,
                                  BuildableModuleComponentMetaDataResolveResult result,
                                  boolean isChanging,
                                  CachePolicy cachePolicy,
                                  ComponentMetadataProcessor metadataProcessor) {
        CacheKey<ModuleComponentIdentifier> key = new CacheKey<ModuleComponentIdentifier>(repo, requested);
        CachedModule fromCache = metadataCache.getIfPresent(key);
        if (fromCache == null) {
            return false;
        }
        if (isChanging && (cachePolicy.mustRefreshChangingModule(requested, fromCache.resolvedModuleVersion, fromCache.age()))) {
            metadataCache.invalidate(key);
            return false;
        }
        if (!isChanging && (cachePolicy.mustRefreshModule(requested, fromCache.resolvedModuleVersion, fromCache.age()))) {
            metadataCache.invalidate(key);
            return false;
        }
        fromCache.cachedModule.supply(result);
        metadataProcessor.processMetadata(result.getMetaData());
        return true;
    }

    public boolean supplyArtifact(String repo, ComponentArtifactMetaData requested, BuildableArtifactResolveResult result, ModuleSource moduleSource, CachePolicy cachePolicy) {
        CacheKey<ComponentArtifactMetaData> key = new CacheKey<ComponentArtifactMetaData>(repo, requested);
        CachedArtifact fromCache = artifactCache.getIfPresent(key);
        if (fromCache == null) {
            return false;
        }
        if (!fromCache.file.exists()) {
            artifactCache.invalidate(key);
            return false;
        }
        if (moduleSource instanceof CachingModuleComponentRepository.CachingModuleSource) {
            CachingModuleComponentRepository.CachingModuleSource cms = (CachingModuleComponentRepository.CachingModuleSource) moduleSource;
            BigInteger descriptorHash = cms.getDescriptorHash();
            ArtifactIdentifier artifactIdentifier = ((ModuleComponentArtifactMetaData) requested).toArtifactIdentifier();
            if (cachePolicy.mustRefreshArtifact(artifactIdentifier, fromCache.file, fromCache.age(), cms.isChangingModule(), descriptorHash.equals(fromCache.moduleDescriptorHash))) {
                metadataCache.invalidate(key);
                return false;
            }
            result.resolved(fromCache.file);
            return true;
        } else {
            return false;
        }
    }

    public void maybeCache(String repo, ModuleComponentIdentifier id, BuildableModuleComponentMetaDataResolveResult result) {
        if (result.hasResult() && result.getState().equals(BuildableModuleComponentMetaDataResolveResult.State.Resolved)) {
            MutableModuleComponentResolveMetaData metaData = result.getMetaData();
            if (!metaData.isChanging()) {
                metadataCache.put(new CacheKey<ModuleComponentIdentifier>(repo, id), CachedModule.of(result));
            }
        }
    }

    public void maybeCache(String repo, ModuleSource moduleSource, ComponentArtifactMetaData artifact, BuildableArtifactResolveResult result) {
        if (result.hasResult() && result.getFailure() == null && (moduleSource instanceof CachingModuleComponentRepository.CachingModuleSource)) {
            BigInteger hash = ((CachingModuleComponentRepository.CachingModuleSource) moduleSource).getDescriptorHash();
            File file = result.getFile();
            artifactCache.put(new CacheKey<ComponentArtifactMetaData>(repo, artifact), CachedArtifact.of(hash, file));
        }
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

    private final static class CachedModule {
        private final long timestamp;
        private final CachedModuleVersionResult cachedModule;
        private final ResolvedModuleVersion resolvedModuleVersion;

        private CachedModule(long timestamp, BuildableModuleComponentMetaDataResolveResult result, ResolvedModuleVersion resolvedModuleVersion) {
            this.timestamp = timestamp;
            this.cachedModule = new CachedModuleVersionResult(result);
            this.resolvedModuleVersion = resolvedModuleVersion;
        }

        public static CachedModule of(BuildableModuleComponentMetaDataResolveResult result) {
            return new CachedModule(System.currentTimeMillis(), result, new DefaultResolvedModuleVersion(result.getMetaData().getId()));
        }

        public long age() {
            return System.currentTimeMillis() - timestamp;
        }
    }

    private final static class CachedArtifact {
        private final long timestamp;
        private final BigInteger moduleDescriptorHash;
        private final File file;


        private CachedArtifact(long timestamp, BigInteger moduleDescriptorHash, File file) {
            this.timestamp = timestamp;
            this.moduleDescriptorHash = moduleDescriptorHash;
            this.file = file;
        }

        public static CachedArtifact of(BigInteger hash, File artifact) {
            return new CachedArtifact(System.currentTimeMillis(), hash, artifact);
        }

        public long age() {
            return System.currentTimeMillis() - timestamp;
        }
    }
}
