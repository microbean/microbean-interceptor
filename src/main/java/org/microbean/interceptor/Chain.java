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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.concurrent.atomic.AtomicReference;

import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

import static org.microbean.interceptor.LowLevelOperation.invokeUnchecked;

public class Chain implements Callable<Object>, InvocationContext {

  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private static final Lookup lookup = lookup();

  private final ConcurrentMap<String, Object> contextData;

  private final Supplier<? extends Constructor<?>> constructorSupplier;

  private final Supplier<? extends Method> methodSupplier;

  private final Supplier<?> timerSupplier;

  private final AtomicReference<Object> targetReference;

  private final Supplier<?> targetSupplier;

  private final Supplier<?> proceedImplementation;

  private volatile Object[] parameters;

  @Deprecated
  public Chain() {
    super();
    this.contextData = new ConcurrentHashMap<>();
    this.constructorSupplier = Chain::returnNull;
    this.methodSupplier = Chain::returnNull;
    this.timerSupplier = Chain::returnNull;
    this.targetReference = new AtomicReference<>();
    this.targetSupplier = Chain::returnNull;
    this.proceedImplementation = Chain::returnNull;
    this.parameters = EMPTY_OBJECT_ARRAY;
  }

  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Constructor<?> terminalConstructor) {
    this(interceptorMethods,
         terminalFunctionOf(terminalConstructor),
         true, // set target
         new ConcurrentHashMap<>(),
         () -> terminalConstructor,
         Chain::returnNull, // method supplier
         Chain::returnNull, // targetSupplier (initial target supplier)
         EMPTY_OBJECT_ARRAY,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Constructor<?> terminalConstructor,
               final Object[] parameters) {
    this(interceptorMethods,
         terminalFunctionOf(terminalConstructor),
         true, // set target
         new ConcurrentHashMap<>(),
         () -> terminalConstructor,
         Chain::returnNull, // method supplier
         Chain::returnNull, // targetSupplier (initial target supplier)
         parameters,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Method terminalMethod) {
    this(interceptorMethods,
         terminalFunctionOf(terminalMethod, targetSupplier),
         false, // don't set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         () -> terminalMethod,
         targetSupplier,
         EMPTY_OBJECT_ARRAY,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Method terminalMethod,
               final Object[] parameters) {
    this(interceptorMethods,
         terminalFunctionOf(terminalMethod, targetSupplier),
         false, // don't set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         () -> terminalMethod,
         targetSupplier,
         parameters,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Function<? super Object[], ?> terminalFunction,
               final boolean setTarget, // is the terminal function effectively a constructor?
               final Object[] parameters) {
    this(interceptorMethods,
         terminalFunction,
         setTarget,
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         Chain::returnNull, // method supplier
         targetSupplier,
         parameters,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  private Chain(List<? extends InterceptorMethod> interceptorMethods,
                final Function<? super Object[], ?> terminalFunction,
                final boolean setTarget,
                final ConcurrentMap<String, Object> contextData,
                final Supplier<? extends Constructor<?>> constructorSupplier,
                final Supplier<? extends Method> methodSupplier,
                final Supplier<?> targetSupplier,
                final Object[] parameters,
                final Supplier<?> timerSupplier,
                final AtomicReference<Object> targetReference) {
    super();
    this.contextData = contextData == null ? new ConcurrentHashMap<>() : contextData;
    this.constructorSupplier = constructorSupplier == null ? Chain::returnNull : constructorSupplier;
    this.methodSupplier = methodSupplier == null ? Chain::returnNull : methodSupplier;
    this.parameters = parameters == null ? EMPTY_OBJECT_ARRAY : parameters;
    this.timerSupplier = timerSupplier == null ? Chain::returnNull : timerSupplier;
    this.targetReference = targetReference == null ? new AtomicReference<>() : targetReference;
    this.targetSupplier = targetSupplier == null ? Chain::returnNull : targetSupplier;
    if (interceptorMethods == null || interceptorMethods.isEmpty()) {
      Objects.requireNonNull(terminalFunction, "terminalFunction");
      if (setTarget) {
        this.proceedImplementation = () -> this.targetReference.updateAndGet(v -> terminalFunction.apply(this.getParameters()));
      } else {
        this.proceedImplementation = () -> terminalFunction.apply(this.getParameters());
      }
    } else {
      interceptorMethods = List.copyOf(interceptorMethods);
      final InterceptorMethod im = interceptorMethods.get(0);
      final int size = interceptorMethods.size();
      final List<? extends InterceptorMethod> ims = size == 1 ? List.of() : interceptorMethods.subList(1, size);
      this.proceedImplementation = () -> {
        try {
          return im.intercept(new Chain(ims,
                                        terminalFunction,
                                        setTarget,
                                        this.contextData,
                                        this::getConstructor,
                                        this::getMethod,
                                        this.targetSupplier,
                                        this.parameters,
                                        this::getTimer,
                                        this.targetReference));
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e.getMessage(), e);
        } catch (final Exception e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
      };
    }
  }

  @Override
  public final Constructor<?> getConstructor() {
    return this.constructorSupplier.get();
  }

  @Override
  public final Map<String, Object> getContextData() {
    return this.contextData;
  }

  @Override
  public final Method getMethod() {
    return this.methodSupplier.get();
  }

  @Override
  public final Object[] getParameters() {
    // Cloning etc. is not necessary; this whole API is stupid
    return this.parameters; // volatile read
  }

  @Override
  public final Object getTarget() {
    Object target = this.targetReference.get();
    if (target == null) {
      target = this.targetSupplier.get();
      return target == null || this.targetReference.compareAndSet(null, target) ? target : this.targetReference.get();
    }
    return target;
  }

  public final void setTarget(final Object target) {
    this.targetReference.set(Objects.requireNonNull(target, "target"));
  }

  @Override
  public final Object getTimer() {
    return this.timerSupplier.get();
  }

  @Override // Callable<Object>
  public final Object call() throws Exception {
    return this.proceed();
  }

  @Override
  public Object proceed() throws Exception {
    return this.proceedImplementation.get();
  }

  @Override
  public final void setParameters(final Object[] parameters) {
    // Cloning etc. is not necessary; this whole API is stupid
    this.parameters = parameters == null ? EMPTY_OBJECT_ARRAY : parameters; // volatile write
  }


  /*
   * Static methods.
   */


  public static final Function<Object[], Object> terminalFunctionOf(final Constructor<?> c) {
    try {
      return terminalFunctionOf(privateLookupIn(c.getDeclaringClass(), Chain.lookup).unreflectConstructor(c), null);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public static final Function<Object[], Object> terminalFunctionOf(final Method staticMethod) {
    return terminalFunctionOf(staticMethod, null);
  }

  public static final Function<Object[], Object> terminalFunctionOf(final Method m, final Supplier<?> receiverSupplier) {
    try {
      return terminalFunctionOf(privateLookupIn(m.getDeclaringClass(), Chain.lookup).unreflect(m), receiverSupplier);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public static final Function<Object[], Object> terminalFunctionOf(final MethodHandle receiverlessMethodHandle) {
    return terminalFunctionOf(receiverlessMethodHandle, null);
  }

  public static final Function<Object[], Object> terminalFunctionOf(MethodHandle mh, final Supplier<?> receiverSupplier) {
    mh = mh.asType(mh.type().changeReturnType(Object.class));
    MethodType mt = mh.type();
    final int pc = mt.parameterCount();

    final MethodHandle terminalFunction;

    if (receiverSupplier == null) {
      // Static
      switch (pc) {
      case 0:
        terminalFunction = mh;
        return ps -> invokeUnchecked(() -> terminalFunction.invokeExact());
      default:
        terminalFunction = pc == 1 && mt.parameterType(0) == Object[].class ? mh : mh.asSpreader(Object[].class, pc);
        return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(ps));
      }
    }

    // Virtual
    mh = mh.asType(mt.changeParameterType(0, Object.class));
    mt = mh.type();

    switch (pc) {
    case 1:
      terminalFunction = mh;
      return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(receiverSupplier.get()));
    default:
      terminalFunction = pc == 2 && mt.parameterType(1) == Object[].class ? mh : mh.asSpreader(Object[].class, pc - 1);
      return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(receiverSupplier.get(), ps));
    }
  }

  private static final <T> T returnNull() {
    return null;
  }

}
