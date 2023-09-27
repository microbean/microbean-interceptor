/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.interceptor;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import java.util.Objects;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import static java.lang.invoke.LambdaMetafactory.metafactory;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TestLambdaMetafactory {

  private TestLambdaMetafactory() {
    super();
  }

  @Test
  final void testLambdaMetafactoryVirtualWithCapturing() throws Throwable {
    // See https://stackoverflow.com/a/30633563/208288 and https://stackoverflow.com/a/73749785/208288.

    final Lookup lookup = lookup();

    // Use the metametafactory (the LambdaMetafactory#metafactory(Lookup, String, MethodType, MethodType, MethodHandle,
    // MethodType) method) to create a metafactory (a ConstantCallSite).
    final ConstantCallSite metafactory =
      (ConstantCallSite)metafactory(lookup,
                                    "get", // the single-abstract-method of the "function object"
                                    methodType(Supplier.class, // the functional interface implemented by the "function object" (the "factory")
                                               this.getClass(), // capture (for "this")
                                               String.class), // capture (for #crap(String)'s sole parameter)
                                    methodType(Object.class), // erased return type of the "function object" SAM
                                    lookup.findVirtual(this.getClass(),
                                                       "crap",
                                                       methodType(String.class, String.class)),
                                    methodType(String.class)); // unerased return type of the "function object" SAM
    // Use the metafactory (a ConstantCallSite) to create a factory (a MethodHandle) via its #getTarget() factory
    // method.
    final MethodHandle factory = metafactory.getTarget();

    // (One thing that's interesting is that the factory is also a Constable. So you could squirrel this away inside
    // some other dynamic constant and use it to initialize some static Supplier<String> field somewhere.)

    // Use the factory (a MethodHandle) to create a functional interface implementation (a "function object") via its
    // #invokeExact() factory method.
    final Supplier<String> s0 = (Supplier<String>)factory.invokeExact(this, "Yo"); // capture this, "Yo"
    assertEquals("Yo", s0.get());

    // Do it again, capturing different variables.
    final Supplier<String> s1 = (Supplier<String>)factory.invokeExact(this, "Dawg"); // capture this, "Dawg"
    assertEquals("Dawg", s1.get());
  }

  private final String crap(final String s) {
    return s;
  }

  private final Object aroundInvokeVirtual(final InvocationContext ic) throws Exception {
    return ic.proceed();
  }

  private static final Object aroundInvokeStatic(final InvocationContext ic) throws Exception {
    return ic.proceed();
  }

  private final <T, U extends T> Function<InvocationContext, Object> frobnicate2(final Lookup lookup,
                                                                                 final MethodHandle aroundInvokeMethod,
                                                                                 final Class<T> receiverType,
                                                                                 final Supplier<U> receiverSupplier)
    throws Throwable {
    final MethodType aroundInvokeMethodType = aroundInvokeMethod.type();
    if (aroundInvokeMethodType.returnType() != Object.class) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().returnType() != Object.class: " + aroundInvokeMethodType);
    } else if (receiverType == null) {
      if (aroundInvokeMethodType.parameterCount() != 1) {
        throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterCount() != 1: " + aroundInvokeMethodType);
      } else if (aroundInvokeMethodType.parameterType(0) != InvocationContext.class) {
        throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterType(0) != InvocationContext.class: " + aroundInvokeMethodType);
      }
    } else if (aroundInvokeMethodType.parameterCount() != 2) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterCount() != 2: " + aroundInvokeMethodType);
    } else if (aroundInvokeMethodType.parameterType(0) != receiverType) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterType(0) != " + receiverType.getName() + ".class: " + aroundInvokeMethodType);
    } else if (aroundInvokeMethodType.parameterType(1) != InvocationContext.class) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterType(0) != InvocationContext.class: " + aroundInvokeMethodType);
    }
    
    final ConstantCallSite metafactory =
      (ConstantCallSite)metafactory(lookup,
                                    "apply",
                                    receiverType == null ? methodType(Function.class) : methodType(Function.class, receiverType), // Function is the functional interface we want. receiverType is a capture.
                                    methodType(Object.class, Object.class), // Function<T, R> erasure
                                    aroundInvokeMethod,
                                    methodType(Object.class, InvocationContext.class));
    final MethodHandle target = metafactory.getTarget();
    if (receiverType == null) {
      return (Function<InvocationContext, Object>)target.invokeExact();
    }

    // See https://stackoverflow.com/a/77060967/208288
    // 
    // If we want to use invokeExact() on target/factory, which we do here because we don't know how often the Function
    // we return will be invoked, we need to change the type of its receiver parameter to permit the invokeExact call to
    // work in this use case. Normally the symbolic type descriptor kind of does this for you if I understand right, but
    // we'll be invoking the factory repeatedly on the return value of a Supplier.get(), so Object.class is all that's
    // needed. See
    // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html#:~:text=or%20else%20Object%20if%20the%20invocation%20is%20an%20expression.
    //
    // You might think that you could change the receiverType argument of the first methodType argument to metafactory()
    // above to simply be Object.class, but that does not work.
    final MethodHandle factory = target.asType(target.type().changeParameterType(0, Object.class));
    return ic -> {
      try {
        return ((Function<InvocationContext, Object>)factory.invokeExact(receiverSupplier.get())).apply(ic);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final Throwable e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    };
  }
  
  private final <T> Function<T, Function<InvocationContext, Object>> frobnicate(final Lookup lookup,
                                                                                final MethodHandle aroundInvokeMethod,
                                                                                final Class<T> receiverType)
    throws Throwable {
    Objects.requireNonNull(receiverType); // for now
    final MethodType aroundInvokeMethodType = aroundInvokeMethod.type();
    if (aroundInvokeMethodType.returnType() != Object.class) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().returnType() != Object.class: " + aroundInvokeMethodType);
    } else if (receiverType == null) {
      if (aroundInvokeMethodType.parameterCount() != 1) {
        throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterCount() != 1: " + aroundInvokeMethodType);
      } else if (aroundInvokeMethodType.parameterType(0) != InvocationContext.class) {
        throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterType(0) != InvocationContext.class: " + aroundInvokeMethodType);
      }
    } else if (aroundInvokeMethodType.parameterCount() != 2) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterCount() != 2: " + aroundInvokeMethodType);
    } else if (aroundInvokeMethodType.parameterType(0) != receiverType) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterType(0) != " + receiverType.getName() + ".class: " + aroundInvokeMethodType);
    } else if (aroundInvokeMethodType.parameterType(1) != InvocationContext.class) {
      throw new IllegalArgumentException("aroundInvokeMethodType.methodType().parameterType(0) != InvocationContext.class: " + aroundInvokeMethodType);
    }
    
    final ConstantCallSite ccs =
      (ConstantCallSite)metafactory(lookup,
                                    "apply",
                                    methodType(Function.class, receiverType), // Function is the functional interface we want. receiverType is a capture.
                                    methodType(Object.class, Object.class), // Function<Object, InvocationContext> erasure
                                    aroundInvokeMethod,
                                    methodType(Object.class, InvocationContext.class));
    final MethodHandle factory = ccs.getTarget();
    return t -> {
      try {
        return (Function<InvocationContext, Object>)factory.invoke(t);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final Throwable e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    };
    // return ccs.getTarget();
  }

  @Test
  final void testFrobnicate() throws Throwable {
    final Lookup lookup = lookup();
    MethodHandle mh = lookup.findVirtual(this.getClass(), "aroundInvokeVirtual", methodType(Object.class, InvocationContext.class));
    Function<TestLambdaMetafactory, Function<InvocationContext, Object>> factory = frobnicate(lookup, mh, TestLambdaMetafactory.class);
    final Function<InvocationContext, Object> f = factory.apply(this);
    // final Function<InvocationContext, Object> f = (Function<InvocationContext, Object>)mh.invokeExact(this);
    assertNotNull(f);
    f.apply(new SimpleInvocationContext());
  }

  @Test
  final void testFrobnicate2() throws Throwable {
    final Lookup lookup = lookup();
    MethodHandle mh = lookup.findVirtual(this.getClass(), "aroundInvokeVirtual", methodType(Object.class, InvocationContext.class));
    final Function<InvocationContext, Object> f = frobnicate2(lookup, mh, TestLambdaMetafactory.class, () -> this);
    f.apply(new SimpleInvocationContext());
  }

  @Test
  final void testFrobnicate3() throws Throwable {
    final Lookup lookup = lookup();
    MethodHandle mh = lookup.findStatic(this.getClass(), "aroundInvokeStatic", methodType(Object.class, InvocationContext.class));
    final Function<InvocationContext, Object> f = frobnicate2(lookup, mh, null, null);
    f.apply(new SimpleInvocationContext());
  }

}
