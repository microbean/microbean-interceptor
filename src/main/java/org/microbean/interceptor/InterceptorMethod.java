/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2025 microBean™.
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
import java.lang.invoke.MethodHandles.Lookup;

import java.lang.reflect.Method;

import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.invoke.MethodHandles.privateLookupIn;

/**
 * A representation of a Jakarta Interceptors <em>interceptor method</em>.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 */
@FunctionalInterface
public interface InterceptorMethod {


  /**
   * Performs interception and returns any result.
   *
   * @param ic an {@link InvocationContext}; must not be {@code null}
   *
   * @return the result of interception, often computed by invoking the {@link InvocationContext#proceed()} method; the
   * return value may be {@code null}
   *
   * @exception Exception if any error occurs
   */
  public Object intercept(final InvocationContext ic) throws Exception;


  /*
   * Public static methods.
   */


  /**
   * Returns a new {@link InterceptorMethod} that adapts the supplied {@code static} {@link Method}.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param staticMethod a {@code static} {@link Method}; must not be {@code null}; must accept exactly one {@link
   * InvocationContext}-typed argument
   *
   * @return a new {@link InterceptorMethod}; never {@code null}
   *
   * @exception NullPointerException if {@code lookup} or {@code staticMethod} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   *
   * @see #of(Lookup, Method, Supplier)
   *
   * @see #of(MethodHandle, Supplier)
   */
  public static InterceptorMethod of(final Lookup lookup, final Method staticMethod)
    throws IllegalAccessException {
    return of(lookup, staticMethod, null);
  }

  /**
   * Returns a new {@link InterceptorMethod} that adapts the supplied {@link Method} and the supplied {@link Supplier}
   * of its receiver.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param m a {@link Method}; must not be {@code null}; must accept exactly one {@link InvocationContext}-typed
   * argument
   *
   * @param targetSupplier a {@link Supplier} of the supplied {@link Method}'s receiver; often memoized; may be {@code
   * null} in which case the supplied {@link Method} must be {@code static}
   *
   * @return a new {@link InterceptorMethod}; never {@code null}
   *
   * @exception NullPointerException if {@code lookup} or {@code m} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   *
   * @see #of(MethodHandle, Supplier)
   */
  public static InterceptorMethod of(final Lookup lookup, final Method m, final Supplier<?> targetSupplier)
    throws IllegalAccessException {
    return of(privateLookupIn(m.getDeclaringClass(), lookup).unreflect(m), targetSupplier);
  }

  /**
   * Returns a new {@link InterceptorMethod} that adapts the supplied receiverless or {@linkplain
   * MethodHandle#bindTo(Object) bound} {@link MethodHandle}.
   *
   * @param receiverlessOrBoundMethodHandle a {@link MethodHandle}; must not be {@code null}; must either not require a
   * receiver or must be already {@linkplain MethodHandle#bindTo(Object) bound} to one; must accept exactly one {@link
   * InvocationContext}-typed argument
   *
   * @return a new {@link InterceptorMethod}; never {@code null}
   *
   * @exception NullPointerException if {@code receiverlessOrBoundMethodHandle} is {@code null}
   *
   * @see #of(MethodHandle, Supplier)
   */
  public static InterceptorMethod of(final MethodHandle receiverlessOrBoundMethodHandle) {
    return of(receiverlessOrBoundMethodHandle, null);
  }

  /**
   * Returns a new {@link InterceptorMethod} that adapts the supplied {@link MethodHandle} and the supplied {@link
   * Supplier} of its receiver.
   *
   * @param mh a {@link MethodHandle}; must not be {@code null}; must either accept two arguments where the first
   * argument's type is a valid receiver type and the second argument's type is {@link InvocationContext}, or one
   * argument whose type is {@link InvocationContext}
   *
   * @param receiverSupplier a {@link Supplier} of the supplied {@link MethodHandle}'s receiver; often memoized; may be
   * {@code null} in which case the supplied {@link MethodHandle} must either not require a receiver or must be already
   * {@linkplain MethodHandle#bindTo(Object) bound} to one
   *
   * @return a new {@link InterceptorMethod}; never {@code null}
   *
   * @exception NullPointerException if {@code m} is {@code null}
   */
  public static InterceptorMethod of(final MethodHandle mh, final Supplier<?> receiverSupplier) {
    final MethodHandle unboundInterceptorMethod;
    final Object returnType = mh.type().returnType();
    if (returnType == void.class || returnType == Void.class) {
      if (receiverSupplier == null) {
        return ic -> invokeExactReturnNull(mh, ic);
      }
      unboundInterceptorMethod = mh.asType(mh.type().changeParameterType(0, Object.class));
      return ic -> invokeExactReturnNull(unboundInterceptorMethod, receiverSupplier, ic);
    }
    unboundInterceptorMethod = mh.asType(mh.type().changeParameterType(0, Object.class));
    return
      receiverSupplier == null ?
      ic -> invokeExact(unboundInterceptorMethod, ic) :
      ic -> invokeExact(unboundInterceptorMethod, receiverSupplier, ic);
  }


  /*
   * Private static methods.
   */


  private static Object invokeExact(final MethodHandle mh, final InvocationContext ic) {
    try {
      return mh.invokeExact(ic);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  private static Object invokeExact(final MethodHandle mh, final Supplier<?> receiverSupplier, final InvocationContext ic) {
    try {
      return mh.invokeExact(receiverSupplier.get(), ic);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  private static Void invokeExactReturnNull(final MethodHandle mh, final InvocationContext ic) {
    try {
      mh.invokeExact(ic);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new InterceptorException(e.getMessage(), e);
    }
    return null;
  }

  private static Void invokeExactReturnNull(final MethodHandle mh, final Supplier<?> receiverSupplier, final InvocationContext ic) {
    try {
      mh.invokeExact(receiverSupplier.get(), ic);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new InterceptorException(e.getMessage(), e);
    }
    return null;
  }

}
