/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

class InMemoryCachedModuleComponentRepository extends BaseModuleComponentRepository {
    private final ModuleComponentRepositoryAccess localAccess;
    private final ModuleComponentRepositoryAccess remoteAccess;
    private final ResolutionStrategyInternal strategy;
    private final ComponentMetadataProcessor metadataProcessor;

    public InMemoryCachedModuleComponentRepository(ResolutionStrategyInternal strategy, InMemoryModuleComponentRepositoryCaches cache, ModuleComponentRepository delegate, CrossBuildModuleComponentCache crossBuildCache, ComponentMetadataProcessor metadataProcessor) {
        super(delegate);
        this.strategy = strategy;
        this.metadataProcessor = metadataProcessor;
        this.localAccess = new CachedAccess(delegate.getLocalAccess(), cache.localArtifactsCache, cache.localMetaDataCache, null);
        this.remoteAccess = new CachedAccess(delegate.getRemoteAccess(), cache.remoteArtifactsCache, cache.remoteMetaDataCache, crossBuildCache);
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    private class CachedAccess extends BaseModuleComponentRepositoryAccess {
        private final InMemoryMetaDataCache metaDataCache;
        private final CrossBuildModuleComponentCache crossBuildCache;
        private final InMemoryArtifactsCache artifactsCache;
        private final String repoId;

        public CachedAccess(ModuleComponentRepositoryAccess access, InMemoryArtifactsCache artifactsCache, InMemoryMetaDataCache metaDataCache, CrossBuildModuleComponentCache crossBuildCache) {
            super(access);
            this.artifactsCache = artifactsCache;
            this.metaDataCache = metaDataCache;
            this.crossBuildCache = crossBuildCache;
            this.repoId = getId();
        }

        @Override
        public String toString() {
            return "in-memory cache > " + getDelegate().toString();
        }

        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionListingResolveResult result) {
            if (!metaDataCache.supplyModuleVersions(dependency.getRequested(), result)) {
                super.listModuleVersions(dependency, result);
                metaDataCache.newModuleVersions(dependency.getRequested(), result);
            }
        }

        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
            boolean changing = requestMetaData.isChanging();
            if (crossBuildCache==null || !crossBuildCache.supplyMetaData(repoId, moduleComponentIdentifier, result, changing, strategy.getCachePolicy(), metadataProcessor)) {
                if (!metaDataCache.supplyMetaData(moduleComponentIdentifier, result)) {
                    super.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
                    metaDataCache.newDependencyResult(moduleComponentIdentifier, result);
                }
                if (crossBuildCache!=null) {
                    crossBuildCache.maybeCache(repoId, moduleComponentIdentifier, result);
                }
            }
        }

        public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            if (crossBuildCache== null || !crossBuildCache.supplyArtifact(repoId, artifact, result, moduleSource, strategy.getCachePolicy())) {
                if (!artifactsCache.supplyArtifact(artifact.getId(), result)) {
                    super.resolveArtifact(artifact, moduleSource, result);
                    artifactsCache.newArtifact(artifact.getId(), result);
                }
                if (crossBuildCache != null) {
                    crossBuildCache.maybeCache(repoId, moduleSource, artifact, result);
                }
            }
        }
    }
}
