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

/**
 * A {@link Callable} {@link InvocationContext} implementation.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 */
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

  private volatile Object[] arguments;

  /**
   * Creates a new {@link Chain} primarily for testing purposes.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #call()}</li>
   *
   * <li>{@link #getConstructor()}</li>
   *
   * <li>{@link #getMethod()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * <li>{@link #getTarget()}</li>
   *
   * <li>{@link #proceed()}</li>
   *
   * </ul>
   */
  public Chain() {
    super();
    this.contextData = new ConcurrentHashMap<>();
    this.constructorSupplier = Chain::returnNull;
    this.methodSupplier = Chain::returnNull;
    this.timerSupplier = Chain::returnNull;
    this.targetReference = new AtomicReference<>();
    this.targetSupplier = Chain::returnNull;
    this.proceedImplementation = Chain::returnNull;
    this.arguments = EMPTY_OBJECT_ARRAY;
  }

  /**
   * Creates a new {@link Chain} for lifecycle method interceptions.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #getConstructor()}</li>
   *
   * <li>{@link #getMethod()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * </ul>
   *
   * @param interceptorMethods a {@link List} of {@link InterceptorMethod}s; may, rather uselessly, be {@code null}
   *
   * @param targetSupplier a {@link Supplier} of the target instance; may, rather uselessly, be {@code null}
   */
  // For lifecycle events like PostConstruct, PreDestroy; no terminal function in these cases
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier) {
    this(interceptorMethods,
         Chain::returnNull, // terminal function
         false, // set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         Chain::returnNull, // method supplier
         targetSupplier,
         EMPTY_OBJECT_ARRAY,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  /**
   * Creates a new {@link Chain} for around-construct interceptions.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #getMethod()}</li>
   *
   * <li>{@link #getTarget()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * </ul>
   *
   * @param interceptorMethods a {@link List} of {@link InterceptorMethod}s; may, rather uselessly, be {@code null}
   *
   * @param terminalConstructor the {@link Constructor} being intercepted; must not be {@code null}
   *
   * @exception NullPointerException if {@code terminalConstructor} is {@code null}
   */
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

  /**
   * Creates a new {@link Chain} for around-construct interceptions.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #getMethod()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * </ul>
   *
   * @param interceptorMethods a {@link List} of {@link InterceptorMethod}s; may, rather uselessly, be {@code null}
   *
   * @param terminalConstructor the {@link Constructor} being intercepted; must not be {@code null}
   *
   * @param arguments the arguments to supply to the {@link Constructor}; may be {@code null}
   *
   * @exception NullPointerException if {@code terminalConstructor} is {@code null}
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Constructor<?> terminalConstructor,
               final Object[] arguments) {
    this(interceptorMethods,
         terminalFunctionOf(terminalConstructor),
         true, // set target
         new ConcurrentHashMap<>(),
         () -> terminalConstructor,
         Chain::returnNull, // method supplier
         Chain::returnNull, // targetSupplier (initial target supplier)
         arguments,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  /**
   * Creates a new {@link Chain} for around-invoke interceptions.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #getConstructor()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * </ul>
   *
   * @param interceptorMethods a {@link List} of {@link InterceptorMethod}s; may, rather uselessly, be {@code null}
   *
   * @param targetSupplier a {@link Supplier} of the target instance; may, rather uselessly, be {@code null}
   *
   * @param terminalMethod the {@link Method} to intercept; must not be {@code null}
   *
   * @exception NullPointerException if {@code terminalMethod} is {@code null}
   */
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

  /**
   * Creates a new {@link Chain} for around-invoke interceptions.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #getConstructor()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * </ul>
   *
   * @param interceptorMethods a {@link List} of {@link InterceptorMethod}s; may, rather uselessly, be {@code null}
   *
   * @param targetSupplier a {@link Supplier} of the target instance; may, rather uselessly, be {@code null}
   *
   * @param terminalMethod the {@link Method} to intercept; must not be {@code null}
   *
   * @param arguments the arguments to supply to the {@link Method}; may be {@code null}
   *
   * @exception NullPointerException if {@code terminalMethod} is {@code null}
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Method terminalMethod,
               final Object[] arguments) {
    this(interceptorMethods,
         terminalFunctionOf(terminalMethod, targetSupplier),
         false, // don't set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         () -> terminalMethod,
         targetSupplier,
         arguments,
         Chain::returnNull, // timer supplier
         new AtomicReference<>());
  }

  /**
   * Creates a new {@link Chain} for around-construct or around-invoke interceptions.
   *
   * <p>The resulting {@link Chain} will return {@code null} from the following methods:</p>
   *
   * <ul>
   *
   * <li>{@link #getConstructor()}</li>
   *
   * <li>{@link #getMethod()}</li>
   *
   * <li>{@link #getTimer()}</li>
   *
   * </ul>
   *
   * @param interceptorMethods a {@link List} of {@link InterceptorMethod}s; may, rather uselessly, be {@code null}
   *
   * @param targetSupplier a {@link Supplier} of the target instance; may, rather uselessly, be {@code null}
   *
   * @param terminalFunction the terminal {@link Function} to intercept; must not be {@code null}
   *
   * @param setTarget whether the supplied {@code terminalFunction} is effectively a constructor
   *
   * @param arguments the arguments to supply to the terminal {@link Function}; may be {@code null}
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Function<? super Object[], ?> terminalFunction,
               final boolean setTarget, // is the terminal function effectively a constructor?
               final Object[] arguments) {
    this(interceptorMethods,
         terminalFunction,
         setTarget,
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         Chain::returnNull, // method supplier
         targetSupplier,
         arguments,
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
                final Object[] arguments,
                final Supplier<?> timerSupplier,
                final AtomicReference<Object> targetReference) {
    super();
    this.contextData = contextData == null ? new ConcurrentHashMap<>() : contextData;
    this.constructorSupplier = constructorSupplier == null ? Chain::returnNull : constructorSupplier;
    this.methodSupplier = methodSupplier == null ? Chain::returnNull : methodSupplier;
    this.arguments = arguments == null ? EMPTY_OBJECT_ARRAY : arguments;
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
                                        this.arguments,
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

  /**
   * Returns the {@link Constructor} being intercepted, if available, or {@code null}.
   *
   * @return the {@link Constructor} being intercepted, if available, or {@code null}
   */
  @Override
  public final Constructor<?> getConstructor() {
    return this.constructorSupplier.get();
  }

  @Override
  public final Map<String, Object> getContextData() {
    return this.contextData;
  }

  /**
   * Returns the {@link Method} being intercepted, if available, or {@code null}.
   *
   * @return the {@link Method} being intercepted, if available, or {@code null}
   */
  // OK to return null in many cases; see for example https://issues.redhat.com/browse/EJBTHREE-1215
  @Override
  public final Method getMethod() {
    return this.methodSupplier.get();
  }

  /**
   * Returns any arguments in effect for the current interception.
   *
   * <p>For historical reasons, the Jakarta Interceptors specification refers, incorrectly, to arguments as
   * "parameters".</p>
   *
   * @return any arguments in effect for the current interception; never {@code null}
   *
   * @see #setParameters(Object[])
   */
  @Override
  public final Object[] getParameters() {
    // Cloning etc. is not necessary; this whole API is stupid
    return this.arguments; // volatile read
  }

  /**
   * Returns the target instance, if available, or {@code null}.
   *
   * @return the target instance, if available, or {@code null}
   */
  @Override
  public final Object getTarget() {
    Object target = this.targetReference.get();
    if (target == null) {
      target = this.targetSupplier.get();
      return target == null || this.targetReference.compareAndSet(null, target) ? target : this.targetReference.get();
    }
    return target;
  }

  /**
   * Sets the target instance to be the supplied {@code target}.
   *
   * @param target the target instance; must not be {@code null}
   *
   * @exception NullPointerException if {@code target} is {@code null}
   *
   * @see #getTarget()
   */
  public final void setTarget(final Object target) {
    this.targetReference.set(Objects.requireNonNull(target, "target"));
  }

  /**
   * Returns the timer, if available, or {@code null}.
   *
   * @return the timer, if available, or {@code null}
   */
  @Override
  public final Object getTimer() {
    return this.timerSupplier.get();
  }

  /**
   * Invokes the {@link #proceed()} method and returns its result.
   *
   * @return the result of invoking the {@link #proceed()} method, which may be {@code null}
   *
   * @exception Exception if an error occurs
   *
   * @see #proceed()
   */
  @Override // Callable<Object>
  public final Object call() throws Exception {
    return this.proceed();
  }

  /**
   * Applies the next interception in this {@link Chain}, or calls the terminal function and returns the result, which
   * may be {@code null}.
   *
   * @return the result of proceeding, which may be {@code null}
   *
   * @exception Exception if an error occurs
   */
  @Override
  public Object proceed() throws Exception {
    return this.proceedImplementation.get();
  }

  /**
   * Sets arguments to be in effect for the current interception.
   *
   * <p>For historical reasons, the Jakarta Interceptors specification refers, incorrectly, to arguments as
   * "parameters".</p>
   *
   * @param arguments the arguments; may be {@code null}
   *
   * @see #getParameters()
   */
  @Override
  public final void setParameters(final Object[] arguments) {
    // Cloning etc. is not necessary; this whole API is stupid
    this.arguments = arguments == null ? EMPTY_OBJECT_ARRAY : arguments; // volatile write
  }


  /*
   * Static methods.
   */


  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@link Constructor}.
   *
   * @param c a {@link Constructor}; must not be {@code null}
   *
   * @return a {@link Function} encapsulating the supplied {@link Constructor}; never {@code null}
   *
   * @exception NullPointerException if {@code c} is {@code null}
   *
   * @exception IllegalStateException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  public static final Function<Object[], Object> terminalFunctionOf(final Constructor<?> c) {
    try {
      return terminalFunctionOf(privateLookupIn(c.getDeclaringClass(), Chain.lookup).unreflectConstructor(c), null);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@code static} {@link Method}.
   *
   * @param staticMethod a {@code static} {@link Method}; must not be {@code null}
   *
   * @return a {@link Function} encapsulating the supplied {@code static} {@link Method}; never {@code null}
   *
   * @exception NullPointerException if {@code staticMethod} is {@code null}
   *
   * @exception IllegalStateException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public static final Function<Object[], Object> terminalFunctionOf(final Method staticMethod) {
    return terminalFunctionOf(staticMethod, null);
  }

  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@link Method}.
   *
   * @param m a {@link Method}; must not be {@code null}. If {@code m} is a virtual method, then the supplied {@code
   * receiverSupplier} will be used to supply its receiver
   *
   * @param receiverSupplier a {@link Supplier} of the receiver for the supplied {@link Method}; may be {@code null} in
   * which case the supplied {@link Method} must be {@code static}
   *
   * @return a {@link Function} encapsulating the supplied {@link Method}; never {@code null}
   *
   * @exception NullPointerException if {@code m} is {@code null}
   *
   * @exception IllegalStateException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public static final Function<Object[], Object> terminalFunctionOf(final Method m, final Supplier<?> receiverSupplier) {
    try {
      return terminalFunctionOf(privateLookupIn(m.getDeclaringClass(), Chain.lookup).unreflect(m), receiverSupplier);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@link MethodHandle}.
   *
   * @param receiverlessMethodHandle a {@link MethodHandle}; must not be {@code null}. {@code mh} must be receiverless
   * or {@linkplain MethodHandle#bindTo(Object) bound} to a receiver already.
   *
   * @return a {@link Function} encapsulating the supplied {@link MethodHandle}; never {@code null}
   *
   * @exception NullPointerException if {@code mh} is {@code null}
   */
  public static final Function<Object[], Object> terminalFunctionOf(final MethodHandle receiverlessMethodHandle) {
    return terminalFunctionOf(receiverlessMethodHandle, null);
  }

  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@link MethodHandle}.
   *
   * @param mh a {@link MethodHandle}; must not be {@code null}. If {@code mh} is a {@link MethodHandle} requiring a
   * receiver, then the supplied {@code receiverSupplier} will be used to supply its receiver
   *
   * @param receiverSupplier a {@link Supplier} of the receiver for the supplied {@link MethodHandle}; may be {@code
   * null} in which case the supplied {@link MethodHandle} must be receiverless
   *
   * @return a {@link Function} encapsulating the supplied {@link MethodHandle}; never {@code null}
   *
   * @exception NullPointerException if {@code mh} is {@code null}
   */
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

  private static final <T> T returnNull(final Object[] ignored) {
    return null;
  }

}
