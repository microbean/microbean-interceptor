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

  /*
    Originally this had two parameters: an Object representing the target, and the InvocationContext. The first
    parameter seems superfluous: if you're generating these things with MethodHandles and whatnot, just bind the
    receiver (or receiver-producing mechanism) up front; no parameter needed.

    Where it gets tricky, it seems to me, is when you have an @AroundInvoke method on a method of the target class.

    Interceptors must be in dependent scope, but presumably that's not true of whatever instance it is that receives
    the @AroundInvoke-annotated method's invocation (it's possible, in other words, I think?, to intercept a business
    method, foo(), on X, with an @AroundInvoke-annotated method on X as well, when X is in, say, Singleton scope).

    So you have the target instance, Target, and its business method, frobnicate(), and an @AroundInvoke method in
    Target (log()).  The idea is to generate an InterceptorMethod object that encapsulates log() in its
    intercept(InvocationContext) method.  But you need a way, now, to invoke log() on Target.

    Obviously if you actually have Target in hand, you can make a MethodHandle that uses bindTo() to bind it to
    Target. Problem solved: intercept(InvocationContext) -> mh.invokeExact() and log() will be called on Target.

    If you don't yet have Target in hand, because you're generating the InterceptorMethod object ahead of time, then the
    InterceptorMethod so generated has to get it out of ic.getTarget(). Which it can only do when
    intercept(InvocationContext) is actually called.

    I guess we could introduce a bind(Object) default method here that by default does nothing, but at least gives the
    possibility of the InterfaceMethod object early-binding the target to a backing MethodHandle.  Or you could just do
    that binding on the first occurrence via InvocationContext.getTarget()...
   */
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
