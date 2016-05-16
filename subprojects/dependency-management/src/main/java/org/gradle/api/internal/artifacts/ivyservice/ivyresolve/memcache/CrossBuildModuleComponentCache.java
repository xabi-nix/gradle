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
import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.CachingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChainModuleSource;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenMetadata;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentUsage;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The cross build module component cache caches resolution of module metadata and artifacts for remote access.
 * Since it's a cross-build cache, only successful resolutions are cached: failures are always re-attempted.
 */
public class CrossBuildModuleComponentCache {
    private final Cache<CacheKey<ModuleComponentIdentifier>, CachedModule> metadataCache = CacheBuilder.newBuilder()
        .softValues()
        .build();

    private final Cache<CacheKey<ComponentArtifactMetaData>, CachedArtifact> artifactCache = CacheBuilder.newBuilder()
        .softValues()
        .build();

    private final ConcurrentMap<URI, SoftReference<ModuleComponentResolveCacheEntry>> uriToModuleComponentCache = new ConcurrentHashMap<URI, SoftReference<ModuleComponentResolveCacheEntry>>();
    private final ConcurrentMap<URI, SoftReference<MavenMetadataCacheEntry>> uriToMavenMetadataCacheEntry = new ConcurrentHashMap<URI, SoftReference<MavenMetadataCacheEntry>>();

    public <T extends ModuleComponentResolveMetaData> T getModuleComponentResolveMetadata(LocallyAvailableExternalResource resource, Factory<T> loader) {
        URI uri = resource.getURI();
        SoftReference<ModuleComponentResolveCacheEntry> ref = uriToModuleComponentCache.get(uri);
        LocallyAvailableResource localResource = resource.getLocalResource();
        if (ref!=null) {
            ModuleComponentResolveCacheEntry cacheEntry = ref.get();
            if (cacheEntry != null && cacheEntry.lastModified == localResource.getLastModified() && cacheEntry.contentLength == localResource.getContentLength()) {
                return Cast.uncheckedCast(cacheEntry.metadata);
            }
        }
        T value = loader.create();
        uriToModuleComponentCache.putIfAbsent(uri, new SoftReference<ModuleComponentResolveCacheEntry>(new ModuleComponentResolveCacheEntry(localResource.getLastModified(), localResource.getContentLength(), value)));
        return value;
    }

    public ModuleComponentRepository getRepository(ModuleComponentRepository delegate, CachePolicy cachePolicy, ComponentMetadataProcessor metadataProcessor) {
        return new CrossBuildCacheRepository(delegate, cachePolicy, metadataProcessor);
    }

    public void supplyMavenMetadataInfo(LocallyAvailableExternalResource resource, MavenMetadata metadata, Action<MavenMetadata> loader) {
        URI uri = resource.getURI();
        SoftReference<MavenMetadataCacheEntry> ref = uriToMavenMetadataCacheEntry.get(uri);
        LocallyAvailableResource localResource = resource.getLocalResource();
        if (ref!=null) {
            MavenMetadataCacheEntry cacheEntry = ref.get();
            if (cacheEntry != null && cacheEntry.lastModified == localResource.getLastModified() && cacheEntry.contentLength == localResource.getContentLength()) {
                metadata.copyFrom(cacheEntry.metadata);
                return;
            }
        }
        loader.execute(metadata);
        uriToMavenMetadataCacheEntry.putIfAbsent(uri, new SoftReference<MavenMetadataCacheEntry>(new MavenMetadataCacheEntry(localResource.getLastModified(), localResource.getContentLength(), metadata)));
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
        private final MutableModuleComponentResolveMetaData metaData;
        private final ResolvedModuleVersion resolvedModuleVersion;
        private final boolean authoritative;

        private CachedModule(long timestamp, BuildableModuleComponentMetaDataResolveResult result, ResolvedModuleVersion resolvedModuleVersion) {
            this.timestamp = timestamp;
            this.metaData = result.getMetaData().copy();
            this.authoritative = result.isAuthoritative();
            this.resolvedModuleVersion = resolvedModuleVersion;
        }

        public void supply(BuildableModuleComponentMetaDataResolveResult result) {
            MutableModuleComponentResolveMetaData metaData = this.metaData.copy();
            result.resolved(metaData);
            result.setAuthoritative(authoritative);
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

    private class CrossBuildCacheRepository extends BaseModuleComponentRepository {

        private final CachePolicy cachePolicy;
        private final ComponentMetadataProcessor metadataProcessor;
        private final ModuleComponentRepositoryAccess remoteAccess;

        public CrossBuildCacheRepository(ModuleComponentRepository delegate, CachePolicy cachePolicy, ComponentMetadataProcessor metadataProcessor) {
            super(delegate);
            this.cachePolicy = cachePolicy;
            this.metadataProcessor = metadataProcessor;
            this.remoteAccess = new CrossBuildModuleComponentRepositoryAccess(super.getRemoteAccess());
        }

        @Override
        public ModuleComponentRepositoryAccess getRemoteAccess() {
            return remoteAccess;
        }

        private class CrossBuildModuleComponentRepositoryAccess extends BaseModuleComponentRepositoryAccess {
            private final String repoId;

            public CrossBuildModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess access) {
                super(access);
                this.repoId = CrossBuildCacheRepository.this.getId();
            }

            public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
                boolean changing = requestMetaData.isChanging();
                if (!supplyMetaData(repoId, moduleComponentIdentifier, result, changing, cachePolicy, metadataProcessor)) {
                    super.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
                    maybeCache(repoId, moduleComponentIdentifier, result);
                } else {
                    metadataProcessor.processMetadata(result.getMetaData());
                }
            }

            public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
                if (!supplyArtifact(repoId, artifact, result, moduleSource, cachePolicy)) {
                    super.resolveArtifact(artifact, moduleSource, result);
                    maybeCache(repoId, moduleSource, artifact, result);
                }
            }

            @Override
            public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
                super.resolveModuleArtifacts(component, artifactType, result);
            }

            @Override
            public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result) {
                super.resolveModuleArtifacts(component, componentUsage, result);
            }

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
                MutableModuleComponentResolveMetaData metaData = fromCache.metaData;
                isChanging |= metaData.isChanging();
                if (isChanging && (cachePolicy.mustRefreshChangingModule(requested, fromCache.resolvedModuleVersion, fromCache.age()))) {
                    metadataCache.invalidate(key);
                    return false;
                }
                if (!isChanging && (cachePolicy.mustRefreshModule(requested, fromCache.resolvedModuleVersion, fromCache.age()))) {
                    metadataCache.invalidate(key);
                    return false;
                }

                fromCache.supply(result);
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
                CachingModuleComponentRepository.CachingModuleSource ms = cachingModuleSource(moduleSource);
                if (ms != null) {
                    BigInteger descriptorHash = ms.getDescriptorHash();
                    ArtifactIdentifier artifactIdentifier = ((ModuleComponentArtifactMetaData) requested).toArtifactIdentifier();
                    if (cachePolicy.mustRefreshArtifact(artifactIdentifier, fromCache.file, fromCache.age(), ms.isChangingModule(), descriptorHash.equals(fromCache.moduleDescriptorHash))) {
                        metadataCache.invalidate(key);
                        return false;
                    }
                    result.resolved(fromCache.file);
                    return true;
                } else {
                    return false;
                }
            }

            private CachingModuleComponentRepository.CachingModuleSource cachingModuleSource(ModuleSource ms) {
                if (ms instanceof CachingModuleComponentRepository.CachingModuleSource) {
                    return (CachingModuleComponentRepository.CachingModuleSource) ms;
                }
                if (ms instanceof RepositoryChainModuleSource) {
                    return cachingModuleSource(((RepositoryChainModuleSource) ms).getDelegate());
                }
                return null;
            }

            public void maybeCache(String repo, ModuleComponentIdentifier id, BuildableModuleComponentMetaDataResolveResult result) {
                if (result.hasResult() && result.getState().equals(BuildableModuleComponentMetaDataResolveResult.State.Resolved)) {
                    metadataCache.put(new CacheKey<ModuleComponentIdentifier>(repo, id), CachedModule.of(result));
                }
            }

            public void maybeCache(String repo, ModuleSource moduleSource, ComponentArtifactMetaData artifact, BuildableArtifactResolveResult result) {
                if (result.hasResult() && result.getFailure() == null) {
                    CachingModuleComponentRepository.CachingModuleSource cms = cachingModuleSource(moduleSource);
                    if (cms != null) {
                        BigInteger hash = cms.getDescriptorHash();
                        File file = result.getFile();
                        artifactCache.put(new CacheKey<ComponentArtifactMetaData>(repo, artifact), CachedArtifact.of(hash, file));
                    }
                }
            }
        }
    }

    private static class LocalResourceCacheEntry<T> {
        protected final long lastModified;
        protected final long contentLength;
        protected final T metadata;

        private LocalResourceCacheEntry(long lastModified, long contentLength, T metadata) {
            this.lastModified = lastModified;
            this.contentLength = contentLength;
            this.metadata = metadata;
        }
    }

    private static class ModuleComponentResolveCacheEntry extends LocalResourceCacheEntry<ModuleComponentResolveMetaData> {
        private ModuleComponentResolveCacheEntry(long lastModified, long contentLength, ModuleComponentResolveMetaData metadata) {
            super(lastModified, contentLength, metadata);
        }
    }

    private static class MavenMetadataCacheEntry extends LocalResourceCacheEntry<MavenMetadata> {
        private MavenMetadataCacheEntry(long lastModified, long contentLength, MavenMetadata metadata) {
            super(lastModified, contentLength, metadata);
        }
    }
}
