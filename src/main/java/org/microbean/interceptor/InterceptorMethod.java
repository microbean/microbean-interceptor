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

import java.lang.invoke.MethodHandle;

import java.lang.reflect.Method;

import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

// TODO: rename/refactor when InterceptorFunction gets slimmed down
@FunctionalInterface
public interface InterceptorMethod {


  public Object intercept(final InvocationContext ic) throws Exception;


  /*
   * Static methods.
   */


  public static InterceptorMethod of(final Method staticMethod) {
    return of(staticMethod, null);
  }

  public static InterceptorMethod of(final Method m, final Supplier<?> targetSupplier) {
    try {
      return of(privateLookupIn(m.getDeclaringClass(), lookup()).unreflect(m), targetSupplier);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public static InterceptorMethod of(final MethodHandle receiverlessOrBoundMethodHandle) {
    return of(receiverlessOrBoundMethodHandle, null);
  }

  public static InterceptorMethod of(final MethodHandle mh, final Supplier<?> receiverSupplier) {
    final Object returnType = mh.type().returnType();
    if (returnType == void.class || returnType == Void.class) {
      if (receiverSupplier == null) {
        return ic -> invokeExact(mh, ic);
      }
      final MethodHandle unboundInterceptorMethod = mh.asType(mh.type().changeParameterType(0, Object.class));
      return ic -> {
        try {
          unboundInterceptorMethod.invokeExact(receiverSupplier.get(), ic);
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
        return null;
      };
    } else if (receiverSupplier == null) {
      return ic -> invokeExact(mh, ic);
    }
    final MethodHandle unboundInterceptorMethod = mh.asType(mh.type().changeParameterType(0, Object.class));
    return ic -> invokeExact(unboundInterceptorMethod, receiverSupplier, ic);
  }

  private static Object invokeExact(final MethodHandle mh, final InvocationContext ic) {
    try {
      return mh.invokeExact(ic);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private static Object invokeExact(final MethodHandle mh, final Supplier<?> receiverSupplier, final InvocationContext ic) {
    try {
      return mh.invokeExact(receiverSupplier.get(), ic);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

}
