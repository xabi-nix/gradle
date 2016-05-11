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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule;
import org.apache.ivy.core.module.descriptor.DefaultIncludeRule;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.apache.ivy.core.module.descriptor.ExtendsDescriptor;
import org.apache.ivy.core.module.descriptor.IncludeRule;
import org.apache.ivy.core.module.descriptor.License;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleRevisionId;

public class ModuleDescriptorSerializer implements org.gradle.internal.serialize.Serializer<ModuleDescriptor> {
    private final static Field DEPENDENCY_CONFIG_FIELD;

    static {
        Field dependencyConfigField;
        try {
            dependencyConfigField = DefaultDependencyDescriptor.class.getDeclaredField("confs");
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        dependencyConfigField.setAccessible(true);
        DEPENDENCY_CONFIG_FIELD = dependencyConfigField;
    }

    private final ResolverStrategy resolverStrategy;

    public ModuleDescriptorSerializer(ResolverStrategy resolverStrategy) {
        this.resolverStrategy = resolverStrategy;
    }

    @Override
    public ModuleDescriptor read(Decoder decoder) throws EOFException, Exception {
        return new Reader(decoder, resolverStrategy).read();
    }

    @Override
    public void write(Encoder encoder, ModuleDescriptor md) throws Exception {
        new Writer(encoder).write(md);
    }

    private static class Writer {
        private final Encoder encoder;

        private Writer(Encoder encoder) {
            this.encoder = encoder;
        }

        public void write(ModuleDescriptor md) throws IOException {
            writeInfoSection(md);
            writeConfigurations(md);
            writePublications(md);
            writeDependencies(md);
        }

        private void writeInt(int i) throws IOException {
            encoder.writeInt(i);
        }

        private void writeString(String str) throws IOException {
            encoder.writeString(str);
        }

        private void writeNullableString(String str) throws IOException {
            encoder.writeNullableString(str);
        }

        private void writeBoolean(boolean b) throws IOException {
            encoder.writeBoolean(b);
        }

        private void writeLong(long l) throws IOException {
            encoder.writeLong(l);
        }

        private void writeDependencies(ModuleDescriptor md) throws IOException {
            DependencyDescriptor[] dds = md.getDependencies();
            encoder.writeInt(dds.length);
            for (DependencyDescriptor dd : dds) {
                writeDependency(dd);
            }
            writeAllExcludes(md);
        }

        private void writeAllExcludes(ModuleDescriptor md) throws IOException {
            ExcludeRule[] excludes = md.getAllExcludeRules();
            writeInt(excludes.length);
            for (ExcludeRule exclude : excludes) {
                ArtifactId id = exclude.getId();
                writeString(id.getModuleId().getOrganisation());
                writeString(id.getModuleId().getName());
                writeString(id.getName());
                writeString(id.getType());
                writeString(id.getExt());
                writeStringArray(exclude.getConfigurations());
                writeString(exclude.getMatcher().getName());
            }
        }

        private void writeDependency(DependencyDescriptor dep) throws IOException {
            ModuleRevisionId dependencyRevisionId = dep.getDependencyRevisionId();
            writeModuleRevisionId(dependencyRevisionId);
            ModuleRevisionId dynamicConstraintDependencyRevisionId = dep.getDynamicConstraintDependencyRevisionId();
            boolean hasDynamicRevision = !dynamicConstraintDependencyRevisionId.equals(dependencyRevisionId);
            writeBoolean(hasDynamicRevision);
            if (hasDynamicRevision) {
                writeModuleRevisionId(dynamicConstraintDependencyRevisionId);
            }

            writeBoolean(dep.isForce());
            writeBoolean(dep.isChanging());
            writeBoolean(dep.isTransitive());
            writeDependencyConfigurationMapping(dep);
            writeDependencyArtifactDescriptors(dep);

            IncludeRule[] includes = dep.getAllIncludeRules();
            writeIncludeRules(includes);

            ExcludeRule[] excludes = dep.getAllExcludeRules();
            writeExcludeRules(excludes);
        }

        private void writeModuleRevisionId(ModuleRevisionId dependencyRevisionId) throws IOException {
            writeString(dependencyRevisionId.getOrganisation());
            writeString(dependencyRevisionId.getName());
            writeNullableString(dependencyRevisionId.getBranch());
            writeNullableString(dependencyRevisionId.getRevision());
            writeExtraAttributes(dependencyRevisionId.getExtraAttributes());
        }

        private void writeIncludeRules(IncludeRule[] includes) throws IOException {
            writeInt(includes.length);
            for (IncludeRule include : includes) {
                ArtifactId id = include.getId();
                writeString(id.getName());
                writeString(id.getType());
                writeString(id.getExt());
                writeStringArray(include.getConfigurations());
                writeString(include.getMatcher().getName());
            }
        }

        private void writeExcludeRules(ExcludeRule[] excludes) throws IOException {
            writeInt(excludes.length);
            for (ExcludeRule exclude : excludes) {
                ArtifactId id = exclude.getId();
                writeString(id.getModuleId().getOrganisation());
                writeString(id.getModuleId().getName());
                writeString(id.getName());
                writeString(id.getType());
                writeString(id.getExt());
                writeStringArray(exclude.getConfigurations());
                writeString(exclude.getMatcher().getName());
            }
        }

        private void writeDependencyArtifactDescriptors(DependencyDescriptor dep) throws IOException {
            DependencyArtifactDescriptor[] depArtifacts = dep.getAllDependencyArtifacts();
            writeInt(depArtifacts.length);
            for (DependencyArtifactDescriptor depArtifact : depArtifacts) {
                writeString(depArtifact.getName());
                writeString(depArtifact.getType());
                writeString(depArtifact.getExt());
                writeStringArray(depArtifact.getConfigurations());
                writeExtraAttributes(depArtifact.getExtraAttributes());
            }
        }

        private void writeDependencyConfigurationMapping(DependencyDescriptor dep) throws IOException {
            writeString(getConfMapping(dep));
        }

        // todo: replace with proper serialization
        private String getConfMapping(DependencyDescriptor dep) {
            StringBuilder confs = new StringBuilder();
            String[] modConfs = dep.getModuleConfigurations();

            Map<String, List<String>> configMappings;
            if (dep instanceof DefaultDependencyDescriptor) {
                // The `getDependencyConfigurations()` implementation for DefaultDependencyDescriptor does some interpretation of the RHS of the configuration
                // mappings, and gets it wrong for mappings such as '*->@' pr '*->#'. So, instead, reach into the descriptor and get the raw mappings out.
                try {
                    configMappings = Cast.uncheckedCast(DEPENDENCY_CONFIG_FIELD.get(dep));
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            } else {
                configMappings = new HashMap<String, List<String>>();
                for (String modConf : modConfs) {
                    configMappings.put(modConf, Arrays.asList(dep.getDependencyConfigurations(modConfs)));
                }
            }

            for (int j = 0; j < modConfs.length; j++) {
                List<String> depConfs = configMappings.get(modConfs[j]);
                confs.append(modConfs[j]).append("->");
                for (int k = 0; k < depConfs.size(); k++) {
                    confs.append(depConfs.get(k));
                    if (k + 1 < depConfs.size()) {
                        confs.append(",");
                    }
                }
                if (j + 1 < modConfs.length) {
                    confs.append(";");
                }
            }
            return confs.toString();
        }


        private void writePublications(ModuleDescriptor md) throws IOException {
            Artifact[] artifacts = md.getAllArtifacts();
            writeInt(artifacts.length);
            for (Artifact artifact : artifacts) {
                writeString(artifact.getName());
                writeString(artifact.getType());
                writeString(artifact.getExt());
                writeStringArray(artifact.getConfigurations());
                writeExtraAttributes(artifact.getExtraAttributes());
            }
        }

        private void writeStringArray(String[] values) throws IOException {
            writeInt(values.length);
            for (String configuration : values) {
                writeNullableString(configuration);
            }
        }

        private void writeConfigurations(ModuleDescriptor md) throws IOException {
            Configuration[] confs = md.getConfigurations();
            writeInt(confs.length);
            for (Configuration conf : confs) {
                writeConfiguration(conf);
            }
        }

        private void writeConfiguration(Configuration conf) throws IOException {
            writeString(conf.getName());
            writeString(conf.getVisibility().toString());
            writeNullableString(conf.getDescription());
            writeStringArray(conf.getExtends());
            writeBoolean(conf.isTransitive());
            writeNullableString(conf.getDeprecated());
            writeExtraAttributes(conf.getExtraAttributes());
        }

        private void writeInfoSection(ModuleDescriptor md) throws IOException {
            ModuleRevisionId moduleRevisionId = md.getModuleRevisionId();
            writeNullableString(moduleRevisionId.getOrganisation());
            writeNullableString(moduleRevisionId.getName());
            ModuleRevisionId resolvedModuleRevisionId = md.getResolvedModuleRevisionId();
            writeNullableString(resolvedModuleRevisionId.getBranch());
            writeNullableString(resolvedModuleRevisionId.getRevision());
            writeString(md.getStatus());
            writeLong(md.getResolvedPublicationDate().getTime());
            writeBoolean(md.isDefault());
            if (md instanceof DefaultModuleDescriptor) {
                DefaultModuleDescriptor dmd = (DefaultModuleDescriptor) md;
                if (dmd.getNamespace() != null && !dmd.getNamespace().getName().equals("system")) {
                    writeNullableString(dmd.getNamespace().getName());
                } else {
                    writeNullableString(null);
                }
            } else {
                writeNullableString(null);
            }
            writeExtraAttributes(md.getExtraAttributes());

            ExtendsDescriptor[] parents = md.getInheritedDescriptors();
            if (parents.length != 0) {
                throw new UnsupportedOperationException("Extends descriptors not supported.");
            }

            writeLicenses(md);
            writeNullableString(md.getHomePage());
            writeNullableString(md.getDescription());

            writeExtraInfo(md.getExtraInfo());
        }

        private void writeExtraInfo(Map extraInfo) throws IOException {
            writeInt(extraInfo.size());
            for (Object o : extraInfo.entrySet()) {
                Map.Entry extraDescr = (Map.Entry) o;
                // original IvyXmlModuleDescriptorWriter tries to match a `NamespaceId` as the key
                // but it doesn't seem to be possible to have it, given that the only way to add
                // extra info is to call {@link ModuleDescriptor#addExtraInfo}
                Object key = extraDescr.getKey();
                String value = extraDescr.getValue().toString();
                if (key instanceof String) {
                    writeInt(0);
                    writeString((String) key);
                } else {
                    writeInt(1);
                    NamespaceId ns = (NamespaceId) key;
                    writeString(ns.getNamespace());
                    writeString(ns.getName());
                }
                writeString(value);
            }
        }

        private void writeExtraAttributes(Map extraAttributes) throws IOException {
            writeInt(extraAttributes.size());
            for (Object o : extraAttributes.keySet()) {
                writeString(o.toString());
                writeNullableString(extraAttributes.get(o).toString());
            }
        }

        private void writeLicenses(ModuleDescriptor md) throws IOException {
            License[] licenses = md.getLicenses();
            writeInt(licenses.length);
            for (License license : licenses) {
                writeNullableString(license.getName());
                writeNullableString(license.getUrl());
            }
        }
    }

    private static class Reader {
        public static final String[] DEFAULT_CONFIGURATION = new String[]{"default"};
        private final Decoder decoder;
        private final ResolverStrategy resolverStrategy;
        private DefaultModuleDescriptor md;
        private DefaultDependencyDescriptor defaultConfMappingDescriptor;

        private Reader(Decoder decoder, ResolverStrategy resolverStrategy) {
            this.decoder = decoder;
            this.resolverStrategy = resolverStrategy;
        }

        public ModuleDescriptor read() throws IOException {
            this.md = new DefaultModuleDescriptor(XmlModuleDescriptorParser.getInstance(), null);
            readInfoSection();
            readConfigurations();
            readPublications();
            readDependencies();
            return md;
        }

        private int readInt() throws IOException {
            return decoder.readInt();
        }

        private String readString() throws IOException {
            return decoder.readString();
        }

        private String readNullableString() throws IOException {
            return decoder.readNullableString();
        }

        private boolean readBoolean() throws IOException {
            return decoder.readBoolean();
        }

        private long readLong() throws IOException {
            return decoder.readLong();
        }

        private void readDependencies() throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                md.addDependency(readDependency());
            }
            readAllExcludes();
        }

        private void readAllExcludes() throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                String moduleOrg = readString();
                String moduleName = readString();
                String name = readString();
                String type = readString();
                String ext = readString();
                String[] configurations = readStringArray();
                String matcher = readString();
                ArtifactId aid = new ArtifactId(IvyUtil.createModuleId(moduleOrg, moduleName), name, type, ext);
                DefaultExcludeRule rule = new DefaultExcludeRule(aid, resolverStrategy.getPatternMatcher(matcher), null);
                for (String configuration : configurations) {
                    rule.addConfiguration(configuration);
                }
                md.addExcludeRule(rule);
            }
        }

        private DependencyDescriptor readDependency() throws IOException {
            ModuleRevisionId revId = readModuleRevisionId();
            ModuleRevisionId dynId = null;
            boolean dynamicConstraint = readBoolean();
            if (dynamicConstraint) {
                dynId = readModuleRevisionId();
            }

            boolean force = readBoolean();
            boolean changing = readBoolean();
            boolean transitive = readBoolean();

            DefaultDependencyDescriptor dd = dynId == null
                ? new DefaultDependencyDescriptor(md, revId, force, changing, transitive)
                : new DefaultDependencyDescriptor(md, revId, dynId, force, changing, transitive);
            readDependencyConfigurationMapping(dd);
            readDependencyArtifactDescriptors(dd);
            readIncludeRules(dd);
            readExcludeRules(dd);
            return dd;
        }

        ModuleRevisionId readModuleRevisionId() throws IOException {
            String moduleOrg = readString();
            String moduleName = readNullableString();
            String branch = readNullableString();
            String rev = readNullableString();
            Map extraAttributes = readExtraAttributes();
            return createModuleRevisionId(moduleOrg, moduleName, branch, rev, extraAttributes);
        }

        private void readIncludeRules(DefaultDependencyDescriptor dd) throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                String name = readString();
                String type = readString();
                String ext = readString();
                String[] confs = readStringArray();
                String matcher = readString();
                IncludeRule rule = new DefaultIncludeRule(IvyUtil.createArtifactId(
                    md.getModuleRevisionId().getOrganisation(),
                    md.getModuleRevisionId().getName(),
                    name,
                    type,
                    ext),
                    resolverStrategy.getPatternMatcher(matcher),
                    Collections.emptyMap()
                );
                for (String conf : confs) {
                    dd.addIncludeRule(conf, rule);
                }
            }
        }

        private void readExcludeRules(DefaultDependencyDescriptor dd) throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                String moduleOrg = readString();
                String moduleName = readString();
                String name = readString();
                String type = readString();
                String ext = readString();
                String[] confs = readStringArray();
                String matcher = readString();
                ExcludeRule rule = new DefaultExcludeRule(IvyUtil.createArtifactId(
                    moduleOrg,
                    moduleName,
                    name,
                    type,
                    ext),
                    resolverStrategy.getPatternMatcher(matcher),
                    Collections.emptyMap()
                );
                for (String conf : confs) {
                    dd.addExcludeRule(conf, rule);
                }
            }
        }

        private void readDependencyArtifactDescriptors(DefaultDependencyDescriptor dd) throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                String name = readString();
                String type = readString();
                String ext = readString();
                String[] confs = readStringArray();
                Map<String, String> extra = readExtraAttributes();
                String url = extra.get("url");
                DefaultDependencyArtifactDescriptor dad = new DefaultDependencyArtifactDescriptor(
                    dd,
                    name,
                    type,
                    ext,
                    url == null ? null : new URL(url),
                    extra
                );
                if (confs.length == 0) {
                    confs = md.getConfigurationsNames();
                }
                for (String conf : confs) {
                    dd.addDependencyArtifact(conf, dad);
                }
            }
        }

        private void readDependencyConfigurationMapping(DefaultDependencyDescriptor dd) throws IOException {
            String confs = readString();
            parseDepsConfs(confs, dd);
        }

        protected void parseDepsConfs(String confs, DefaultDependencyDescriptor dd) {
            parseDepsConfs(confs, dd, false);
        }

        protected void parseDepsConfs(String confs, DefaultDependencyDescriptor dd,
                                      boolean useDefaultMappingToGuessRightOperande) {
            parseDepsConfs(confs, dd, useDefaultMappingToGuessRightOperande, true);
        }

        protected void parseDepsConfs(String confs, DefaultDependencyDescriptor dd,
                                      boolean useDefaultMappingToGuessRightOperande, boolean evaluateConditions) {
            if (confs == null) {
                return;
            }

            String[] conf = confs.split(";");
            parseDepsConfs(conf, dd, useDefaultMappingToGuessRightOperande, evaluateConditions);
        }

        protected void parseDepsConfs(String[] conf, DefaultDependencyDescriptor dd,
                                      boolean useDefaultMappingToGuessRightOperande) {
            parseDepsConfs(conf, dd, useDefaultMappingToGuessRightOperande, true);
        }

        private void replaceConfigurationWildcards(ModuleDescriptor md) {
            Configuration[] configs = md.getConfigurations();
            for (int i = 0; i < configs.length; i++) {
                configs[i].replaceWildcards(md);
            }
        }

        protected DependencyDescriptor getDefaultConfMappingDescriptor() {
            if (defaultConfMappingDescriptor == null) {
                defaultConfMappingDescriptor = new DefaultDependencyDescriptor(createModuleRevisionId("", "", ""), false);
                parseDepsConfs(DEFAULT_CONFIGURATION, defaultConfMappingDescriptor, false, false);
            }
            return defaultConfMappingDescriptor;
        }

        protected void parseDepsConfs(String[] conf, DefaultDependencyDescriptor dd,
                                      boolean useDefaultMappingToGuessRightOperande, boolean evaluateConditions) {
            replaceConfigurationWildcards(md);
            for (int i = 0; i < conf.length; i++) {
                String[] ops = conf[i].split("->");
                if (ops.length == 1) {
                    String[] modConfs = ops[0].split(",");
                    if (!useDefaultMappingToGuessRightOperande) {
                        for (int j = 0; j < modConfs.length; j++) {
                            dd.addDependencyConfiguration(modConfs[j].trim(), modConfs[j].trim());
                        }
                    } else {
                        for (int j = 0; j < modConfs.length; j++) {
                            String[] depConfs = getDefaultConfMappingDescriptor()
                                .getDependencyConfigurations(modConfs[j]);
                            if (depConfs.length > 0) {
                                for (int k = 0; k < depConfs.length; k++) {
                                    String mappedDependency = evaluateConditions
                                        ? evaluateCondition(depConfs[k].trim(), dd)
                                        : depConfs[k].trim();
                                    if (mappedDependency != null) {
                                        dd.addDependencyConfiguration(modConfs[j].trim(),
                                            mappedDependency);
                                    }
                                }
                            } else {
                                // no default mapping found for this configuration, map
                                // configuration to itself
                                dd.addDependencyConfiguration(modConfs[j].trim(), modConfs[j]
                                    .trim());
                            }
                        }
                    }
                } else if (ops.length == 2) {
                    String[] modConfs = ops[0].split(",");
                    String[] depConfs = ops[1].split(",");
                    for (int j = 0; j < modConfs.length; j++) {
                        for (int k = 0; k < depConfs.length; k++) {
                            String mappedDependency = evaluateConditions ? evaluateCondition(
                                depConfs[k].trim(), dd) : depConfs[k].trim();
                            if (mappedDependency != null) {
                                dd.addDependencyConfiguration(modConfs[j].trim(), mappedDependency);
                            }
                        }
                    }
                } else {
                    throw new IllegalArgumentException("invalid conf " + conf[i] + " for " + dd);
                }
            }

            if (md.isMappingOverride()) {
                addExtendingConfigurations(conf, dd, useDefaultMappingToGuessRightOperande);
            }
        }

        private void addExtendingConfigurations(String[] confs, DefaultDependencyDescriptor dd,
                                                boolean useDefaultMappingToGuessRightOperande) {
            for (int i = 0; i < confs.length; i++) {
                addExtendingConfigurations(confs[i], dd, useDefaultMappingToGuessRightOperande);
            }
        }

        private void addExtendingConfigurations(String conf, DefaultDependencyDescriptor dd,
                                                boolean useDefaultMappingToGuessRightOperande) {
            Set configsToAdd = new HashSet();
            Configuration[] configs = md.getConfigurations();
            for (int i = 0; i < configs.length; i++) {
                String[] ext = configs[i].getExtends();
                for (int j = 0; j < ext.length; j++) {
                    if (conf.equals(ext[j])) {
                        String configName = configs[i].getName();
                        configsToAdd.add(configName);
                        addExtendingConfigurations(configName, dd,
                            useDefaultMappingToGuessRightOperande);
                    }
                }
            }

            String[] confs = (String[]) configsToAdd.toArray(new String[0]);
            parseDepsConfs(confs, dd, useDefaultMappingToGuessRightOperande);
        }

        /**
         * Evaluate the optional condition in the given configuration, like "[org=MYORG]confX". If the condition evaluates to true, the configuration is returned, if the condition evaluatate to false,
         * null is returned. If there are no conditions, the configuration itself is returned.
         *
         * @param conf the configuration to evaluate
         * @param dd the dependencydescriptor to which the configuration will be added
         * @return the evaluated condition
         */
        private String evaluateCondition(String conf, DefaultDependencyDescriptor dd) {
            if (conf.charAt(0) != '[') {
                return conf;
            }

            int endConditionIndex = conf.indexOf(']');
            if (endConditionIndex == -1) {
                throw new IllegalArgumentException("invalid conf " + conf + " for " + dd);
            }

            String condition = conf.substring(1, endConditionIndex);

            int notEqualIndex = condition.indexOf("!=");
            if (notEqualIndex == -1) {
                int equalIndex = condition.indexOf('=');
                if (equalIndex == -1) {
                    throw new IllegalArgumentException("invalid conf " + conf + " for " + dd.getDependencyRevisionId());
                }

                String leftOp = condition.substring(0, equalIndex).trim();
                String rightOp = condition.substring(equalIndex + 1).trim();

                // allow organisation synonyms, like 'org' or 'organization'
                if (leftOp.equals("org") || leftOp.equals("organization")) {
                    leftOp = "organisation";
                }

                String attrValue = dd.getAttribute(leftOp);
                if (!rightOp.equals(attrValue)) {
                    return null;
                }
            } else {
                String leftOp = condition.substring(0, notEqualIndex).trim();
                String rightOp = condition.substring(notEqualIndex + 2).trim();

                // allow organisation synonyms, like 'org' or 'organization'
                if (leftOp.equals("org") || leftOp.equals("organization")) {
                    leftOp = "organisation";
                }

                String attrValue = dd.getAttribute(leftOp);
                if (rightOp.equals(attrValue)) {
                    return null;
                }
            }

            return conf.substring(endConditionIndex + 1);
        }


        private void readPublications() throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                String name = readString();
                String type = readString();
                String ext = readString();
                String[] confs = readStringArray();
                Map<String, String> extraAttributes = readExtraAttributes();
                Artifact artifact = new DefaultArtifact(
                    md.getModuleRevisionId(),
                    md.getPublicationDate(),
                    name,
                    type,
                    ext,
                    extraAttributes
                );
                if (confs.length == 0) {
                    confs = md.getConfigurationsNames();
                }
                for (String conf : confs) {
                    md.addArtifact(conf, artifact);
                }
            }
        }

        private String[] readStringArray() throws IOException {
            int len = readInt();
            String[] result = new String[len];
            for (int i = 0; i < len; i++) {
                result[i] = readNullableString();
            }
            return result;
        }

        private void readConfigurations() throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                readConfiguration();
            }
        }

        private void readConfiguration() throws IOException {
            String name = readString();
            Configuration.Visibility visibility = Configuration.Visibility.getVisibility(readString());
            String desc = readNullableString();
            String[] ext = readStringArray();
            boolean transitive = readBoolean();
            String deprecated = readNullableString();
            Map<String, String> extra = readExtraAttributes();
            Configuration conf = new Configuration(name, visibility, desc, ext, transitive, deprecated);
            md.addConfiguration(conf);
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                conf.setExtraAttribute(entry.getKey(), entry.getValue());
            }
        }

        private void readInfoSection() throws IOException {
            String org = readNullableString();
            String name = readNullableString();
            String branch = readNullableString();
            String rev = readNullableString();
            md.setStatus(readString());
            md.setResolvedPublicationDate(new Date(readLong()));
            md.setDefault(readBoolean());
            String nsName = readNullableString();
            if (nsName != null) {
                Namespace ns = new Namespace();
                ns.setName(nsName);
                md.setNamespace(ns);
            }
            Map<String, String> extraAttributes = readExtraAttributes();
            md.setModuleRevisionId(createModuleRevisionId(org, name, branch, rev, extraAttributes));
            readLicenses();
            md.setHomePage(readNullableString());
            md.setDescription(readNullableString());

            readExtraInfo();
        }

        @SuppressWarnings("unchecked")
        private void readExtraInfo() throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                Object key;
                switch (readInt()) {
                    case 0:
                        key = readString();
                        break;
                    case 1:
                        key = new NamespaceId(readString(), readString());
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected key type");
                }
                // Ivy has the good idea of declaring extraInfo as <String, String>
                // but it's in reality <String|NamespaceId, String>
                // and the only way to add the NamespaceId version is to use this...
                md.getExtraInfo().put(key, readString());
            }
        }

        private Map<String, String> readExtraAttributes() throws IOException {
            int len = readInt();
            if (len == 0) {
                return Collections.emptyMap();
            }
            Map<String, String> map = new HashMap<String, String>();
            for (int i = 0; i < len; i++) {
                String key = readString();
                String value = readNullableString();
                map.put(key, value);
            }
            return map;
        }

        private void readLicenses() throws IOException {
            int len = readInt();
            for (int i = 0; i < len; i++) {
                String name = readNullableString();
                String url = readNullableString();
                md.addLicense(new License(name, url));
            }
        }
    }

}
