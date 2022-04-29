/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2022 microBean™.
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

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicReference;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import org.microbean.development.annotation.Convenience;

import org.microbean.invoke.CachingSupplier;
import org.microbean.invoke.FixedValueSupplier;

/**
 * A {@link Callable} {@link InvocationContext}.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public final class Chain implements Callable<Object>, Cloneable, InvocationContext {

  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private static final VarHandle CONTEXT_DATA;

  static {
    try {
      CONTEXT_DATA = MethodHandles.lookup().findVarHandle(Chain.class, "contextData", Map.class);
    } catch (final ReflectiveOperationException reflectiveOperationException) {
      throw (Error)new ExceptionInInitializerError(reflectiveOperationException.getMessage()).initCause(reflectiveOperationException);
    }
  }


  /*
   * Instance fields.
   */


  private final List<? extends InterceptorFunction> interceptorFunctions;

  private final Map<? extends InterceptorFunction, ? extends Supplier<?>> interceptorSuppliers;

  private final BiFunction<? super Object, ? super Object[], ?> terminalFunction;

  private volatile Map<String, Object> contextData;

  private final AtomicReference<Object> target;

  private volatile Supplier<? extends Object[]> parameters;

  private final CachingSupplier<? extends Constructor<?>> constructorSupplier;

  private final CachingSupplier<? extends Method> methodSupplier;

  private final CachingSupplier<?> timerSupplier;

  private final boolean setTarget;

  private volatile boolean primed;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link Chain}.
   */
  public Chain() {
    this(List.of(),
         false, // sort
         false, // copy
         null, // interceptorSuppliers
         null, // contextData
         new AtomicReference<>(), // target
         null, // parameters
         null, // constructorSupplier,
         null, // methodSupplier,
         null, // timerSupplier
         null, // terminalFunction
         false,
         false);
  }

  private Chain(final List<? extends InterceptorFunction> interceptorFunctions,
                final boolean sort,
                final boolean copy,
                final IdentityHashMap<? extends InterceptorFunction, ? extends Supplier<?>> interceptorSuppliers, // not copied!
                final Map<String, Object> contextData,
                final AtomicReference<Object> target,
                final Supplier<? extends Object[]> parameters,
                final Supplier<? extends Constructor<?>> constructorSupplier,
                final Supplier<? extends Method> methodSupplier,
                final Supplier<?> timerSupplier,
                final BiFunction<? super Object, ? super Object[], ?> terminalFunction,
                final boolean setTarget,
                final boolean primed) {
    super();
    if (interceptorFunctions == null || interceptorFunctions.isEmpty()) {
      this.interceptorFunctions = List.of();
    } else if (sort) {
      // copy is implied
      final List<InterceptorFunction> unsortedInterceptorFunctions = new ArrayList<>(interceptorFunctions.size());
      unsortedInterceptorFunctions.addAll(interceptorFunctions);
      Collections.sort(unsortedInterceptorFunctions, Prioritized.Comparator.INSTANCE);
      this.interceptorFunctions = Collections.unmodifiableList(unsortedInterceptorFunctions);
    } else if (copy) {
      this.interceptorFunctions = List.copyOf(interceptorFunctions);
    } else {
      this.interceptorFunctions = interceptorFunctions;
    }
    // Note to future maintainers: don't add isEmpty() check
    this.interceptorSuppliers = interceptorSuppliers == null ? Map.of() : interceptorSuppliers;
    this.contextData = contextData;
    this.target = target == null ? new AtomicReference<>() : target;
    this.parameters = parameters;
    if (constructorSupplier == null) {
      this.constructorSupplier = new CachingSupplier<>(Chain::returnNull);
    } else if (constructorSupplier instanceof CachingSupplier<? extends Constructor<?>> cachingSupplier) {
      this.constructorSupplier = cachingSupplier;
    } else {
      this.constructorSupplier = new CachingSupplier<>(constructorSupplier);
    }
    if (methodSupplier == null) {
      this.methodSupplier = new CachingSupplier<>(Chain::returnNull);
    } else if (methodSupplier instanceof CachingSupplier<? extends Method> cachingSupplier) {
      this.methodSupplier = cachingSupplier;
    } else {
      this.methodSupplier = new CachingSupplier<>(methodSupplier);
    }
    if (timerSupplier == null) {
      this.timerSupplier = new CachingSupplier<>(Chain::returnNull);
    } else if (timerSupplier instanceof CachingSupplier<?> cachingSupplier) {
      this.timerSupplier = cachingSupplier;
    } else {
      this.timerSupplier = new CachingSupplier<>(timerSupplier);
    }
    if (terminalFunction == null) {
      this.terminalFunction = NullTerminalFunction.INSTANCE;
    } else {
      this.terminalFunction = terminalFunction;
    }
    this.setTarget = setTarget;
    this.primed = primed;
  }


  /*
   * Instance methods.
   */


  /*
   * Builder-like methods.
   */


  /**
   * Returns a {@link Chain} whose internal list of {@link
   * InterceptorFunction}s is sorted by {@linkplain
   * Prioritized#priority() priority}, where the smallest number
   * "wins".
   *
   * @return a {@link Chain}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent but not necessarily
   * deterministic, since two {@link InterceptorFunction}s with the
   * same {@linkplain Prioritized#priority() priority} may sort to
   * different indices in the relevant list.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public final Chain sort() {
    return this.interceptorFunctions.size() <= 1 ? this : this.copy(this.interceptorFunctions, true);
  }

  /**
   * Returns this {@link Chain} with its {@linkplain #getTarget()
   * target} already set to the supplied {@code target}.
   *
   * @param target the new target; may be {@code null}
   *
   * @return this {@link Chain}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   *
   * @see #getTarget()
   */
  public final Chain withTarget(final Object target) {
    this.target.set(target);
    return this;
  }
  
  /**
   * Calls {@link #setParameters(Object[])} with the supplied {@code
   * parameters} and returns this {@link Chain}.
   *
   * @param parameters the parameters; may be {@code null}
   *
   * @return this {@link Chain}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   *
   * @see #setParameters(Object[])
   */
  @Convenience
  public final Chain withParameters(final Object... parameters) {
    this.setParameters(parameters);
    return this;
  }

  /**
   * Calls {@link #setParameters(Supplier)} with the supplied {@code
   * parametersSupplier} and returns this {@link Chain}.
   *
   * @param parametersSupplier a {@link Supplier} supplying
   * parameters; may be {@code null}
   *
   * @return this {@link Chain}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   *
   * @see #setParameters(Supplier)
   */
  @Convenience
  public final Chain withParameters(final Supplier<? extends Object[]> parametersSupplier) {
    this.setParameters(parametersSupplier);
    return this;
  }

  public final Chain plusInterceptorFunction(final Lookup lookup, final Method method) {
    // Don't get clever and pass this::getTarget instead of null as
    // the trailing parameter.
    return this.plusInterceptorFunction(lookup, method, null);
  }

  public final Chain plusInterceptorFunction(final Lookup lookup, final Method method, final Supplier<?> interceptorSupplier) {
    return this.plusInterceptorFunction(interceptorFunction(lookup, method), interceptorSupplier);
  }

  public final Chain plusInterceptorFunction(final InterceptorFunction function) {
    // Don't get clever and pass this::getTarget instead of null as
    // the trailing parameter.
    return this.plusInterceptorFunction(function, null);
  }

  /**
   * Returns a {@link Chain} that reflects the addition of the
   * supplied {@link InterceptorFunction}.
   *
   * @param function the {@link InterceptorFunction}; may be {@code
   * null} in which case this {@link Chain} will be returned
   *
   * @param interceptorSupplier a {@link Supplier} of an interceptor;
   * may be {@code null} in which case an appropriate representation
   * of the {@link #getTarget()} method will be used instead
   *
   * @return a {@link Chain} that reflects the addition of the
   * supplied {@link InterceptorFunction}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public final Chain plusInterceptorFunction(final InterceptorFunction function, final Supplier<?> interceptorSupplier) {
    if (function == null) {
      return this;
    }
    final List<InterceptorFunction> unsortedInterceptorFunctions = new ArrayList<>(this.interceptorFunctions.size() + 1);
    unsortedInterceptorFunctions.addAll(this.interceptorFunctions);
    unsortedInterceptorFunctions.add(function);
    final IdentityHashMap<InterceptorFunction, Supplier<?>> map = new IdentityHashMap<>(this.interceptorSuppliers);
    final Supplier<?> thisGetTarget = this::getTarget;
    if (interceptorSupplier == null || interceptorSupplier == thisGetTarget) {
      map.put(function, thisGetTarget);
    } else if (interceptorSupplier instanceof CachingSupplier<?> cs) {
      map.put(function, cs);
    } else {
      map.put(function, new CachingSupplier<>(interceptorSupplier));
    }
    return this.copy(unsortedInterceptorFunctions, false, map);
  }

  
  public final Chain withTerminalConstructor(final Lookup lookup, final Constructor<?> constructor) {
    constructor.trySetAccessible();
    try {
      final Function<Object[], Object> terminalConstructor = terminalConstructor(lookup, lookup.unreflectConstructor(constructor));
      return
        this.withTerminalFunction((ignoredTarget, parameters) -> terminalConstructor.apply(parameters),
                                  true,
                                  () -> constructor,
                                  null,
                                  null);
    } catch (final IllegalAccessException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public final Chain withTerminalConstructor(final Lookup lookup, final Method factoryMethod) {
    factoryMethod.trySetAccessible();
    try {
      final Function<Object[], Object> terminalConstructor = terminalConstructor(lookup, lookup.unreflect(factoryMethod));
      return
        this.withTerminalFunction((ignoredTarget, parameters) -> terminalConstructor.apply(parameters),
                                  true,
                                  null,
                                  () -> factoryMethod,
                                  null);
    } catch (final IllegalAccessException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public final Chain withTerminalConstructor(final Lookup lookup, final MethodHandle mh) {
    try {
      return
        this.withTerminalConstructor(terminalConstructor(lookup, mh),
                                     () -> MethodHandles.reflectAs(Constructor.class, mh));
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final Exception e) {
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new AssertionError(e.getMessage(), e);
    }
  }

  public final Chain withTerminalConstructor(final Function<? super Object[], ?> constructor) {
    return this.withTerminalConstructor(constructor, null);
  }

  public final Chain withTerminalConstructor(final Function<? super Object[], ?> constructor,
                                             final Supplier<? extends Constructor<?>> constructorSupplier) {
    return
      this.withTerminalFunction((ignoredTarget, parameters) -> constructor.apply(parameters),
                                true,
                                constructorSupplier,
                                null,
                                null);
  }

  public final Chain withTerminalMethod(final Lookup lookup, final Method method) {
    method.trySetAccessible();
    try {
      return
        this.withTerminalFunction(terminalFunction(lookup, lookup.unreflect(method)),
                                  false,
                                  null,
                                  () -> method,
                                  null);
    } catch (final IllegalAccessException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public final Chain withTerminalMethod(final Lookup lookup, final MethodHandle mh) {
    try {
      return
        this.withTerminalFunction(terminalFunction(lookup, mh),
                                  false,
                                  null,
                                  () -> MethodHandles.reflectAs(Method.class, mh),
                                  null);
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final Exception e) {
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new AssertionError(e.getMessage(), e);
    }
  }

  public final Chain withTerminalConsumer(final Consumer<? super Object> terminalConsumer) {
    return this.withTerminalConsumer(terminalConsumer, null);
  }

  public final Chain withTerminalConsumer(final Consumer<? super Object> terminalConsumer,
                                          final Supplier<? extends Method> methodSupplier) {
    return
      this.withTerminalFunction((target, ignoredParameters) -> {
          terminalConsumer.accept(target);
          return null;
        },
        false,
        null,
        methodSupplier,
        this::getTimer);
  }

  private final Chain withTerminalFunction(final Function<? super Object[], ?> selfContainedTerminalFunction) {
    return this.withTerminalFunction((ignoredTarget, parameters) -> selfContainedTerminalFunction.apply(parameters), null);
  }

  public final Chain withTerminalFunction(final BiFunction<? super Object, ? super Object[], ?> terminalFunction) {
    return this.withTerminalFunction(terminalFunction, null);
  }

  public final Chain withTerminalFunction(final BiFunction<? super Object, ? super Object[], ?> terminalFunction,
                                          final Supplier<? extends Method> methodSupplier) {
    return
      this.withTerminalFunction(terminalFunction,
                                false,
                                null,
                                methodSupplier,
                                this::getTimer);
  }

  private final Chain withTerminalFunction(final BiFunction<? super Object, ? super Object[], ?> terminalFunction,
                                           final boolean setTarget,
                                           Supplier<? extends Constructor<?>> constructorSupplier,
                                           Supplier<? extends Method> methodSupplier,
                                           Supplier<?> timerSupplier) {
    if (constructorSupplier == null) {
      if (methodSupplier == null) {
        constructorSupplier = this::getConstructor;
        methodSupplier = this::getMethod;
      }
    } else if (methodSupplier != null || timerSupplier != null) {
      throw new IllegalArgumentException("Can't have both a constructor supplier and either a method supplier or a timer supplier");
    }
    return
      this.copy(0,
                constructorSupplier,
                methodSupplier,
                timerSupplier == null ? this::getTimer : timerSupplier,
                terminalFunction,
                setTarget);
  }

  public final Chain withTimerSupplier(final Supplier<?> timerSupplier) {
    return
      this.copy(0,
                null, // constructor supplier
                this::getMethod,
                timerSupplier == null ? this::getTimer : timerSupplier,
                this.terminalFunction,
                this.setTarget);
  }



  /*
   * Non-"builder" instance methods.
   */


  public final int size() {
    return this.interceptorFunctions.size() + (this.isTerminated() ? 1 : 0);
  }

  public final boolean isEmpty() {
    return !(this.isTerminated() || this.intercepts());
  }

  public final boolean isTerminated() {
    return this.terminalFunction != NullTerminalFunction.INSTANCE;
  }

  public final boolean intercepts() {
    return !this.interceptorFunctions.isEmpty();
  }

  @Override // InvocationContext
  public final Constructor<?> getConstructor() {
    return this.constructorSupplier.get();
  }

  @Override // InvocationContext
  public final Method getMethod() {
    return this.methodSupplier.get();
  }

  @Override // InvocationContext
  public final Object getTimer() {
    return this.timerSupplier.get();
  }

  @Override // InvocationContext
  public final Map<String, Object> getContextData() {
    Map<String, Object> map = this.contextData; // volatile read
    if (map == null) {
      map = new ConcurrentHashMap<>();
      if (!CONTEXT_DATA.compareAndSet(this, null, map)) { // volatile write
        map = this.contextData; // volatile read
      }
    }
    return map;
  }

  @Override // InvocationContext
  public final Object[] getParameters() {
    final Supplier<? extends Object[]> supplier = this.parameters; // volatile read
    if (supplier == null) {
      return EMPTY_OBJECT_ARRAY;
    } else {
      Object[] returnValue = supplier.get();
      if (returnValue == null || returnValue.length <= 0) {
        returnValue = EMPTY_OBJECT_ARRAY;
      } else {
        returnValue = returnValue.clone();
      }
      return returnValue;
    }
  }

  @Override // InvocationContext
  public final void setParameters(final Object[] parameters) {
    if (parameters == null || parameters.length == 0) {
      this.parameters = Chain::emptyObjectArray; // volatile write
    } else {
      final Object[] clonedParameters = parameters.clone();
      this.parameters = () -> clonedParameters; // volatile write
    }
  }

  public final void setParameters(final Supplier<? extends Object[]> parameters) {
    if (parameters == null) {
      this.parameters = Chain::emptyObjectArray; // volatile write
    } else {
      this.parameters = parameters; // volatile write
    }
  }

  @Override // InvocationContext
  public final Object getTarget() {
    return this.target.get();
  }

  @Override
  public final Chain clone() {
    return this.copy(0);
  }

  private final Chain copy(final int index) {
    return this.copy(index,
                     this::getConstructor,
                     this::getMethod,
                     this::getTimer,
                     this.terminalFunction,
                     this.setTarget);
  }

  private final Chain copy(final int index,
                           final Supplier<? extends Constructor<?>> constructorSupplier,
                           final Supplier<? extends Method> methodSupplier,
                           final Supplier<?> timerSupplier,
                           final BiFunction<? super Object, ? super Object[], ?> terminalFunction,
                           final boolean setTarget) {
    final List<? extends InterceptorFunction> interceptorFunctions;
    // Defensive copying of this map is deliberately not handled by
    // the constructor so we do it here.
    final IdentityHashMap<? extends InterceptorFunction, ? extends Supplier<?>> interceptorSuppliers =
      new IdentityHashMap<>(this.interceptorSuppliers);
    final int functionCount = this.interceptorFunctions.size();
    if (index <= 0 || functionCount <= 0) {
      interceptorFunctions = this.interceptorFunctions;
    } else if (index >= functionCount) {
      interceptorFunctions = List.of();
    } else {
      interceptorFunctions = this.interceptorFunctions.subList(index, functionCount);
      for (int i = 0; i < index; i++) {
        interceptorSuppliers.remove(this.interceptorFunctions.get(i));
      }
    }
    return
      new Chain(interceptorFunctions,
                false, // sort
                false, // copy
                interceptorSuppliers,
                this.contextData,
                this.target,
                this.parameters,
                constructorSupplier == null ? this::getConstructor : constructorSupplier,
                methodSupplier == null ? this::getMethod : methodSupplier,
                timerSupplier == null ? this::getTimer : timerSupplier,
                terminalFunction,
                setTarget,
                this.isPrimed());
  }

  private final Chain copy(final List<? extends InterceptorFunction> interceptorFunctions,
                           final boolean sort) {
    return this.copy(interceptorFunctions, sort, new IdentityHashMap<>(this.interceptorSuppliers));
  }

  private final Chain copy(final List<? extends InterceptorFunction> interceptorFunctions,
                           final boolean sort,
                           final IdentityHashMap<? extends InterceptorFunction, ? extends Supplier<?>> interceptorSuppliers) {
    final Chain returnValue =
      new Chain(interceptorFunctions,
                sort,
                false, // copy
                interceptorSuppliers,
                this.contextData,
                this.target,
                this.parameters,
                this::getConstructor,
                this::getMethod,
                this::getTimer,
                this.terminalFunction,
                this.setTarget,
                this.isPrimed());
    returnValue.parameters = this.parameters; // volatile read and write
    return returnValue;
  }

  public final Chain prime() {
    final boolean primed = this.primed; // volatile read
    if (!primed) {
      this.interceptorSuppliers.forEach(Chain::prime);
      this.primed = true; // volatile write
    }
    return this;
  }

  public final boolean isPrimed() {
    return this.primed; // volatile read
  }

  /**
   * Calls the {@link #proceed()} method and returns its result.
   *
   * @return the result of invoking the {@link #proceed()} method
   *
   * @exception Exception if an error occurs
   *
   * @see #proceed()
   */
  @Override
  public final Object call() throws Exception {
    return this.proceed();
  }

  @Override
  public final Object proceed() throws Exception {
    final Object returnValue;
    if (this.interceptorFunctions.isEmpty()) {
      returnValue = this.terminalFunction.apply(this.getTarget(), this.getParameters());
      if (this.setTarget) {
        this.target.set(returnValue);
      }
    } else {
      final Chain chain = this.copy(1);
      assert this.getTarget() == chain.getTarget();
      final InterceptorFunction interceptorFunction = this.interceptorFunctions.get(0);
      final Supplier<?> interceptorSupplier = this.interceptorSuppliers.get(interceptorFunction);
      if (interceptorSupplier == null) {
        returnValue = interceptorFunction.intercept(chain.getTarget(), chain);
      } else {
        returnValue = interceptorFunction.intercept(interceptorSupplier.get(), chain);
      }
    }
    return returnValue;
  }


  /*
   * Static methods.
   */


  /**
   * Calls {@link Supplier#get()} on the supplied {@link Supplier} to
   * "prime" it.
   *
   * @param ignored ignored; required to make this method look like a
   * {@link BiConsumer}
   *
   * @param supplier the {@link Supplier} to prime; must not be {@code
   * null}
   *
   * @exception NullPointerException if {@code supplier} is {@code
   * null}
   */
  private static final void prime(final Object ignored, final Supplier<?> supplier) {
    supplier.get();
  }

  private static final Object[] emptyObjectArray() {
    return EMPTY_OBJECT_ARRAY;
  }

  private static final <T> T returnNull() {
    return null;
  }

  /**
   * Invokes {@link MethodHandles#reflectAs(Class, MethodHandle)} with
   * {@link Constructor Constructor.class} and the supplied {@link
   * MethodHandle} as arguments, and returns the result.
   *
   * @param mh the {@link MethodHandle} representing a constructor;
   * must not be {@code null}
   *
   * @return a {@link Constructor} representing the supplied {@link
   * MethodHandle}
   *
   * @exception ClassCastException if {@code mh} does not represent a
   * constructor
   *
   * @exception IllegalArgumentException if {@code mh} is not a direct
   * method handle
   *
   * @exception NullPointerException if {@code mh} is {@code null}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  @Convenience
  public static final Constructor<?> constructor(final MethodHandle mh) {
    return MethodHandles.reflectAs(Constructor.class, mh);
  }

  /**
   * Invokes {@link MethodHandles#reflectAs(Class, MethodHandle)} with
   * {@link Method Method.class} and the supplied {@link MethodHandle}
   * as arguments, and returns the result.
   *
   * @param mh the {@link MethodHandle} representing a method; must
   * not be {@code null}
   *
   * @return a {@link Method} representing the supplied {@link
   * MethodHandle}
   *
   * @exception ClassCastException if {@code mh} does not represent a
   * method
   *
   * @exception IllegalArgumentException if {@code mh} is not a direct
   * method handle
   *
   * @exception NullPointerException if {@code mh} is {@code null}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  @Convenience
  public static final Method method(final MethodHandle mh) {
    return MethodHandles.reflectAs(Method.class, mh);
  }

  /**
   * Returns an {@link InterceptorFunction} representing the method
   * designated by the supplied arguments.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param targetClass the {@link Class} hosting the method; must not
   * be {@code null}
   *
   * @param methodName the name of the method; must not be {@code
   * null}
   *
   * @return an {@link InterceptorFunction} representing the method
   * designated by the supplied arguments
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception InterceptorException if an error occurs while looking
   * up the relevant method
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final InterceptorFunction interceptorFunction(final Lookup lookup,
                                                              final Class<?> targetClass,
                                                              final String methodName) {
    try {
      return
        interceptorFunction(lookup,
                            lookup.findVirtual(targetClass,
                                               methodName,
                                               MethodType.methodType(Object.class,
                                                                     InvocationContext.class)));
    } catch (final IllegalAccessException | NoSuchMethodException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  /**
   * Returns an {@link InterceptorFunction} representing the method
   * designated by the supplied arguments.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param method a {@link Method}; must not be {@code null}
   *
   * @return an {@link InterceptorFunction} representing the method
   * designated by the supplied arguments
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception InterceptorException if an error occurs while
   * {@linkplain Lookup#unreflect(Method) unreflecting} the relevant
   * method
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final InterceptorFunction interceptorFunction(final Lookup lookup, final Method method) {
    try {
      return interceptorFunction(lookup, lookup.unreflect(method));
    } catch (final IllegalAccessException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  /**
   * Returns an {@link InterceptorFunction} representing the method
   * designated by the supplied arguments.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param mh a {@link MethodHandle}; must not be null}
   *
   * @return an {@link InterceptorFunction} representing the method
   * designated by the supplied arguments
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception InterceptorException if an error occurs while looking
   * up the relevant method
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final InterceptorFunction interceptorFunction(final Lookup lookup, final MethodHandle mh) {
    final MethodType methodType = mh.type();
    final String methodName;
    final MethodType signature;
    final MethodType factoryType;
    final boolean invokeExact;
    if (void.class.equals(methodType.returnType())) {
      methodName = "execute";
      factoryType = MethodType.methodType(InterceptorProcedure.class);
      signature = MethodType.methodType(void.class, List.of(Object.class, InvocationContext.class));
    } else {
      methodName = "intercept";
      factoryType = MethodType.methodType(InterceptorFunction.class);
      signature = MethodType.methodType(Object.class, List.of(Object.class, InvocationContext.class));
    }
    try {
      final MethodHandle factory =
        LambdaMetafactory.metafactory(lookup,
                                      methodName,
                                      factoryType,
                                      signature,
                                      mh, // better conform to signature
                                      methodType) // signature enforced dynamically at runtime
        .getTarget();
      if (InterceptorFunction.class.equals(factoryType.returnType())) {
        return (InterceptorFunction)factory.invokeExact();
      } else {
        return (InterceptorProcedure)factory.invokeExact();
      }
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final Exception e) {
      throw new InterceptorException(e.getMessage(), e);
    } catch (final Throwable e) {
      throw new AssertionError(e.getMessage(), e);
    }
  }

  public static final BiFunction<Object, Object[], Object> terminalFunction(final Lookup lookup,
                                                                            final Class<?> targetClass,
                                                                            final String methodName,
                                                                            final MethodType methodType) {
    try {
      return
        terminalFunction(lookup,
                         lookup.findVirtual(targetClass,
                                            methodName,
                                            methodType));
    } catch (final IllegalAccessException | NoSuchMethodException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public static final BiFunction<Object, Object[], Object> terminalFunction(final Class<?> targetClass, MethodHandle mh) {
    try {
      return terminalFunction(MethodHandles.privateLookupIn(targetClass, MethodHandles.lookup()), mh);
    } catch (final IllegalAccessException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public static final BiFunction<Object, Object[], Object> terminalFunction(final Lookup lookup, MethodHandle mh) {
    final MethodType methodType = mh.type();
    final int parameterCount = methodType.parameterCount();
    BiFunction<Object, Object[], Object> returnValue = null;
    if (parameterCount <= 0) {
      throw new IllegalArgumentException("mh.methodType().parameterCount() <= 0: " + mh);
    } else if (parameterCount == 1) {
      // The sole parameter is assumed to be the receiver type, so the
      // virtual method the handle represents has no parameters.
      if (void.class.equals(methodType.returnType())) {
        // The virtual method the handle represents has no parameters
        // and returns nothing, so is like a Runnable.  We have to use
        // Consumer because of the sole receiver parameter.
        final MethodType consumerAcceptSignature = MethodType.methodType(void.class, List.of(Object.class));
        try {
          final Consumer<? super Object> consumer =
            (Consumer<? super Object>)LambdaMetafactory.metafactory(lookup,
                                                                    "accept",
                                                                    MethodType.methodType(Consumer.class),
                                                                    consumerAcceptSignature,
                                                                    mh,
                                                                    methodType)
            .getTarget()
            .invokeExact();
          returnValue = (target, parameters) -> {
            consumer.accept(target);
            return null;
          };
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final Exception e) {
          throw new InterceptorException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new AssertionError(e.getMessage(), e);
        }
      } else {
        // The virtual method the handle represents has no parameters
        // and returns something, so is like a Supplier<?>.  We have
        // to use Function<? super Object, ?> because of the receiver
        // parameter.
        final MethodType functionApplySignature = MethodType.methodType(Object.class, List.of(Object.class));
        try {
          final Function<? super Object, ?> function =
            (Function<? super Object, ?>)LambdaMetafactory.metafactory(lookup,
                                                                       "apply",
                                                                       MethodType.methodType(Function.class),
                                                                       functionApplySignature,
                                                                       mh,
                                                                       methodType)
            .getTarget()
            .invokeExact();
          returnValue = (target, parameters) -> function.apply(target);
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final Exception e) {
          throw new InterceptorException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new AssertionError(e.getMessage(), e);
        }
      }
    } else if (parameterCount == 2 && Object[].class.equals(methodType.parameterType(1))) {
      // The virtual method the handle represents has one parameter of
      // type Object[].class.  The first of the two parameters is the
      // receiver type.
      if (void.class.equals(methodType.returnType())) {
        // The virtual method the handle represents takes one
        // parameter of type Object[].class and returns nothing, so is
        // like a Consumer<? super Object[]>.  We have to use
        // BiConsumer<? super Object, ? super Object[]> because of the
        // receiver parameter.
        try {
          final MethodType biConsumerAcceptSignature = MethodType.methodType(void.class, List.of(Object.class, Object.class));
          final BiConsumer<? super Object, ? super Object[]> biConsumer =
            (BiConsumer<? super Object, ? super Object[]>)LambdaMetafactory.metafactory(lookup,
                                                                                        "accept",
                                                                                        MethodType.methodType(BiConsumer.class), // returns BiConsumer and captures no variables
                                                                                        biConsumerAcceptSignature, // compiled/erased signature of BiConsumer::accept
                                                                                        mh, // better conform to signature
                                                                                        methodType) // signature enforced dynamically at runtime (same as compiled/erased in this case)
            .getTarget()
            .invokeExact();
          returnValue = (target, parameters) -> {
            biConsumer.accept(target, parameters);
            return null;
          };
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final Exception e) {
          throw new InterceptorException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new AssertionError(e.getMessage(), e);
        }
      } else {
        // The virtual method the handle represents takes one
        // parameter of type Object[].class and returns something, so
        // is like a Function<? super Object[], ?>.  We have to use
        // BiFunction<? super Object, ? super Object[], ?> because of
        // the receiver parameter.
        try {
          final MethodType biFunctionApplySignature = MethodType.methodType(Object.class, List.of(Object.class, Object.class));
          returnValue =
            (BiFunction<Object, Object[], Object>)LambdaMetafactory.metafactory(lookup,
                                                                                "apply",
                                                                                MethodType.methodType(BiFunction.class), // returns BiFunction and captures no variables
                                                                                biFunctionApplySignature, // compiled/erased signature of BiFunction::apply
                                                                                mh, // better conform to signature
                                                                                methodType) // signature enforced dynamically at runtime
            .getTarget()
            .invokeExact();
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final Exception e) {
          throw new InterceptorException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new AssertionError(e.getMessage(), e);
        }
      }
    } else {
      final MethodHandle spreader = mh.asSpreader(Object[].class, parameterCount - 1);
      returnValue = (target, parameters) -> {
        try {
          return spreader.invoke(target, parameters);
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final Exception e) {
          throw new InterceptorException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new AssertionError(e.getMessage(), e);
        }
      };
    }
    return returnValue;
  }

  public static final Function<Object[], Object> terminalConstructor(final Lookup lookup,
                                                                     final Class<?> targetClass,
                                                                     final List<? extends Class<?>> parameters) {
    return
      terminalConstructor(lookup,
                          targetClass,
                          MethodType.methodType(void.class,
                                                parameters == null || parameters.isEmpty() ? List.of() : List.copyOf(parameters)));
  }

  public static final Function<Object[], Object> terminalConstructor(final Lookup lookup,
                                                                     final Class<?> targetClass,
                                                                     final MethodType methodTypeWithVoidReturnType) {
    try {
      return
        terminalConstructor(lookup,
                            lookup.findConstructor(targetClass, methodTypeWithVoidReturnType));
    } catch (final IllegalAccessException | NoSuchMethodException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public static final Function<Object[], Object> terminalConstructor(final Lookup lookup,
                                                                     final Class<?> targetClass,
                                                                     final String factoryMethodName,
                                                                     final MethodType methodType) {
    try {
      return
        terminalConstructor(lookup,
                            lookup.findVirtual(targetClass, factoryMethodName, methodType));
    } catch (final IllegalAccessException | NoSuchMethodException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public static final Function<Object[], Object> terminalConstructor(final Lookup lookup, final Constructor<?> constructor) {
    try {
      return terminalConstructor(lookup, lookup.unreflectConstructor(constructor));
    } catch (final IllegalAccessException e) {
      throw new InterceptorException(e.getMessage(), e);
    }
  }

  public static final Function<Object[], Object> terminalConstructor(final Lookup lookup, final MethodHandle mh) {
    final MethodType methodType = mh.type();
    if (void.class.equals(methodType.returnType()) || Void.class.equals(methodType.returnType())) {
      throw new IllegalArgumentException("mh.methodType().returnType().equals(void.class) || " +
                                         "mh.methodType().returnType().equals(Void.class): " + mh);
    }
    final int parameterCount = methodType.parameterCount();
    Function<Object[], Object> returnValue = null;
    if (parameterCount < 0) {
      throw new IllegalArgumentException("mh.methodType().parameterCount() <= 0: " + mh);
    } else if (parameterCount == 0) {
      final MethodType supplierGetSignature = MethodType.methodType(Object.class);
      try {
        final Supplier<?> supplier =
          (Supplier<?>)LambdaMetafactory.metafactory(lookup,
                                                     "get",
                                                     MethodType.methodType(Supplier.class),
                                                     supplierGetSignature,
                                                     mh,
                                                     methodType)
          .getTarget()
          .invokeExact();
        returnValue = parameters -> supplier.get();
      } catch (final RuntimeException | Error e) {
        throw e;
      } catch (final Exception e) {
        throw new InterceptorException(e.getMessage(), e);
      } catch (final Throwable e) {
        throw new AssertionError(e.getMessage(), e);
      }
    } else if (parameterCount == 1 && Object[].class.equals(methodType.parameterType(0))) {
      final MethodType functionApplySignature = MethodType.methodType(Object.class, List.of(Object.class));
      try {
        returnValue =
          (Function<Object[], Object>)LambdaMetafactory.metafactory(lookup,
                                                                    "apply",
                                                                    MethodType.methodType(Function.class),
                                                                    functionApplySignature,
                                                                    mh,
                                                                    methodType)
          .getTarget()
          .invokeExact();
      } catch (final RuntimeException | Error e) {
        throw e;
      } catch (final Exception e) {
        throw new InterceptorException(e.getMessage(), e);
      } catch (final Throwable e) {
        throw new AssertionError(e.getMessage(), e);
      }
    } else {
      final MethodHandle spreader = mh.asSpreader(Object[].class, parameterCount);
      returnValue = parameters -> {
        try {
          return spreader.invoke(parameters);
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final Exception e) {
          throw new InterceptorException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new AssertionError(e.getMessage(), e);
        }
      };
    }
    return returnValue;
  }


  /*
   * Inner and nested classes.
   */


  private static final class NullTerminalFunction implements BiFunction<Object, Object[], Object> {

    private static final BiFunction<Object, Object[], Object> INSTANCE = new NullTerminalFunction();

    private NullTerminalFunction() {
      super();
    }

    @Override // BiFunction
    public final Object apply(final Object target, final Object[] parameters) {
      return null;
    }

  }

  private static interface InterceptorProcedure extends InterceptorFunction {

    public default Object intercept(final Object interceptor, final InvocationContext invocationContext) {
      this.execute(interceptor, invocationContext);
      return invocationContext.getTarget();
    }

    void execute(final Object interceptor, final InvocationContext invocationContext);

  }

}
