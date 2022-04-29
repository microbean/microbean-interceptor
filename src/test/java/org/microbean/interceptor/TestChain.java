/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2021 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.interceptor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import java.lang.reflect.Method;

import java.util.function.BiFunction;

import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestChain {

  private static boolean aroundConstruct;

  private static boolean construct;

  private static boolean aroundInvoke;

  private static boolean invoke;

  private TestChain() {
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
    ic.proceed();
  }

  private Object aroundInvoke(final InvocationContext ic) throws Exception {
    aroundInvoke = true;
    return ic.proceed();
  }

  @Test
  final void testEmptyChain() throws Exception {
    final Chain chain = new Chain();
    assertNull(chain.call());
    assertFalse(construct);
    assertFalse(aroundConstruct);
    assertFalse(invoke);
    assertFalse(aroundInvoke);
  }

  @Test
  final void testVoidAroundConstruct() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Chain chain =
      new Chain()
      .plusInterceptorFunction(lookup, this.getClass().getDeclaredMethod("voidAroundConstruct", InvocationContext.class), () -> this)
      .sort()
      .withTerminalConstructor(lookup, this.getClass().getDeclaredConstructor())
      .prime();
    assertNull(chain.getTarget());
    chain.call();
    assertTrue(aroundConstruct);
    assertTrue(construct);
    assertNotNull(chain.getTarget());
    assertSame(chain.getTarget(), chain.getTarget());
    assertNotSame(this, chain.getTarget());
  }

  @Test
  final void testAroundInvokeOnFrobnicate() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Chain chain =
      new Chain()
      .plusInterceptorFunction(lookup, this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class), () -> this)
      .sort()
      .withTerminalMethod(lookup, Frobnicator.class.getDeclaredMethod("frobnicate"))
      .withTarget(new Frobnicator())
      .prime();
    assertNotNull(chain.getTarget());
    chain.call();
    assertTrue(aroundInvoke);
    assertTrue(invoke);
    assertNotNull(chain.getTarget());
    assertSame(chain.getTarget(), chain.getTarget());
  }

  @Test
  final void testUninterceptedAdd() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Method add = Frobnicator.class.getDeclaredMethod("add", int.class, int.class);
    final Chain chain =
      new Chain()
      .withTerminalMethod(lookup, add)
      .withTarget(new Frobnicator())
      .withParameters(1, 2);
    assertSame(add, chain.getMethod());
    assertEquals(Integer.valueOf(3), chain.call());
    assertFalse(construct);
    assertFalse(aroundConstruct);
    assertTrue(invoke);
    assertFalse(aroundInvoke);
  }

  @Test
  final void testAroundInvokeOnAdd() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Method add = Frobnicator.class.getDeclaredMethod("add", int.class, int.class);
    final Chain chain =
      new Chain()
      .plusInterceptorFunction(lookup, this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class), () -> this)
      .sort()
      .withTerminalMethod(lookup, add)
      .withTarget(new Frobnicator())
      .withParameters(1, 2)
      .prime();
    assertNotNull(chain.getTarget());
    assertSame(add, chain.getMethod());
    final Object result = chain.call();
    assertEquals(Integer.valueOf(3), result);
    assertTrue(aroundInvoke);
    assertTrue(invoke);
    assertNotNull(chain.getTarget());
    assertSame(chain.getTarget(), chain.getTarget());
  }

  @Test
  final void testAroundInvokeOnRuminate() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Method ruminate = Frobnicator.class.getDeclaredMethod("ruminate", int.class, int.class);
    final Chain chain =
      new Chain()
      .plusInterceptorFunction(lookup, this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class), () -> this)
      .sort()
      .withTerminalMethod(lookup, ruminate)
      .withTarget(new Frobnicator())
      .withParameters(1, 2)
      .prime();
    assertNotNull(chain.getTarget());
    assertSame(ruminate, chain.getMethod());
    final Object result = chain.call();
    assertNull(result);
    assertTrue(aroundInvoke);
    assertTrue(invoke);
    assertNotNull(chain.getTarget());
    assertSame(chain.getTarget(), chain.getTarget());
  }

  @Test
  final void testAroundInvokeOnVoidLambdaize() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Method voidLambdaize = Frobnicator.class.getDeclaredMethod("voidLambdaize", Object[].class);
    final Chain chain =
      new Chain()
      .plusInterceptorFunction(lookup, this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class), () -> this)
      .sort()
      .withTerminalMethod(lookup, voidLambdaize)
      .withTarget(new Frobnicator())
      .withParameters(1, 2)
      .prime();
    assertNotNull(chain.getTarget());
    assertSame(voidLambdaize, chain.getMethod());
    final Object result = chain.call();
    assertNull(result);
    assertTrue(aroundInvoke);
    assertTrue(invoke);
    assertNotNull(chain.getTarget());
    assertSame(chain.getTarget(), chain.getTarget());
  }

  @Test
  final void testAroundInvokeOnLambdaize() throws Exception {
    final Lookup lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
    final Method lambdaize = Frobnicator.class.getDeclaredMethod("lambdaize", Object[].class);
    final Chain chain =
      new Chain()
      .plusInterceptorFunction(lookup, this.getClass().getDeclaredMethod("aroundInvoke", InvocationContext.class), () -> this)
      .sort()
      .withTerminalMethod(lookup, lambdaize)
      .withTarget(new Frobnicator())
      .withParameters(1, 2)
      .prime();
    assertNotNull(chain.getTarget());
    assertSame(lambdaize, chain.getMethod());
    final Object result = chain.call();
    assertEquals(Integer.valueOf(3), result);
    assertTrue(aroundInvoke);
    assertTrue(invoke);
    assertNotNull(chain.getTarget());
    assertSame(chain.getTarget(), chain.getTarget());
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

    public void voidLambdaize(final Object[] parameters) {
      invoke = true;
      assertEquals(Integer.valueOf(1), parameters[0]);
      assertEquals(Integer.valueOf(2), parameters[1]);
    }

    public Integer lambdaize(final Object[] parameters) {
      invoke = true;
      assertEquals(Integer.valueOf(1), parameters[0]);
      assertEquals(Integer.valueOf(2), parameters[1]);
      return Integer.valueOf(((Integer)parameters[0]).intValue() + ((Integer)parameters[1]).intValue());
    }


  }

}
