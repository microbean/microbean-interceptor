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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import jakarta.inject.Singleton;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestWeldInterception {

  private SeContainer container;

  private TestWeldInterception() {
    super();
  }

  @BeforeEach
  final void startContainer() {
    ExternalInterceptor.count = 0;
    Bean.count = 0;
    this.container = SeContainerInitializer.newInstance()
      .disableDiscovery()
      .addBeanClasses(Bean.class, ExternalInterceptor.class)
      .enableInterceptors(ExternalInterceptor.class)
      .initialize();

  }

  @AfterEach
  final void stopContainer() {
    if (this.container != null) {
      this.container.close();
    }
  }

  @Test
  final void testInterception() {
    final Bean b = this.container.select(Bean.class).get();
    System.out.println("Bean selected");
    b.businessMethod(); // ExternalInterceptor.aroundInvoke(), Bean.aroundInvoke(), Bean.businessMethod()
    assertEquals(1, ExternalInterceptor.count);
    assertEquals(1, Bean.count);
  }

  @Interceptor
  @Verbose
  private static class ExternalInterceptor {

    private static int count;

    ExternalInterceptor() {
      super();
      count++;
    }

    @AroundInvoke
    Object aroundInvokeMethod(final InvocationContext ic) throws Exception {
      System.out.println("ExternalInterceptor.aroundInvokeMethod()");
      return ic.proceed();
    }

  }

  @Singleton
  private static class Bean {

    private static int count;

    Bean() {
      super();
      count++;
    }

    @Verbose
    void businessMethod() {
      System.out.println("Bean.businessMethod()");
    }

    @AroundInvoke
    @Verbose
    Object aroundInvokeMethod(final InvocationContext ic) throws Exception {
      System.out.println("Bean.aroundInvokeMethod()");
      return ic.proceed();
    }

  }

  @InterceptorBinding
  @Retention(RUNTIME)
  @Target({ METHOD, TYPE})
  private @interface Verbose {

  }

}
