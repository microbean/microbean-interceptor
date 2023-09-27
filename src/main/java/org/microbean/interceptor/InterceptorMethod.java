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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import java.lang.reflect.Method;

import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.invoke.MethodHandles.lookup;

// TODO: rename/refactor when InterceptorFunction gets slimmed down
@FunctionalInterface
public interface InterceptorMethod {
  
  public Object intercept(final InvocationContext ic) throws Exception;

  public static InterceptorMethod of(final Method staticMethod) {
    return of(staticMethod, null);
  }

  public static InterceptorMethod of(final Method m, final Supplier<?> targetSupplier) {
    MethodHandle mh;
    try {
      mh = MethodHandles.privateLookupIn(m.getDeclaringClass(), lookup()).unreflect(m);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
    return of(mh, targetSupplier);
  }

  public static InterceptorMethod of(final MethodHandle mh) {
    return of(mh, null);
  }

  public static InterceptorMethod of(final MethodHandle mh, final Supplier<?> targetSupplier) {
    final MethodHandle interceptorMethod = mh.asType(mh.type().changeParameterType(0, Object.class));
    final Object returnType = interceptorMethod.type().returnType();
    if (returnType == void.class || returnType == Void.class) {
      if (targetSupplier == null) {
        return ic -> {
          try {
            interceptorMethod.invokeExact(ic);
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
      }
      return ic -> {
        try {
          interceptorMethod.invokeExact(targetSupplier.get(), ic);
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
    } else if (targetSupplier == null) {
      return ic -> {
        try {
          return interceptorMethod.invokeExact(ic);
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
      };
    }
    return ic -> {
      try {
        return interceptorMethod.invokeExact(targetSupplier.get(), ic);
      } catch (final RuntimeException | Error e) {
        throw e;
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e.getMessage(), e);
      } catch (final Throwable e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    };
  }

}
