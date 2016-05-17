/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder;

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.api.internal.classloading.GroovySystemLoaderFactory;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.*;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Vector;

public class DefaultIsolatedAntBuilder implements IsolatedAntBuilder, Stoppable {

    private final static Logger LOG = Logging.getLogger(DefaultIsolatedAntBuilder.class);

    private final ClassPath libClasspath;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderFactory classLoaderFactory;
    private final ModuleRegistry moduleRegistry;
    private final GroovySystemLoaderFactory groovySystemLoaderFactory;
    private final ClassPathToClassLoaderCache classLoaderCache;

    public DefaultIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory, ModuleRegistry moduleRegistry) {
        this.classPathRegistry = classPathRegistry;
        this.classLoaderFactory = classLoaderFactory;
        this.moduleRegistry = moduleRegistry;
        this.libClasspath = new DefaultClassPath();
        this.groovySystemLoaderFactory = new GroovySystemLoaderFactory();
        this.classLoaderCache = new ClassPathToClassLoaderCache(groovySystemLoaderFactory);
    }

    protected DefaultIsolatedAntBuilder(DefaultIsolatedAntBuilder copy, Iterable<File> libClasspath) {
        this.classPathRegistry = copy.classPathRegistry;
        this.classLoaderFactory = copy.classLoaderFactory;
        this.moduleRegistry = copy.moduleRegistry;
        this.libClasspath = new DefaultClassPath(libClasspath);
        this.groovySystemLoaderFactory = copy.groovySystemLoaderFactory;
        this.classLoaderCache = copy.classLoaderCache;
    }

    public ClassPathToClassLoaderCache getClassLoaderCache() {
        return classLoaderCache;
    }

    public IsolatedAntBuilder withClasspath(Iterable<File> classpath) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Forking a new isolated ant builder for classpath : {}", classpath);
        }
        return new DefaultIsolatedAntBuilder(this, classpath);
    }

    public void execute(final Closure antClosure) {

        List<File> antClasspath = Lists.newArrayList(classPathRegistry.getClassPath("ANT").getAsFiles());
        // Need tools.jar for compile tasks
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar != null) {
            antClasspath.add(toolsJar);
        }

        ClassLoader antLoader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(antClasspath));
        FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().getClassLoader());
        loggingLoader.allowPackage("org.slf4j");
        loggingLoader.allowPackage("org.apache.commons.logging");
        loggingLoader.allowPackage("org.apache.log4j");
        loggingLoader.allowClass(Logger.class);
        loggingLoader.allowClass(LogLevel.class);

        ClassLoader baseAntLoader = new CachingClassLoader(new MultiParentClassLoader(antLoader, loggingLoader));

        // Need gradle core to pick up ant logging adapter, AntBuilder and such
        ClassPath gradleCoreUrls = moduleRegistry.getGradleModule("gradle-core").getImplementationClasspath();
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getGradleModule("gradle-logging").getImplementationClasspath());
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getExternalModule("groovy-all").getClasspath());

        // Need Transformer (part of AntBuilder API) from base services
        gradleCoreUrls = gradleCoreUrls.plus(moduleRegistry.getGradleModule("gradle-base-services").getImplementationClasspath());
        URLClassLoader antAdapterLoader = new MutableURLClassLoader(baseAntLoader, gradleCoreUrls);

        GroovySystemLoader gradleApiGroovyLoader = groovySystemLoaderFactory.forClassLoader(this.getClass().getClassLoader());
        GroovySystemLoader antAdapterGroovyLoader = groovySystemLoaderFactory.forClassLoader(antAdapterLoader);

        URLClassLoader classLoader = new MutableURLClassLoader(baseAntLoader, libClasspath);
        Object antBuilder = newInstanceOf(antAdapterLoader, "org.gradle.api.internal.project.ant.BasicAntBuilder");
        Object antLogger = newInstanceOf(antAdapterLoader, "org.gradle.api.internal.project.ant.AntLoggingAdapter");

        // This looks ugly, very ugly, but that is apparently what Ant does itself
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);

        try {
            configureAntBuilder(antBuilder, antLogger);

            // Ideally, we'd delegate directly to the AntBuilder, but its Closure class is different to our caller's
            // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
            // because they are not an instanceof its Closure class.
            Thread.currentThread().setContextClassLoader(originalLoader);
            Object delegate = new AntBuilderDelegate(antBuilder, classLoader);
            Thread.currentThread().setContextClassLoader(classLoader);

            new ClosureBackedAction<Object>(antClosure).execute(delegate);

        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
            disposeBuilder(antBuilder, antLogger);
        }

        // Remove classes from core Gradle API
        gradleApiGroovyLoader.discardTypesFrom(antAdapterLoader);
        gradleApiGroovyLoader.discardTypesFrom(antLoader);

        // Shutdown the adapter Groovy system
        antAdapterGroovyLoader.shutdown();

        stopClassLoader(classLoader);
        stopClassLoader(antAdapterLoader);
        stopClassLoader((URLClassLoader) antLoader);
        System.gc();
    }

    private static void stopClassLoader(URLClassLoader loader) {
        Method close;
        try {
            close = URLClassLoader.class.getMethod("close");
            close.invoke(loader);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private Object newInstanceOf(ClassLoader antAdapterLoader, String className) {
        // we must use a String literal here, otherwise using things like Foo.class.name will trigger unnecessary
        // loading of classes in the classloader of the DefaultIsolatedAntBuilder, which is not what we want.
        try {
            return antAdapterLoader.loadClass(className).newInstance();
        } catch (Exception e) {
            // should never happen
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    // We *absolutely* need to avoid polluting the project with ClassInfo from *our* classloader
    // So this class must NOT call any dynamic Groovy code. This means we must do what follows using
    // good old java reflection!

    private Object getProject(Object antBuilder) throws Exception {
        return antBuilder.getClass().getMethod("getProject").invoke(antBuilder);
    }

    protected void configureAntBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method addBuildListener = projectClass.getDeclaredMethod("addBuildListener", buildListenerClass);
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            Method getBuildListeners = projectClass.getDeclaredMethod("getBuildListeners");
            Vector listeners = (Vector) getBuildListeners.invoke(project);
            removeBuildListener.invoke(project, listeners.get(0));
            addBuildListener.invoke(project, antLogger);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    protected void disposeBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            // remove build listener
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            removeBuildListener.invoke(project, antLogger);
            antBuilder.getClass().getDeclaredMethod("close").invoke(antBuilder);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    @Override
    public void stop() {
        classLoaderCache.stop();
    }
}
