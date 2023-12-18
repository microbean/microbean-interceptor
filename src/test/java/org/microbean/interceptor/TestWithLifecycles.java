/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2022–2023 microBean™.
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

import java.util.List;

import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestWithLifecycles {

  private TestWithLifecycles() {
    super();
  }

  @Test
  final void testTargetClassInterceptorMethod() throws Exception {
    final Target target = new Target();
    final Supplier<?> targetSupplier = () -> target;
    final InterceptorMethod im = InterceptorMethod.of(Target.class.getMethod("aroundInvokeMethod", InvocationContext.class), targetSupplier);
    final Chain chain = new Chain(List.of(im), targetSupplier, Target.class.getMethod("businessMethod"), null /* no parameters */);
    assertTrue(chain.getTarget() instanceof Target);
    assertNull(chain.call()); // null because businessMethod() returns void
  }

  private static final class Target {

    private Target() {
      super();
    }

    public Object aroundInvokeMethod(final InvocationContext ic) throws Exception {
      System.out.println("Around invoke method");
      return ic.proceed();
    }

    public void businessMethod() {
      System.out.println("Business method");
    }

  }

}
