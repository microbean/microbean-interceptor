/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2022–2024 microBean™.
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
import java.lang.invoke.VarHandle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

import static org.microbean.interceptor.LowLevelOperation.invokeUnchecked;

/**
 * A {@link Callable} {@link InvocationContext} implementation representing the interception of a constructor, method,
 * or lifecycle event.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 *
 * @see #proceed()
 */
public class Chain implements Callable<Object>, InvocationContext {


  /*
   * Static fields.
   */


  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private static final Lookup lookup = lookup();

  private static final VarHandle ARGUMENTS;

  private static final VarHandle TARGET;

  static {
    try {
      ARGUMENTS = lookup.findVarHandle(Chain.class, "arguments", Object[].class);
      TARGET = lookup.findVarHandle(Chain.class, "target", Object.class);
    } catch (final IllegalAccessException | NoSuchFieldException e) {
      throw (ExceptionInInitializerError)new ExceptionInInitializerError(e.getMessage()).initCause(e);
    }
  }


  /*
   * Instance fields.
   */


  private final ConcurrentMap<String, Object> contextData;

  private final Supplier<? extends Constructor<?>> constructorSupplier;

  private final Supplier<? extends Method> methodSupplier;

  private final Supplier<?> timerSupplier;

  private final Supplier<?> targetSupplier;

  private final Supplier<? extends Object[]> argumentsSupplier;

  private final Consumer<? super Object[]> setParametersImplementation;

  private final Callable<?> proceedImplementation;

  private final Chain chain;

  private volatile Object[] arguments;

  private volatile Object target;


  /*
   * Constructors.
   */

  /*
   * Lifecycle method/callback.
   */

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
    this.targetSupplier = Chain::returnNull;
    this.chain = this;
    this.proceedImplementation = Chain::returnNull;
    this.argumentsSupplier = Chain::emptyObjectArray;
    this.setParametersImplementation = Chain::throwIllegalStateException;
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
         null, // terminal function; null on purpose
         false, // set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         Chain::returnNull, // method supplier
         targetSupplier,
         Chain::emptyObjectArray, // arguments supplier
         Chain::sink, // arguments validator
         Chain::returnNull, // timer supplier
         null); // chain
  }

  /*
   * Around-construct.
   */

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
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Constructor<?> terminalConstructor)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(terminalConstructor),
         true, // set target
         new ConcurrentHashMap<>(),
         () -> terminalConstructor,
         Chain::returnNull, // method supplier
         Chain::returnNull, // targetSupplier (initial target supplier)
         Chain::emptyObjectArray, // arguments supplier
         args -> validate(terminalConstructor.getParameterTypes(), args),
         Chain::returnNull, // timer supplier
         null); // chain
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
   * @param argumentsSupplier a {@link Supplier} supplying the arguments for the {@link Constructor}; may be {@code
   * null}
   *
   * @exception NullPointerException if {@code terminalConstructor} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Constructor<?> terminalConstructor,
               final Supplier<? extends Object[]> argumentsSupplier)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(terminalConstructor),
         true, // set target
         new ConcurrentHashMap<>(),
         () -> terminalConstructor,
         Chain::returnNull, // method supplier
         Chain::returnNull, // targetSupplier (initial target supplier)
         argumentsSupplier,
         args -> validate(terminalConstructor.getParameterTypes(), args),
         Chain::returnNull, // timer supplier
         null); // chain
  }

  /**
   * Creates a new {@link Chain} for around-construct interceptions.
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
   * @param terminalFunction the terminal {@link Function} to intercept; must not be {@code null}
   *
   * @param argumentsSupplier a {@link Supplier} supplying the arguments for the terminal {@link Function}; may be
   * {@code null}
   *
   * @exception NullPointerException if {@code terminalFunction} is {@code null}
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Function<? super Object[], ?> terminalFunction,
               final Supplier<? extends Object[]> argumentsSupplier) {
    this(interceptorMethods,
         Objects.requireNonNull(terminalFunction, "terminalFunction"),
         true, // set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         Chain::returnNull, // method supplier
         Chain::returnNull, // target supplier (won't ever be consulted/invoked; target is set by terminalFunction directly)
         argumentsSupplier,
         Chain::sink, // arguments validator
         Chain::returnNull, // timer supplier
         null); // chain
  }

  /*
   * Around-invoke.
   */

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
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Method terminalMethod)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(terminalMethod, targetSupplier),
         false, // don't set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         () -> terminalMethod,
         targetSupplier,
         Chain::emptyObjectArray, // arguments supplier
         args -> validate(terminalMethod.getParameterTypes(), args),
         Chain::returnNull, // timer supplier
         null); // chain
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
   * @param argumentsSupplier a {@link Supplier} supplying the arguments for the {@link Method}; may be {@code null}
   *
   * @exception NullPointerException if {@code terminalMethod} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Method terminalMethod,
               final Supplier<? extends Object[]> argumentsSupplier)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(terminalMethod, targetSupplier),
         false, // don't set target
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         () -> terminalMethod,
         targetSupplier,
         argumentsSupplier,
         args -> validate(terminalMethod.getParameterTypes(), args),
         Chain::returnNull, // timer supplier
         null); // chain
  }

  /*
   * Around-construct or around-invoke (but not lifecycle method).
   */

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
   * @param argumentsSupplier a {@link Supplier} supplying the arguments for the terminal {@link Function}; may be
   * {@code null}
   *
   * @exception NullPointerException if {@code terminalFunction} is {@code null}
   */
  public Chain(final List<? extends InterceptorMethod> interceptorMethods,
               final Supplier<?> targetSupplier,
               final Function<? super Object[], ?> terminalFunction,
               final boolean setTarget, // is the terminal function effectively a constructor?
               final Supplier<? extends Object[]> argumentsSupplier) {
    this(interceptorMethods,
         Objects.requireNonNull(terminalFunction, "terminalFunction"),
         setTarget,
         new ConcurrentHashMap<>(),
         Chain::returnNull, // constructor supplier
         Chain::returnNull, // method supplier
         targetSupplier,
         argumentsSupplier,
         Chain::sink, // arguments validator
         Chain::returnNull, // timer supplier
         null); // chain
  }

  /*
   * Kitchen sink constructor.
   */

  // Everything is nullable.
  private Chain(List<? extends InterceptorMethod> interceptorMethods,
                final Function<? super Object[], ?> terminalFunction,
                final boolean setTarget,
                final ConcurrentMap<String, Object> contextData,
                final Supplier<? extends Constructor<?>> constructorSupplier,
                final Supplier<? extends Method> methodSupplier,
                final Supplier<?> targetSupplier,
                final Supplier<? extends Object[]> argumentsSupplier,
                final Consumer<? super Object[]> argumentsValidator,
                final Supplier<?> timerSupplier,
                final Chain parent) {
    super();
    this.contextData = contextData == null ? new ConcurrentHashMap<>() : contextData;
    this.constructorSupplier = constructorSupplier == null ? Chain::returnNull : constructorSupplier;
    this.methodSupplier = methodSupplier == null ? Chain::returnNull : methodSupplier;
    this.argumentsSupplier = argumentsSupplier == null ? Chain::emptyObjectArray : argumentsSupplier;
    this.timerSupplier = timerSupplier == null ? Chain::returnNull : timerSupplier;
    this.targetSupplier = targetSupplier == null ? Chain::returnNull : targetSupplier;
    this.chain = parent == null ? this : parent;
    if (interceptorMethods == null || interceptorMethods.isEmpty()) {
      Objects.requireNonNull(terminalFunction, "terminalFunction");
      this.setParametersImplementation =
        argumentsValidator == null ?
        args -> this.setArguments(Chain::sink, args) :
        args -> this.setArguments(argumentsValidator, args);
      if (setTarget) {
        this.proceedImplementation = () -> {
          final Object t = terminalFunction.apply(this.getParameters());
          TARGET.setVolatile(this.chain, t); // volatile write
          return t;
        };
      } else {
        this.proceedImplementation = () -> terminalFunction.apply(this.getParameters());
      }
    } else {
      if (terminalFunction == null) {
        this.setParametersImplementation = Chain::throwIllegalStateException;
      } else if (argumentsValidator == null) {
        this.setParametersImplementation = args -> this.setArguments(Chain::sink, args);
      } else {
        this.setParametersImplementation = args -> this.setArguments(argumentsValidator, args);
      }
      interceptorMethods = List.copyOf(interceptorMethods);
      final int size = interceptorMethods.size();
      final InterceptorMethod im = interceptorMethods.get(0);
      // TODO: OK to create Chain here, or do we need to create a new one inside the proceedImplementation? Consider
      // setParameters(), setTarget(Object). See also: https://www.eclipse.org/lists/interceptors-dev/msg00056.html
      final Chain chain = new Chain(size == 1 ? List.of() : interceptorMethods.subList(1, size),
                                    terminalFunction,
                                    setTarget,
                                    this.contextData,
                                    this.constructorSupplier,
                                    this.methodSupplier,
                                    this.targetSupplier,
                                    this.argumentsSupplier,
                                    argumentsValidator,
                                    this.timerSupplier,
                                    this.chain);
      
      this.proceedImplementation = () -> im.intercept(chain);
    }
  }


  /*
   * Instance methods.
   */


  /**
   * Returns the {@link Constructor} being intercepted, if available, or {@code null}.
   *
   * @return the {@link Constructor} being intercepted, if available, or {@code null}
   */
  @Override
  public final Constructor<?> getConstructor() {
    return this.constructorSupplier.get();
  }

  /**
   * Returns the context data {@link Map} shared by the current invocation.
   *
   * @return the context data {@link Map} shared by the current invocation; never {@code null}
   *
   * @see InvocationContext#getContextData()
   */
  @Override
  public final Map<String, Object> getContextData() {
    return this.contextData;
  }

  /**
   * Returns the {@link Method} being intercepted, if available, or {@code null}.
   *
   * @return the {@link Method} being intercepted, if available, or {@code null}
   */
  // OK to return null in many cases; see
  // https://jakarta.ee/specifications/interceptors/2.1/jakarta-interceptors-spec-2.1#invocation_context
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
   * @exception IllegalStateException if invoked within a lifecycle callback method that is not an {@link
   * jakarta.interceptor.AroundConstruct AroundConstruct} callback
   *
   * @see #setParameters(Object[])
   */
  @Override
  public final Object[] getParameters() {
    // Cloning etc. is not necessary; this whole API is stupid
    final Object[] arguments = this.chain.arguments; // volatile read
    if (arguments == null) {
      try {
        this.setParameters(this.argumentsSupplier.get());
      } catch (final IllegalArgumentException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
      return this.chain.arguments; // volatile read
    }
    return arguments;
  }

  /**
   * Returns the target instance, if available, or {@code null}.
   *
   * @return the target instance, if available, or {@code null}
   */
  @Override
  public final Object getTarget() {
    Object target = this.chain.target; // volatile read
    if (target == null) {
      target = this.targetSupplier.get();
      if (target != null && TARGET.compareAndSet(this.chain, null, target)) { // volatile write
        target = this.chain.target; // volatile read
      }
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
   *
   * @deprecated This really shouldn't be needed as part of the public API.
   */
  @Deprecated // should not be needed?
  public final void setTarget(final Object target) {
    TARGET.setVolatile(this.chain, Objects.requireNonNull(target, "target")); // volatile write
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
   * Applies the next interception in this {@link Chain}, or calls the terminal function, and returns the result of the
   * interception, which may be {@code null}.
   *
   * <p>Overrides must not call {@link #call()} or an infinite loop will result.</p>
   *
   * @return the result of proceeding, which may be {@code null}
   *
   * @exception Exception if an error occurs
   */
  @Override
  public Object proceed() throws Exception {
    return this.proceedImplementation.call();
  }

  /**
   * Sets arguments to be in effect for the current interception.
   *
   * <p>For historical reasons, the Jakarta Interceptors specification refers, incorrectly, to arguments as
   * "parameters".</p>
   *
   * @param arguments the arguments; may be {@code null}
   *
   * @exception IllegalArgumentException if the arguments are invalid
   *
   * @exception IllegalStateException if invoked within a lifecycle callback method that is not an {@link
   * jakarta.interceptor.AroundConstruct AroundConstruct} callback
   *
   * @see #getParameters()
   */
  @Override
  public final void setParameters(final Object[] arguments) {
    this.setParametersImplementation.accept(arguments);
  }

  private final void setArguments(final Consumer<? super Object[]> argumentsValidator, final Object[] arguments) {
    if (arguments == null) {
      argumentsValidator.accept(EMPTY_OBJECT_ARRAY);
      this.chain.arguments = EMPTY_OBJECT_ARRAY; // volatile write
    } else {
      // Cloning etc. is not necessary; this whole API is stupid
      argumentsValidator.accept(arguments);
      this.chain.arguments = arguments; // volatile write
    }
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
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  public static final Function<Object[], Object> terminalFunctionOf(final Constructor<?> c) throws IllegalAccessException {
    return terminalFunctionOf(privateLookupIn(c.getDeclaringClass(), Chain.lookup).unreflectConstructor(c), null);
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
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public static final Function<Object[], Object> terminalFunctionOf(final Method staticMethod) throws IllegalAccessException {
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
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public static final Function<Object[], Object> terminalFunctionOf(final Method m, final Supplier<?> receiverSupplier)
    throws IllegalAccessException {
    return terminalFunctionOf(privateLookupIn(m.getDeclaringClass(), Chain.lookup).unreflect(m), receiverSupplier);
  }

  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@link MethodHandle}.
   *
   * @param receiverlessMethodHandle a {@link MethodHandle}; must not be {@code null}; must be receiverless or
   * {@linkplain MethodHandle#bindTo(Object) bound} to a receiver already.
   *
   * @return a {@link Function} encapsulating the supplied {@link MethodHandle}; never {@code null}
   *
   * @exception NullPointerException if {@code receiverlessMethodHandle} is {@code null}
   */
  public static final Function<Object[], Object> terminalFunctionOf(final MethodHandle receiverlessMethodHandle) {
    return terminalFunctionOf(receiverlessMethodHandle, null);
  }

  /**
   * Creates and returns a {@link Function} encapsulating the supplied {@link MethodHandle}.
   *
   * @param mh a {@link MethodHandle}; must not be {@code null}. If {@code mh} is a {@link MethodHandle} requiring a
   * receiver, then the supplied {@code receiverSupplier}, if non-{@code null}, will be used to supply its receiver
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
        terminalFunction = mh.asSpreader(Object[].class, pc);
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
      terminalFunction = mh.asSpreader(Object[].class, pc - 1);
      return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(receiverSupplier.get(), ps));
    }
  }

  private static final <T> T returnNull() {
    return null;
  }

  private static final <X, Y> X returnNull(final Y ignored) {
    return null;
  }

  private static final Object[] emptyObjectArray() {
    return EMPTY_OBJECT_ARRAY;
  }

  private static final <T> void sink(T ignored) {

  }

  private static final <X> void throwIllegalStateException(final X ignored) {
    throw new IllegalStateException();
  }

  /**
   * A convenience method that ensures that every element of the supplied {@code arguments} array can be assigned to a
   * reference bearing the corresponding {@link Class} drawn from the supplied {@code parameterTypes} array.
   *
   * <p>Boxing, unboxing and widening conversions are taken into consideration.</p>
   *
   * <p>This method implements the logic implied, but nowhere actually specified, by the contract of {@link
   * InvocationContext#setParameters(Object[])}.</p>
   *
   * @param parameterTypes an array of {@link Class} instances; may be {@code null}; must not contain {@code null}
   * elements or {@code void.class}; must have a length equal to that of the supplied {@code arguments} array
   *
   * @param arguments an array of {@link Object}s; may be {@code null}; must have a length equal to that of the supplied
   * {@code parameterTypes} array
   *
   * @exception IllegalArgumentException if not every element of the supplied {@code arguments} array can be assigned to
   * a reference bearing the corresponding {@link Class} drawn from the supplied {@code parameterTypes} array
   *
   * @see #setParameters(Object[])
   */
  public static final void validate(final Class<?>[] parameterTypes, final Object[] arguments) {
    final int parameterTypesLength = parameterTypes == null ? 0 : parameterTypes.length;
    final int argumentsLength = arguments == null ? 0 : arguments.length;
    if (argumentsLength != parameterTypesLength) {
      throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                         "; arguments: " + Arrays.toString(arguments));
    }
    for (int i = 0; i < argumentsLength; i++) {
      final Class<?> parameterType = parameterTypes[i];
      if (parameterType == null || parameterType == void.class) {
        throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                           "; arguments: " + Arrays.toString(arguments) +
                                           "; parameter type: " + parameterType);
      }
      final Object argument = arguments[i];
      if (argument == null) {
        if (parameterType.isPrimitive() || parameterType != Void.class) {
          throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                             "; arguments: " + Arrays.toString(arguments) +
                                             "; parameter type: " + parameterType.getName() +
                                             "; argument: null");
        }
      } else {
        final Class<?> argumentType = argument.getClass();
        if (parameterType != argumentType) {
          if (parameterType == boolean.class) {
            if (argumentType != Boolean.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: boolean" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Boolean.class) {
            if (argumentType != boolean.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Boolean" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == byte.class) {
            if (argumentType != Byte.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: byte" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Byte.class) {
            if (argumentType != byte.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Byte" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == char.class) {
            if (argumentType != Character.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: char" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Character.class) {
            if (argumentType != char.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Character" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == double.class) {
            if (argumentType != byte.class &&
                argumentType != char.class &&
                argumentType != Double.class &&
                argumentType != float.class &&
                argumentType != int.class &&
                argumentType != long.class &&
                argumentType != short.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: double" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Double.class) {
            if (argumentType != double.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Double" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == float.class) {
            if (argumentType != byte.class &&
                argumentType != char.class &&
                argumentType != Float.class &&
                argumentType != int.class &&
                argumentType != long.class &&
                argumentType != short.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: float" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Float.class) {
            if (argumentType != float.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Float" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == int.class) {
            if (argumentType != byte.class &&
                argumentType != char.class &&
                argumentType != Integer.class &&
                argumentType != short.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: int; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Integer.class) {
            if (argumentType != int.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Integer" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == long.class) {
            if (argumentType != byte.class &&
                argumentType != char.class &&
                argumentType != int.class &&
                argumentType != Long.class &&
                argumentType != short.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: long" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Long.class) {
            if (argumentType != long.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Long" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == short.class) {
            if (argumentType != byte.class &&
                argumentType != Short.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: byte" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Short.class) {
            if (argumentType != short.class) {
              throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                                 "; arguments: " + Arrays.toString(arguments) +
                                                 "; parameter type: java.lang.Short" +
                                                 "; argument type: " + argumentType.getName());
            }
          } else if (parameterType == Void.class || !parameterType.isAssignableFrom(argumentType)) {
            throw new IllegalArgumentException("parameter types: " + Arrays.toString(parameterTypes) +
                                               "; arguments: " + Arrays.toString(arguments) +
                                               "; parameter type: " + parameterType.getName() +
                                               "; argument type: " + argumentType.getName());
          }
        }
      }
    }
  }

}
