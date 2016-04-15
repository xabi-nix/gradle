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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.objectweb.asm.Opcodes.*;

public class FastInvokerGenerator {

    private static final Method DEFINE_CLASS_METHOD;

    static {
        Method define;
        try {
            define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);
            define.setAccessible(true);
        } catch (java.lang.NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        DEFINE_CLASS_METHOD = define;
    }

    private final ClassLoader classLoader;

    public FastInvokerGenerator(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Creates an ASM-backed instance creator for a specific constructor. This instantiator must be cached to actually provide a performance improvement. Once it's cached, all instance creations will
     * go through direct invocation, instead of relying on slow reflection.
     *
     * @param ctor the constructor for which we want to create an instantiator
     * @return a fast, direct invoker, instance creator for this constructor
     */
    public FastConstructor forConstructor(Constructor<?> ctor) {
        Class<?> declaringClass = ctor.getDeclaringClass();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final String generatedCreator = declaringClass.getSimpleName() + "GeneratedCreator";
        cw.visit(V1_5, ACC_PUBLIC, generatedCreator, null, "java/lang/Object", new String[]{Type.getInternalName(FastConstructor.class)});
        createDefaultCtor(cw);
        createNewInstanceMethod(cw, declaringClass, ctor);
        cw.visitEnd();
        final byte[] clazzData = cw.toByteArray();
        try {
            @SuppressWarnings("unchecked")
            Class<? extends FastConstructor> invokerClazz = (Class<? extends FastConstructor>) DEFINE_CLASS_METHOD.invoke(classLoader, generatedCreator, clazzData, 0, clazzData.length);
            return invokerClazz.newInstance();
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
    }

    public FastInvoker forMethod(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        final String generatedCreator = generateClassName(method);
        cw.visit(V1_5, ACC_PUBLIC, generatedCreator, null, "java/lang/Object", new String[]{Type.getInternalName(FastInvoker.class)});
        createDefaultCtor(cw);
        createInvokeMethod(cw, declaringClass, method);
        cw.visitEnd();
        final byte[] clazzData = cw.toByteArray();
        // uncomment for debugging
        // ClassReader classReader = new ClassReader(clazzData);
        // TraceClassVisitor trace = new TraceClassVisitor(new PrintWriter(System.out));
        // CheckClassAdapter check = new CheckClassAdapter(trace);
        // classReader.accept(trace, 0);
        // classReader.accept(check, 0);
        try {
            @SuppressWarnings("unchecked")
        Class<? extends FastInvoker> invokerClazz = (Class<? extends FastInvoker>) DEFINE_CLASS_METHOD.invoke(classLoader, generatedCreator.replaceAll("/", "."), clazzData, 0, clazzData.length);
            return invokerClazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateClassName(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        String pkg = declaringClass.getPackage().getName().replace(".", "/") + "/";
        return pkg + declaringClass.getSimpleName() + "_" + method.getName() + Math.abs(method.hashCode());
    }

    private static void createNewInstanceMethod(ClassWriter cw, Class<?> declaringClass, Constructor<?> ctor) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();

        String internalName = Type.getInternalName(declaringClass);
        mv.visitTypeInsn(NEW, internalName);
        mv.visitInsn(DUP);
        Class<?>[] parameterTypes = ctor.getParameterTypes();
        loadParametersOnStack(mv, parameterTypes, true);
        mv.visitMethodInsn(INVOKESPECIAL, internalName, "<init>", Type.getConstructorDescriptor(ctor), false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void createInvokeMethod(ClassWriter cw, Class<?> declaringClass, Method method) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invokeMethod", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();

        String internalName = Type.getInternalName(declaringClass);
        Class<?>[] parameterTypes = method.getParameterTypes();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, internalName);
        loadParametersOnStack(mv, parameterTypes, false);
        int opcode = declaringClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL;
        mv.visitMethodInsn(opcode, internalName, method.getName(), Type.getMethodDescriptor(method), opcode == INVOKEINTERFACE);
        Class<?> returnType = method.getReturnType();
        Type type = Type.getType(returnType);
        opcode = type.getOpcode(Opcodes.IRETURN);
        if (opcode == RETURN) {
            mv.visitInsn(ACONST_NULL);
        } else if (opcode != ARETURN) {
            // need to box, calling for ex Double.valueOf(...)
            Class<?> wrappedType = wrap(returnType);
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(wrappedType), "valueOf", "(" + Type.getDescriptor(returnType) + ")" + Type.getDescriptor(wrappedType), false);
        }
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void loadParametersOnStack(MethodVisitor mv, Class<?>[] parameterTypes, boolean constructor) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            mv.visitVarInsn(ALOAD, constructor ? 1 : 2);
            if (i <= 5) {
                mv.visitInsn(Opcodes.ICONST_0 + i);
            } else {
                mv.visitIntInsn(Opcodes.BIPUSH, i);
            }
            mv.visitInsn(AALOAD); // load parameter value from array
            if (parameterType.isPrimitive()) {
                // need to convert from boxed type to primitive
                Type type = Type.getType(wrap(parameterType));
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
                // then unbox
                mv.visitMethodInsn(INVOKEVIRTUAL, type.getInternalName(), parameterType.getName() + "Value", "()" + Type.getDescriptor(parameterType), false);
            } else {
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(parameterType));
            }
        }
    }

    private static void createDefaultCtor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }


    public static Class<?> wrap(Class<?> type) {
        if (type == Character.TYPE) {
            return Character.class;
        } else if (type == Boolean.TYPE) {
            return Boolean.class;
        } else if (type == Long.TYPE) {
            return Long.class;
        } else if (type == Integer.TYPE) {
            return Integer.class;
        } else if (type == Short.TYPE) {
            return Short.class;
        } else if (type == Byte.TYPE) {
            return Byte.class;
        } else if (type == Float.TYPE) {
            return Float.class;
        } else if (type == Double.TYPE) {
            return Double.class;
        }
        throw new IllegalArgumentException(String.format("Don't know the wrapper type for primitive type %s.", type));
    }
}
