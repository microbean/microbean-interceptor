/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2024–2025 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.interceptor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.List;
import java.util.Set;

import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Function;

import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.parallel.Execution;

import static java.lang.invoke.MethodHandles.lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.microbean.interceptor.Interceptions.ofConstruction;
import static org.microbean.interceptor.Interceptions.ofInvocation;

@Execution(SAME_THREAD)
final class TestInterceptions {

  private static final Lookup lookup = lookup();

  private static boolean aroundConstruct;

  private static boolean construct;

  private static boolean aroundInvoke;

  private static boolean invoke;

  private TestInterceptions() {
    super();
    construct = true;
  }

  @BeforeEach
  final void reset() {
    aroundConstruct = false;
    construct = false;
    aroundInvoke = false;
    invoke = false;
  }

  private void voidAroundConstruct(final InvocationContext ic) throws Exception {
    aroundConstruct = true;
    assertNotNull(ic.getParameters());
    assertTrue(ic.getContextData().isEmpty());
    assertNull(ic.getMethod());
    assertNull(ic.getTimer());
    assertNull(ic.getTarget());
    final Constructor<?> c = ic.getConstructor();
    assertNotNull(c);
    assertSame(c, ic.getConstructor());
    final Object target = ic.proceed();
    assertNotNull(target);
    assertSame(target, ic.getTarget());
    assertSame(target, ic.getTarget());
    final Object newTarget = ic.proceed();
    assertNotNull(newTarget);
    assertSame(newTarget, ic.getTarget());
    assertSame(newTarget, ic.getTarget());
    assertNotSame(target, newTarget);
  }

  private Object aroundInvoke(final InvocationContext ic) throws Exception {
    aroundInvoke = true;
    assertNotNull(ic.getParameters());
    assertTrue(ic.getContextData().isEmpty());
    assertNull(ic.getConstructor());
    assertNull(ic.getTimer());
    final Object target = ic.getTarget();
    assertNotNull(target);
    assertSame(target, ic.getTarget());
    final Method m = ic.getMethod();
    assertNotNull(m);
    assertSame(m, ic.getMethod());
    return ic.proceed();
  }

  @Test
  final void testVoidAroundConstruct() throws Exception {
    final List<InterceptorMethod> ims =
      List.of(InterceptorMethod.of(lookup, this.getClass().getDeclaredMethod("voidAroundConstruct", InvocationContext.class),
                                   this::returnThis));
    final InterceptionFunction f = ofConstruction(ims, lookup, this.getClass().getDeclaredConstructor(), Set::of);
    final TestInterceptions t = (TestInterceptions)f.apply((Object[])null); // null arguments
    assertNotNull(t);
    assertNotSame(this, t);
    final TestInterceptions newT = (TestInterceptions)f.apply((Object[])null); // null arguments
    assertNotNull(newT);
    assertNotSame(t, newT);
    assertNotSame(this, newT);

    assertTrue(aroundConstruct);
    assertTrue(construct);
    assertFalse(aroundInvoke);
    assertFalse(invoke);
  }

  @Test
  final void testMethodHandleStuff() throws Throwable {
    final Method m = Frobnicator.class.getDeclaredMethod("frobnicate");
    assertEquals(0, m.getParameterCount());
    MethodHandle unreflectedMh = lookup.unreflect(m);
    assertEquals(1, unreflectedMh.type().parameterCount()); // receiver type
    MethodHandle virtualMh = lookup.findVirtual(Frobnicator.class, "frobnicate", MethodType.methodType(void.class));
    assertEquals(1, virtualMh.type().parameterCount()); // receiver type
  }

  @Test
  final void testAroundInvokeOnFrobnicate() throws Exception {
    final List<InterceptorMethod> ims =
      List.of(InterceptorMethod.of(lookup,
                                   this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class),
                                   this::returnThis));
    final InterceptionFunction f = ofInvocation(ims, lookup, Frobnicator.class.getDeclaredMethod("frobnicate"), Frobnicator::new, Set::of);
    assertNull(f.apply((Object[])null));
    assertTrue(aroundInvoke);
    assertTrue(invoke);
  }

  @Test
  final void testUninterceptedAdd() throws Exception {
    final Method add = Frobnicator.class.getDeclaredMethod("add", int.class, int.class);
    final InterceptionFunction f = ofInvocation(List.of(), lookup, add, Frobnicator::new, Set::of);
    assertEquals(Integer.valueOf(3), f.apply(new Object[] { 1, 2 }));
    assertFalse(construct);
    assertFalse(aroundConstruct);
    assertTrue(invoke);
    assertFalse(aroundInvoke);
  }

  @Test
  final void testAroundInvokeOnAdd() throws Exception {
    final Method add = Frobnicator.class.getDeclaredMethod("add", int.class, int.class);
    final List<InterceptorMethod> ims =
      List.of(InterceptorMethod.of(lookup,
                                   this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class),
                                   this::returnThis));
    final InterceptionFunction f = ofInvocation(ims, lookup, add, Frobnicator::new, Set::of);
    assertEquals(Integer.valueOf(3), f.apply(new Object[] { 1, 2 }));
    assertTrue(aroundInvoke);
    assertTrue(invoke);
  }

  @Test
  final void testAroundInvokeOnRuminate() throws Exception {
    final Method ruminate = Frobnicator.class.getDeclaredMethod("ruminate", int.class, int.class);
    final List<InterceptorMethod> ims =
      List.of(InterceptorMethod.of(lookup,
                                   this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class),
                                   this::returnThis));
    final InterceptionFunction interception = ofInvocation(ims, lookup, ruminate, Frobnicator::new, Set::of);
    assertNull(interception.apply(new Object[] { 1, 2 }));
    assertTrue(aroundInvoke);
    assertTrue(invoke);
  }

  @Test
  final void testAroundInvokeOnVoidCaturgiate() throws Exception {
    final Method voidCaturgiate = Frobnicator.class.getDeclaredMethod("voidCaturgiate", Object[].class);
    final List<InterceptorMethod> ims =
      List.of(InterceptorMethod.of(lookup,
                                   this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class),
                                   this::returnThis));
    final InterceptionFunction interception = ofInvocation(ims, lookup, voidCaturgiate, Frobnicator::new, Set::of);
    assertNull(interception.apply(new Object[] { new Object[] { 1, 2 } }));
    assertTrue(aroundInvoke);
    assertTrue(invoke);
  }

  @Test
  final void testAroundInvokeOnCaturgiate() throws Exception {
    final Method caturgiate = Frobnicator.class.getDeclaredMethod("caturgiate", Object[].class);
    assertEquals(1, caturgiate.getParameterTypes().length);
    final List<InterceptorMethod> ims =
      List.of(InterceptorMethod.of(lookup,
                                   this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class),
                                   this::returnThis));
    final InterceptionFunction interception = ofInvocation(ims, lookup, caturgiate, Frobnicator::new, Set::of);
    assertEquals(Integer.valueOf(3), interception.apply(new Object[] { new Object[] { 1, 2 } }));
    assertTrue(aroundInvoke);
    assertTrue(invoke);
  }

  private final TestInterceptions returnThis() {
    return this;
  }

  private static class Frobnicator {

    private Frobnicator() {
      super();
    }

    public void frobnicate() {
      invoke = true;
    }

    public int add(final int first, final int second) {
      invoke = true;
      assertEquals(1, first);
      assertEquals(2, second);
      return first + second;
    }

    public void ruminate(final int first, final int second) {
      invoke = true;
      assertEquals(1, first);
      assertEquals(2, second);
    }

    public void voidCaturgiate(final Object[] parameters) {
      invoke = true;
      assertEquals(Integer.valueOf(1), parameters[0]);
      assertEquals(Integer.valueOf(2), parameters[1]);
    }

    public Integer caturgiate(final Object[] parameters) {
      invoke = true;
      assertEquals(Integer.valueOf(1), parameters[0]);
      assertEquals(Integer.valueOf(2), parameters[1]);
      return Integer.valueOf(((Integer)parameters[0]).intValue() + ((Integer)parameters[1]).intValue());
    }

  }

}
