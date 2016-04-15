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

package org.gradle.experiments.reflection;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.WeakHashMap;

public class FastInvokerCache {
    private final Object lock = new Object();
    private final WeakHashMap<Method, FastInvoker> cache = new WeakHashMap<Method, FastInvoker>();

    public FastInvoker get(Method method) {
        synchronized (lock) {
            FastInvoker fastInvoker = cache.get(method);
            if (fastInvoker == null) {
                Class<?> owner = method.getDeclaringClass();
                fastInvoker = new FastInvokerGenerator(new URLClassLoader(new URL[0], owner.getClassLoader())).forMethod(method);
                cache.put(method, fastInvoker);
            }
            return fastInvoker;
        }
    }
}
