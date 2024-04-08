/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2024 microBean™.
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

import java.lang.System.Logger;

import java.lang.annotation.Annotation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.System.getLogger;
import static java.lang.System.Logger.Level.DEBUG;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

import static org.microbean.interceptor.LowLevelOperation.invokeUnchecked;

/**
 * An interception of a construction event, another lifecycle event, or a general purpose method or function.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 *
 * @see #call()
 *
 * @see InvocationContext
 */
public final class Interception implements Callable<Object> {


  /*
   * Static fields.
   */


  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private static final Logger LOGGER = getLogger(Interception.class.getName());

  private static final Optional<Object[]> OPTIONAL_EMPTY_OBJECT_ARRAY = Optional.of(EMPTY_OBJECT_ARRAY);

  private static final Lookup lookup = lookup();


  /*
   * Instance fields.
   */


  private final Supplier<? extends Constructor<?>> constructorBootstrap;

  private final ConcurrentMap<String, Object> data;

  private final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap;

  private final Supplier<? extends Method> methodBootstrap;

  private final Callable<Object> proceeder;

  private final Supplier<?> timerBootstrap;


  /*
   * Constructors.
   */

  /*
   * Lifecycle method/callback.
   */

  /**
   * Creates a new {@link Interception} that performs lifecycle event interceptions.
   *
   * <p>If this constructor is used, an invocation of the {@link #call()} method will return the value of an invocation
   * of the supplied {@code targetBootstrap}'s {@link Supplier#get() get()} method, or {@code null} if the supplied
   * {@code targetBootstrap} is, or returns, {@code null}.</p>
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may, rather uselessly, be {@code
   * null} or empty
   *
   * @param targetBootstrap a {@link Supplier} of the target instance for which a lifecycle event has occurred; may,
   * rather uselessly, be {@code null}, and may, rather uselessly, return {@code null}
   *
   * @see #call()
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Supplier<?> targetBootstrap) {
    this(interceptorMethods,
         null, // terminalFunction
         false, // setTarget
         null, // constructorBootstrap
         null, // methodBootstrap
         targetBootstrap,
         null, // argumentsBootstrap,
         null, // argumentsValidator,
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /*
   * Around-construct.
   */

  /**
   * Creates a new {@link Interception} that performs around-construct interceptions.
   *
   * <p>If this constructor is used, an invocation of the {@link #call()} method will return the value of an invocation
   * of the supplied {@link Constructor}'s {@link Constructor#newInstance(Object...) newInstance(Object...)} method.</p>
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may, rather uselessly, be {@code
   * null}
   *
   * @param constructor the {@link Constructor} being intercepted; must not be {@code null}
   *
   * @exception NullPointerException if {@code constructor} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   *
   * @see #call()
   *
   * @see #terminalFunctionOf(Constructor)
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Constructor<?> constructor)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(constructor),
         true, // setTarget
         constructorBootstrap(constructor),
         null, // methodBootstrap
         null, // targetBootstrap
         null, // argumentsBootstrap,
         argumentsValidator(constructor),
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /**
   * Creates a new {@link Interception} that performs around-construct interceptions.
   *
   * <p>If this constructor is used, an invocation of the {@link #call()} method will return the value of an invocation
   * of the supplied {@link Constructor}'s {@link Constructor#newInstance(Object...) newInstance(Object...)} method.</p>
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may, rather uselessly, be {@code
   * null}
   *
   * @param constructor the {@link Constructor} being intercepted; must not be {@code null}
   *
   * @param argumentsBootstrap a {@link Supplier} of initial arguments that will be supplied to the interception; may be
   * {@code null}
   *
   * @exception NullPointerException if {@code constructor} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   *
   * @see #call()
   *
   * @see #terminalFunctionOf(Constructor)
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Constructor<?> constructor,
                      final Supplier<? extends Object[]> argumentsBootstrap)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(constructor),
         true, // setTarget
         constructorBootstrap(constructor),
         null, // methodBootstrap
         null, // targetBootstrap
         argumentsBootstrap,
         argumentsValidator(constructor),
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /**
   * Creates a new {@link Interception} that performs around-construct interceptions.
   *
   * <p>If this constructor is used, an invocation of the {@link #call()} method will return the value of an invocation
   * of the supplied {@code terminalFunction}'s {@link Function#apply(Object) apply(Object[])} method, or {@code null}
   * if the supplied {@code terminalFunction} is {@code null}</p>
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may, rather uselessly, be {@code
   * null}
   *
   * @param terminalFunction the terminal {@link Function} being intercepted; may, rather uselessly, be {@code null}
   *
   * @param argumentsBootstrap a {@link Supplier} of initial arguments that will be supplied to the interception; may be
   * {@code null}
   *
   * @see #call()
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Function<? super Object[], ?> terminalFunction,
                      final Supplier<? extends Object[]> argumentsBootstrap) {
    this(interceptorMethods,
         terminalFunction,
         true, // setTarget
         null, // constructorBootstrap
         null, // methodBootstrap,
         null, // targetBootstrap
         argumentsBootstrap,
         null, // argumentsValidator,
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /*
   * Around-invoke or lifecycle.
   */

  /**
   * Creates a new {@link Interception} that performs around-invoke or lifecycle event interceptions.
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may be {@code null} if this
   * interception is a lifecycle event interception
   *
   * @param method the {@link Method} to intercept; may be {@code null} if this interception is a lifecycle event
   * interception
   *
   * @param targetBootstrap a {@link Supplier} of the target instance for the around-invoke invocation or for which a
   * lifecycle event has occurred; may, rather uselessly, be {@code null}, and may, rather uselessly, return {@code
   * null}; it is strongly recommended that this {@link Supplier} return a constant value
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   *
   * @see #call()
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Method method,
                      final Supplier<?> targetBootstrap)
    throws IllegalAccessException {
    this(interceptorMethods,
         method == null ? null : terminalFunctionOf(method, targetBootstrap(targetBootstrap)),
         false, // setTarget
         null, // constructorBootstrap
         methodBootstrap(method),
         targetBootstrap(targetBootstrap),
         null, // argumentsBootstrap
         argumentsValidator(method),
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /**
   * Creates a new {@link Interception} that performs around-invoke or lifecycle event interceptions.
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may be {@code null}
   *
   * @param method the {@link Method} to intercept; may be {@code null} if this interception is a lifecycle event
   * interception
   *
   * @param targetBootstrap a {@link Supplier} of the target instance for the around-invoke invocation or for which a
   * lifecycle event has occurred; may, rather uselessly, be {@code null}, and may, rather uselessly, return {@code
   * null}; it is strongly recommended that this {@link Supplier} return a constant value
   *
   * @param argumentsBootstrap a {@link Supplier} of initial arguments that will be supplied to the interception; may be
   * {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   *
   * @see #call()
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Method method,
                      final Supplier<?> targetBootstrap,
                      final Supplier<? extends Object[]> argumentsBootstrap)
    throws IllegalAccessException {
    this(interceptorMethods,
         terminalFunctionOf(method, targetBootstrap(targetBootstrap)),
         false, // setTarget
         null, // constructorBootstrap
         methodBootstrap(method),
         targetBootstrap(targetBootstrap),
         argumentsBootstrap,
         argumentsValidator(method),
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /*
   * Around-construct, around-invoke or lifecycle.
   */

  /**
   * Creates a new {@link Interception} that performs around-construct, around-invoke or lifecycle event interceptions.
   *
   * @param interceptorMethods a {@link Collection} of {@link InterceptorMethod}s; may be {@code null}
   *
   * @param terminalFunction the terminal {@link Function} being intercepted; may be {@code null} if this is a lifecycle
   * event interception
   *
   * @param aroundConstruct whether this is an around-construct interception
   *
   * @param targetBootstrap a {@link Supplier} of the target instance for an around-invoke invocation or for which a
   * lifecycle event has occurred; may, rather uselessly, be {@code null}, and may, rather uselessly, return {@code
   * null}; ignored if {@code aroundConstruct} is {@code true}
   *
   * @param argumentsBootstrap a {@link Supplier} of initial arguments that will be supplied to the interception; may be
   * {@code null}
   *
   * @see #call()
   */
  public Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                      final Function<? super Object[], ?> terminalFunction,
                      final boolean aroundConstruct,
                      final Supplier<?> targetBootstrap,
                      final Supplier<? extends Object[]> argumentsBootstrap) {
    this(interceptorMethods,
         terminalFunction,
         aroundConstruct,
         null, // constructorBootstrap
         null, // methodBootstrap
         targetBootstrap(targetBootstrap),
         argumentsBootstrap,
         null, // argumentsValidator
         null, // timerBootstrap
         null); // interceptorBindingsBootstrap
  }

  /*
   * Kitchen sink. Everything is nullable with null meaning, in general, "use a sensible default".
   */

  private Interception(final Collection<? extends InterceptorMethod> interceptorMethods,
                       final Function<? super Object[], ?> terminalFunction, // null means lifecycle event
                       final boolean setTarget, // ignored if interceptorMethods is null or empty
                       final Supplier<? extends Constructor<?>> constructorBootstrap, // better be memoized
                       final Supplier<? extends Method> methodBootstrap, // better be memoized
                       final Supplier<?> targetBootstrap, // ignored if terminalFunction is null or setTarget is true
                       final Supplier<? extends Object[]> argumentsBootstrap, // ignored if terminalFunction is null
                       final Consumer<? super Object[]> argumentsValidator, // ignored if terminalFunction is null
                       final Supplier<?> timerBootstrap, // better be memoized
                       final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap) { // better be memoized
    super();
    this.data = new ConcurrentHashMap<>();
    this.interceptorBindingsBootstrap = interceptorBindingsBootstrap == null ? Set::of : interceptorBindingsBootstrap;
    if (terminalFunction == null) {
      // Lifecycle event interception. Intercepted by lifecycle callback interceptor methods.
      //
      // Examples: @PostConstruct, @PreDestroy
      if (setTarget) {
        throw new IllegalArgumentException("terminalFunction: null; setTarget: true");
      } else if (interceptorMethods == null || interceptorMethods.isEmpty()) {
        // Pathological (no interception; no terminal function).
        this.proceeder = Interception::returnNull;
      } else {
        final Supplier<?> tb = targetBootstrap == null ? Interception::returnNull : targetBootstrap;
        final List<InterceptorMethod> ims = List.copyOf(interceptorMethods);
        this.proceeder = () ->
          new State(tb)
            .new Context(ims.iterator())
            .proceed();
      }
      this.constructorBootstrap = Interception::returnNull;
      this.methodBootstrap = Interception::returnNull;
      this.timerBootstrap = Interception::returnNull;
    } else if (setTarget) {
      // Around-construct interception. Intercepted by lifecycle callback interceptor methods.
      //
      // Example: @AroundConstruct
      if (interceptorMethods == null || interceptorMethods.isEmpty()) {
        // Pathological (no interception; only the terminal constructor function).
        final Supplier<? extends Object[]> ab =
          argumentsBootstrap == null ? Interception::emptyObjectArray : argumentsBootstrap;
        this.proceeder = () -> terminalFunction.apply(ab.get());
      } else if (targetBootstrap == null) {
        final List<InterceptorMethod> ims = List.copyOf(interceptorMethods);
        this.proceeder = () -> {
          final State s = new State(argumentsBootstrap, argumentsValidator);
          final State.Context c = s.new Context(ims.iterator(), args -> s.target(terminalFunction.apply(args)));
          // This return-value-ignoring ic.proceed() call will look odd to future maintainers (future me).
          //
          // An around-construct interceptor method can have one of two signatures:
          //
          //   void <METHOD>(InvocationContext)
          //   Object <METHOD>(InvocationContext) // this one is odd
          //
          // The one that returns Object is mainly so you can use the same method to intercept constructors and business
          // methods. Its return value in a constructor interception scenario is basically undefined. In
          // around-construct situations I'm not sure why you would rely on this value. We log it here just to make sure
          // it doesn't get lost.
          final Object v = c.proceed();
          final Object t = c.getTarget();
          if (v != t && LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "around-construct proceed() return value: " + v + "; returning getTarget() return value: " + t);
          }
          return t;
        };
      } else {
        throw new IllegalArgumentException("setTarget: true; targetBootstrap: " + targetBootstrap);
      }
      this.constructorBootstrap = constructorBootstrap == null ? Interception::returnNull : constructorBootstrap;
      // little extension here for, say, factory methods
      this.methodBootstrap = methodBootstrap == null ? Interception::returnNull : methodBootstrap;
      this.timerBootstrap = Interception::returnNull;
    } else {
      // Around-invoke or around-timeout interception. Intercepted by business method interceptor methods and timeout
      // method interceptor methods respectively. You need a target, so you need a targetBootstrap.
      //
      // Examples: @AroundInvoke, @AroundTimeout
      if (interceptorMethods == null || interceptorMethods.isEmpty()) {
        // Pathological (no interception; only the terminal function).
        final Supplier<? extends Object[]> ab =
          argumentsBootstrap == null ? Interception::emptyObjectArray : argumentsBootstrap;
        this.proceeder = () -> terminalFunction.apply(ab.get());
      } else {
        final Supplier<?> tb = targetBootstrap == null ? Interception::returnNull : targetBootstrap;
        final List<InterceptorMethod> ims = List.copyOf(interceptorMethods);
        this.proceeder = () ->
          new State(tb, argumentsBootstrap, argumentsValidator, true)
            .new Context(ims.iterator(), terminalFunction)
            .proceed();
      }
      this.constructorBootstrap = Interception::returnNull;
      this.methodBootstrap = methodBootstrap == null ? Interception::returnNull : methodBootstrap;
      this.timerBootstrap = timerBootstrap == null ? Interception::returnNull : timerBootstrap;
    }
  }


  /*
   * Instance methods.
   */


  /**
   * Invokes the interception chain this {@link Interception} represents and returns the result, which may be {@code
   * null}.
   *
   * @return the result of interception
   *
   * @exception Exception if an error occurs
   */
  @Override // Callable<Object>
  public final Object call() throws Exception {
    return this.proceeder.call();
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
    return terminalFunctionOf(privateLookupIn(c.getDeclaringClass(), lookup).unreflectConstructor(c), null);
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
    return terminalFunctionOf(privateLookupIn(m.getDeclaringClass(), lookup).unreflect(m), receiverSupplier);
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
   * @param mh a {@link MethodHandle}; must not be {@code null}. If {@code receiverSupplier} is non-{@code null}, then
   * {@code mh} must accept at least one leading argument (a receiver supplied by the supplied {@code
   * receiverSupplier}).
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
        return ps -> invokeUnchecked(terminalFunction::invokeExact);
      default:
        terminalFunction = mh.asSpreader(Object[].class, pc);
        return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(ps));
      }
    }

    // Virtual
    mh = mh.asType(mt.changeParameterType(0, Object.class));
    mt = mh.type();

    switch (pc) {
    case 0:
      throw new AssertionError(); // changeParameterType() would have blown up
    case 1:
      terminalFunction = mh;
      return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(receiverSupplier.get()));
    default:
      terminalFunction = mh.asSpreader(Object[].class, pc - 1);
      return ps -> invokeUnchecked(() -> terminalFunction.invokeExact(receiverSupplier.get(), ps));
    }
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
   * @exception IllegalArgumentException if validation fails, i.e. if the length of {@code parameterTypes} is not equal
   * to the length of {@code arguments}, or if an element of {@code parameterTypes} is {@code null} or {@code
   * void.class}, or if not every element of the supplied {@code arguments} array can be assigned to a reference bearing
   * the corresponding {@link Class} drawn from the supplied {@code parameterTypes} array
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

  private static final Object[] emptyObjectArray() {
    return EMPTY_OBJECT_ARRAY;
  }

  private static final <T> T returnNull() {
    return null;
  }

  private static final <T> void sink(final T ignored) {

  }

  private static final Consumer<? super Object[]> argumentsValidator(final Executable e) {
    return e == null ? Interception::sink : args -> validate(e.getParameterTypes(), args);
  }

  private static final Supplier<? extends Constructor<?>> constructorBootstrap(final Constructor<?> c) {
    return c == null ? Interception::returnNull : () -> c;
  }

  private static final Supplier<? extends Method> methodBootstrap(final Method m) {
    return m == null ? Interception::returnNull : () -> m;
  }

  private static final Supplier<?> targetBootstrap(final Supplier<?> s) {
    return s == null ? Interception::returnNull : s;
  }

  private static final <X> X throwIllegalStateException() {
    throw new IllegalStateException();
  }

  private static final <X> X throwIllegalStateException(final X ignored) {
    throw new IllegalStateException();
  }


  /*
   * Inner and nested classes.
   */


  private final class State {

    private volatile Optional<Object[]> arguments;

    private final Supplier<? extends Object[]> argumentsReader;

    private final Consumer<? super Object[]> argumentsInstaller;

    private volatile Optional<Object> target;

    private final Supplier<?> targetReader;

    // "update and get" semantics
    private final Function<? super Object, ?> targetInstaller;

    private State(final Supplier<?> targetBootstrap) {
      this(targetBootstrap, null, null, false);
    }

    private State(final Supplier<? extends Object[]> argumentsBootstrap,
                  final Consumer<? super Object[]> argumentsValidator) {
      this(null, argumentsBootstrap, argumentsValidator, true);
    }

    private State(final Supplier<?> targetBootstrap, // nullable
                  final Supplier<? extends Object[]> argumentsBootstrap, // ignored if mutableArguments is false
                  final Consumer<? super Object[]> argumentsValidator, // ignored if mutable arguments is false
                  final boolean mutableArguments) {
      super();
      if (targetBootstrap == null) {
        this.target = Optional.empty();
        this.targetInstaller = target -> {
          this.target = Optional.ofNullable(target); // volatile write
          return target;
        };
        this.targetReader = () -> this.target.orElse(null); // volatile read
      } else {
        this.targetInstaller = Interception::throwIllegalStateException;
        this.targetReader = () -> {
          Optional<Object> target = this.target; // volatile read
          if (target == null) {
            target = Optional.ofNullable(targetBootstrap.get());
            this.target = target; // volatile write
          }
          return target.orElse(null);
        };
      }
      if (mutableArguments) {
        if (argumentsBootstrap == null) {
          this.arguments = OPTIONAL_EMPTY_OBJECT_ARRAY;
          this.argumentsReader = this.arguments::orElseThrow; // volatile read
        } else {
          this.argumentsReader = () -> {
            Optional<Object[]> arguments = this.arguments; // volatile read
            if (arguments == null) {
              final Object[] a = argumentsBootstrap.get();
              arguments = a == null || a.length == 0 ? OPTIONAL_EMPTY_OBJECT_ARRAY : Optional.of(a);
              this.arguments = arguments; // volatile write
            }
            return arguments.orElse(null);
          };
        }
        final Consumer<? super Object[]> av = argumentsValidator == null ? Interception::sink : argumentsValidator;
        this.argumentsInstaller = args -> {
          if (args == null || args.length == 0) {
            av.accept(EMPTY_OBJECT_ARRAY);
            this.arguments = OPTIONAL_EMPTY_OBJECT_ARRAY; // volatile write
          } else {
            av.accept(args);
            this.arguments = Optional.of(args.clone()); // volatile write
          }
        };
      } else {
        this.argumentsReader = Interception::throwIllegalStateException;
        this.argumentsInstaller = Interception::throwIllegalStateException;
      }
    }

    private final Object[] arguments() {
      return this.argumentsReader.get();
    }

    private final void arguments(final Object[] arguments) {
      this.argumentsInstaller.accept(arguments);
    }

    private final Object target() {
      return this.targetReader.get();
    }

    // "update and get" semantics
    private final Object target(final Object target) {
      return this.targetInstaller.apply(target);
    }


    /*
     * Inner and nested classes.
     */


    private final class Context implements InvocationContext {

      private final Callable<?> proceeder;

      private Context(final Iterator<? extends InterceptorMethod> iterator) {
        this(iterator, Interception::returnNull);
      }

      private Context(final Iterator<? extends InterceptorMethod> iterator, final Function<? super Object[], ?> tf) {
        this(iterator, () -> tf.apply(arguments()));
      }

      private Context(final Iterator<? extends InterceptorMethod> iterator, final Callable<?> tc) {
        super();
        if (iterator == null || !iterator.hasNext()) {
          this.proceeder = tc == null ? Interception::returnNull : tc;
        } else {
          final InterceptorMethod im = Objects.requireNonNull(iterator.next());
          final Context c = new Context(iterator, tc);
          this.proceeder = () -> im.intercept(c);
        }
      }

      @Override // InvocationContext
      public final Constructor<?> getConstructor() {
        return constructorBootstrap.get();
      }

      @Override // InvocationContext
      public final Map<String, Object> getContextData() {
        return data;
      }

      // So deeply unfortunate this is going to be part of Interceptors 2.2
      // @Override
      public final Set<Annotation> getInterceptorBindings() {
        return interceptorBindingsBootstrap.get();
      }

      @Override // InvocationContext
      public final Method getMethod() {
        return methodBootstrap.get();
      }

      @Override // InvocationContext
      public final Object[] getParameters() {
        return arguments();
      }

      @Override // InvocationContext
      public final Object getTarget() {
        return target();
      }

      @Override // InvocationContext
      public final Object getTimer() {
        return timerBootstrap.get();
      }

      @Override // InvocationContext
      public final Object proceed() throws Exception {
        return this.proceeder.call();
      }

      @Override // InvocationContext
      public final void setParameters(final Object[] arguments) {
        arguments(arguments);
      }

    }

  }

}
