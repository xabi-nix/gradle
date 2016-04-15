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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DirectInvokerBenchmark {

    public static class BoxedTypesFun {
        public Long fun(Integer x, Long y) {
            return x * x + y;
        }
    }

    public static class PrimitivesFun {
        public long fun(int x, long y) {
            return x * x + y;
        }
    }

    @State(Scope.Benchmark)
    public static class MethodWithBoxedParamsState {
        BoxedTypesFun doMath = new BoxedTypesFun();

        Method method;
        FastInvoker invoker;
        FastInvokerCache cache;
        Integer x = 0;
        Long y = 0L;

        public MethodWithBoxedParamsState() {
            try {
                method = BoxedTypesFun.class.getDeclaredMethod("fun", Integer.class, Long.class);
            } catch (java.lang.NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            FastInvokerGenerator generator = new FastInvokerGenerator(this.getClass().getClassLoader());
            invoker = generator.forMethod(method);
            cache = new FastInvokerCache();

        }
    }

    @State(Scope.Benchmark)
    public static class MethodWithPrimitiveTypesState {
        PrimitivesFun doMath = new PrimitivesFun();

        Method method;
        FastInvoker invoker;
        FastInvokerCache cache;
        int x;
        long y;

        public MethodWithPrimitiveTypesState() {
            try {
                method = PrimitivesFun.class.getDeclaredMethod("fun", Integer.TYPE, Long.TYPE);
            } catch (java.lang.NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            FastInvokerGenerator generator = new FastInvokerGenerator(this.getClass().getClassLoader());
            invoker = generator.forMethod(method);
            cache = new FastInvokerCache();

        }
    }


    // -------------- Without boxing ---------------
    @Benchmark
    public void boxedArgumentsBaseline(MethodWithBoxedParamsState state, Blackhole bh) {
        bh.consume(state.doMath.fun(state.x++, state.y++));
    }

    @Benchmark
    public void boxedArgumentsReflection(MethodWithBoxedParamsState state, Blackhole bh) throws InvocationTargetException, IllegalAccessException {
        bh.consume(state.method.invoke(state.doMath, state.x++, state.y++));
    }

    @Benchmark
    public void boxedArgumentsGeneratedInvoker(MethodWithBoxedParamsState state, Blackhole bh) {
        bh.consume(state.invoker.invokeMethod(state.doMath, state.x++, state.y++));
    }

    @Benchmark
    public void boxedArgumentsGeneratedInvokerThroughCache(MethodWithBoxedParamsState state, Blackhole bh) {
        bh.consume(state.cache.get(state.method).invokeMethod(state.doMath, state.x++, state.y++));
    }


    // -------------- With boxing ---------------
    @Benchmark
    public void primitiveArgumentsBaseline(MethodWithPrimitiveTypesState state, Blackhole bh) {
        bh.consume(state.doMath.fun(state.x++, state.y++));
    }

    @Benchmark
    public void primitiveArgumentsReflection(MethodWithPrimitiveTypesState state, Blackhole bh) throws InvocationTargetException, IllegalAccessException {
        bh.consume(state.method.invoke(state.doMath, state.x++, state.y++));
    }

    @Benchmark
    public void primitiveArgumentsGeneratedInvoker(MethodWithPrimitiveTypesState state, Blackhole bh) {
        bh.consume(state.invoker.invokeMethod(state.doMath, state.x++, state.y++));
    }

    @Benchmark
    public void primitiveArgumentsGeneratedInvokerThroughCache(MethodWithPrimitiveTypesState state, Blackhole bh) {
        bh.consume(state.cache.get(state.method).invokeMethod(state.doMath, state.x++, state.y++));
    }

}
