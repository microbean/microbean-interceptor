/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2024–2025 microBean™.
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

import java.lang.System.Logger;

import java.lang.annotation.Annotation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.interceptor.InvocationContext;

import static java.lang.System.getLogger;
import static java.lang.System.Logger.Level.DEBUG;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

/**
 * A utility class that makes {@link InterceptionFunction}s and {@link Runnable}s that intercept lifecycle events,
 * constructions, and invocations of methods in accordance with the Jakarta Interceptors specification.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 */
public final class Interceptions {


  /*
   * Static fields.
   */


  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private static final Property<Object[]> EMPTY_OBJECT_ARRAY_PROPERTY = new Property<>(Interceptions::emptyObjectArray, false);

  private static final Logger LOGGER = getLogger(Interceptions.class.getName());


  /*
   * Instance fields.
   */


  private final ConcurrentMap<String, Object> data;

  private final Supplier<? extends Constructor<?>> constructorBootstrap;

  private final Supplier<? extends Method> methodBootstrap;

  private final Supplier<?> timerBootstrap;

  private final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap;


  /*
   * Constructors.
   */


  private Interceptions(final Supplier<? extends Constructor<?>> constructorBootstrap,
                        final Supplier<? extends Method> methodBootstrap,
                        final Supplier<?> timerBootstrap,
                        final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap) {
    super();
    this.data = new ConcurrentHashMap<>();
    this.constructorBootstrap = constructorBootstrap == null ? Interceptions::returnNull : constructorBootstrap;
    this.methodBootstrap = methodBootstrap == null ? Interceptions::returnNull : methodBootstrap;
    this.timerBootstrap = timerBootstrap == null ? Interceptions::returnNull : timerBootstrap;
    this.interceptorBindingsBootstrap = interceptorBindingsBootstrap == null ? Set::of : interceptorBindingsBootstrap;
  }


  /*
   * Static methods.
   */


  /**
   * Returns a {@link Runnable} whose {@link Runnable#run() run()} method will invoke all supplied {@link
   * InterceptorMethod}s in encounter order.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} in which case the returned
   * {@link Runnable}'s {@link Runnable#run() run()} method will do nothing
   *
   * @param targetBootstrap a {@link Supplier} that will be called for the initial value to be returned by the first
   * invocation of {@link InvocationContext#getTarget()}; may be {@code null} in which case the value too will be {@code
   * null}
   *
   * @return a {@link Runnable}; never {@code null}
   */
  // Methodless lifecycle event interception. For example, post-construct interceptions by external interceptors only.
  public static final Runnable ofLifecycleEvent(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                final Supplier<?> targetBootstrap) {
    return ofLifecycleEvent(interceptorMethods, targetBootstrap, Set::of);
  }

  /**
   * Returns a {@link Runnable} whose {@link Runnable#run() run()} method will invoke all supplied {@link
   * InterceptorMethod}s in encounter order.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} in which case the returned
   * {@link Runnable}'s {@link Runnable#run() run()} method will do nothing
   *
   * @param targetBootstrap a {@link Supplier} that will be called for the initial value to be returned by the first
   * invocation of {@link InvocationContext#getTarget()}; may be {@code null} in which case the value too will be {@code
   * null}
   *
   * @param interceptorBindingsBootstrap a {@link Supplier} of a {@link Set} of {@link Annotation}s that will be called
   * for the value to be returned by the first invocation of {@link InvocationContext#getInterceptorBindings()}; may be
   * {@code null} in which case the value will be an {@linkplain Set#of() empty, immutable <code>Set</code>}
   *
   * @return a {@link Runnable}; never {@code null}
   */
  // Methodless lifecycle event interception. For example, post-construct interceptions by external interceptors only.
  public static final Runnable ofLifecycleEvent(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                final Supplier<?> targetBootstrap,
                                                final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap) {
    return () -> {
      ff(interceptorMethods,
         null,
         targetBootstrap,
         null, // argumentsValidator
         false, // setTarget
         null, // cb
         null, // mb // TODO: may have to be the last im in the chain if it's defined on the target class (!); weird requirement
         null, // tb
         interceptorBindingsBootstrap)
        .apply(EMPTY_OBJECT_ARRAY);
    };
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * Constructor}'s {@link Constructor#newInstance(Object...) newInstance(Object...)} method.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param constructor the {@link Constructor} to invoke; may be {@code null} (rather uselessly)
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   *
   * @exception NullPointerException if {@code lookup} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  // Around-construct
  public static final InterceptionFunction ofConstruction(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                          final Lookup lookup,
                                                          final Constructor<?> constructor)
    throws IllegalAccessException {
    return ofConstruction(interceptorMethods, lookup, constructor, Set::of);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * Constructor}'s {@link Constructor#newInstance(Object...) newInstance(Object...)} method.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param constructor the {@link Constructor} to invoke; may be {@code null} (rather uselessly)
   *
   * @param interceptorBindingsBootstrap a {@link Supplier} of a {@link Set} of {@link Annotation}s that will be called
   * for the value to be returned by the first invocation of {@link InvocationContext#getInterceptorBindings()}; may be
   * {@code null} in which case the value will be an {@linkplain Set#of() empty, immutable <code>Set</code>}
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   *
   * @exception NullPointerException if {@code lookup} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  // Around-construct
  public static final InterceptionFunction ofConstruction(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                          final Lookup lookup,
                                                          final Constructor<?> constructor,
                                                          final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap)
    throws IllegalAccessException {
    return
      ff(interceptorMethods,
         terminalBiFunctionOf(lookup, constructor),
         null, // targetBootstrap
         a -> validate(constructor.getParameterTypes(), a),
         true, // setTarget
         () -> constructor,
         null, // mb
         null, // tb
         interceptorBindingsBootstrap);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * BiFunction}'s {@link BiFunction#apply(Object, Object) apply(Object, Object[])} method with {@code null} (the return
   * value of {@link InvocationContext#getTarget()}, which will always be {@code null} in this scenario) and the return
   * value of an invocation of {@link InvocationContext#getParameters()}.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param terminalBiFunction a {@link BiFunction} serving as a notional constructor that takes {@code null}, always,
   * as its first argument, and the return value of an invocation of {@link InvocationContext#getParameters()} as its
   * second argument, and returns a new instance; may be {@code null} (rather uselessly)
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   */
  // Around-construct
  public static final InterceptionFunction ofConstruction(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                          final BiFunction<? super Object, ? super Object[], ?> terminalBiFunction) {
    return ofConstruction(interceptorMethods, terminalBiFunction, Set::of);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * BiFunction}'s {@link BiFunction#apply(Object, Object) apply(Object Object[])} method with {@code null} (the return
   * value of {@link InvocationContext#getTarget()}, which will always be {@code null} in this scenario) and the return
   * value of an invocation of {@link InvocationContext#getParameters()}.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param terminalBiFunction a {@link BiFunction} serving as a notional constructor that takes {@code null}, always,
   * as its first argument, and the return value of an invocation of {@link InvocationContext#getParameters()} as its
   * second argument, and returns a new instance; may be {@code null} (rather uselessly)
   *
   * @param interceptorBindingsBootstrap a {@link Supplier} of a {@link Set} of {@link Annotation}s that will be called
   * for the value to be returned by the first invocation of {@link InvocationContext#getInterceptorBindings()}; may be
   * {@code null} in which case the value will be an {@linkplain Set#of() empty, immutable <code>Set</code>}
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   */
  // Around-construct
  public static final InterceptionFunction ofConstruction(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                          final BiFunction<? super Object, ? super Object[], ?> terminalBiFunction,
                                                          final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap) {
    return
      ff(interceptorMethods,
         terminalBiFunction,
         null, // targetBootstrap
         null, // argumentsValidator
         true, // setTarget
         null, // cb
         null, // mb
         null, // tb
         interceptorBindingsBootstrap);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * Method}'s {@link Method#invoke(Object, Object...) invoke(Object, Object...)} method with the return value of {@link
   * InvocationContext#getTarget()}, and with the return value of {@link InvocationContext#getParameters()}.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param method a {@link Method} encapsulating the invocation to be intercepted whose {@link Method#invoke(Object,
   * Object...)  invoke(Object, Object...)} method takes the return value of {@link InvocationContext#getTarget()} as
   * its first argument, and the return value of {@link InvocationContext#getParameters()} spread out appropriately as
   * its trailing arguments; may be {@code null} (rather uselessly)
   *
   * @param targetBootstrap a {@link Supplier} that will be called for the initial value to be returned by the first
   * invocation of {@link InvocationContext#getTarget()}; may be {@code null} in which case the value too will be {@code
   * null}
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   *
   * @exception NullPointerException if {@code lookup} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  // Around-invoke or similar.
  public static final InterceptionFunction ofInvocation(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                        final Lookup lookup,
                                                        final Method method, // not nullable
                                                        final Supplier<?> targetBootstrap)
    throws IllegalAccessException {
    return ofInvocation(interceptorMethods, lookup, method, targetBootstrap, Set::of);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * Method}'s {@link Method#invoke(Object, Object...) invoke(Object, Object...)} method with the return value of {@link
   * InvocationContext#getTarget()}, and with the return value of {@link InvocationContext#getParameters()}.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param method a {@link Method} encapsulating the invocation to be intercepted whose {@link Method#invoke(Object,
   * Object...)  invoke(Object, Object...)} method takes the return value of {@link InvocationContext#getTarget()} as
   * its first argument, and the return value of {@link InvocationContext#getParameters()} spread out appropriately as
   * its trailing arguments; may be {@code null} (rather uselessly)
   *
   * @param targetBootstrap a {@link Supplier} that will be called for the initial value to be returned by the first
   * invocation of {@link InvocationContext#getTarget()}; may be {@code null} in which case the value too will be {@code
   * null}
   *
   * @param interceptorBindingsBootstrap a {@link Supplier} of a {@link Set} of {@link Annotation}s that will be called
   * for the value to be returned by the first invocation of {@link InvocationContext#getInterceptorBindings()}; may be
   * {@code null} in which case the value will be an {@linkplain Set#of() empty, immutable <code>Set</code>}
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   *
   * @exception NullPointerException if {@code lookup} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  // Around-invoke or similar.
  public static final InterceptionFunction ofInvocation(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                        final Lookup lookup,
                                                        final Method method, // not nullable
                                                        final Supplier<?> targetBootstrap,
                                                        final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap)
    throws IllegalAccessException {
    return
      ff(interceptorMethods,
         method == null ? null : terminalBiFunctionOf(lookup, method),
         targetBootstrap,
         method == null ? null : a -> validate(method.getParameterTypes(), a),
         false, // setTarget
         null, // cb,
         method == null ? null : () -> method,
         null, // tb
         interceptorBindingsBootstrap);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * BiFunction}'s {@link BiFunction#apply(Object, Object) apply(Object Object[])} method with the return value of
   * {@link InvocationContext#getTarget()}, and with the return value of {@link InvocationContext#getParameters()}.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param terminalBiFunction a {@link BiFunction} encapsulating the invocation to be intercepted that takes the return
   * value of {@link InvocationContext#getTarget()} as its first argument, and the return value of {@link
   * InvocationContext#getParameters()} as its second argument; may be {@code null} (rather uselessly)
   *
   * @param targetBootstrap a {@link Supplier} that will be called for the initial value to be returned by the first
   * invocation of {@link InvocationContext#getTarget()}; may be {@code null} in which case the value too will be {@code
   * null}
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   */
  // Around-invoke or similar.
  public static final InterceptionFunction ofInvocation(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                        final BiFunction<? super Object, ? super Object[], ?> terminalBiFunction,
                                                        final Supplier<?> targetBootstrap) {
    return ofInvocation(interceptorMethods, terminalBiFunction, targetBootstrap, Set::of);
  }

  /**
   * Returns an {@link InterceptionFunction} whose {@link InterceptionFunction#apply(Object...) apply(Object...)} method
   * will invoke all supplied {@link InterceptorMethod}s in encounter order before invoking the supplied {@link
   * BiFunction}'s {@link BiFunction#apply(Object, Object) apply(Object Object[])} method with the return value of
   * {@link InvocationContext#getTarget()}, and with the return value of {@link InvocationContext#getParameters()}.
   *
   * @param interceptorMethods the {@link InterceptorMethod}s to invoke; may be {@code null} (rather uselessly)
   *
   * @param terminalBiFunction a {@link BiFunction} encapsulating the invocation to be intercepted that takes the return
   * value of {@link InvocationContext#getTarget()} as its first argument, and the return value of {@link
   * InvocationContext#getParameters()} as its second argument; may be {@code null} (rather uselessly)
   *
   * @param targetBootstrap a {@link Supplier} that will be called for the initial value to be returned by the first
   * invocation of {@link InvocationContext#getTarget()}; may be {@code null} in which case the value too will be {@code
   * null}
   *
   * @param interceptorBindingsBootstrap a {@link Supplier} of a {@link Set} of {@link Annotation}s that will be called
   * for the value to be returned by the first invocation of {@link InvocationContext#getInterceptorBindings()}; may be
   * {@code null} in which case the value will be an {@linkplain Set#of() empty, immutable <code>Set</code>}
   *
   * @return an {@link InterceptionFunction}; never {@code null}
   */
  // Around-invoke or similar.
  public static final InterceptionFunction ofInvocation(final Collection<? extends InterceptorMethod> interceptorMethods,
                                                        final BiFunction<? super Object, ? super Object[], ?> terminalBiFunction,
                                                        final Supplier<?> targetBootstrap,
                                                        final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap) {
    return
      ff(interceptorMethods,
         terminalBiFunction,
         targetBootstrap,
         null, // argumentsValidator
         false, // setTarget
         null, // cb,
         null, // mb,
         null, // tb,
         interceptorBindingsBootstrap);
  }

  // ff for function factory :-)
  private static InterceptionFunction ff(final Collection<? extends InterceptorMethod> interceptorMethods,
                                         final BiFunction<? super Object, ? super Object[], ?> tbf,
                                         final Supplier<?> targetBootstrap,
                                         final Consumer<? super Object[]> argumentsValidator,
                                         final boolean setTarget,
                                         final Supplier<? extends Constructor<?>> cb,
                                         final Supplier<? extends Method> mb,
                                         final Supplier<?> tb,
                                         final Supplier<? extends Set<Annotation>> interceptorBindingsBootstrap) {
    final Interceptions i;
    final List<? extends InterceptorMethod> ims;
    if (tbf == null) {
      if (interceptorMethods == null || interceptorMethods.isEmpty()) {
        return Interceptions::returnNull;
      }
      ims = List.copyOf(interceptorMethods);
      // We can get away with hoisting this Property out of the function scope because we know it will never change.
      final Property<Object> target = new Property<Object>(targetBootstrap, false);
      i = new Interceptions(cb, mb, tb, interceptorBindingsBootstrap);
      return a ->
        i.new State(target)
          .new Context(ims.iterator())
          .proceed();
    } else if (interceptorMethods == null || interceptorMethods.isEmpty()) {
      final Property<Object> target = new Property<Object>(targetBootstrap, false);
      return
        argumentsValidator == null ?
        Interceptions::returnNull :
        a -> {
          argumentsValidator.accept(a);
          return tbf.apply(target.get(), a);
        };
    } else {
      ims = List.copyOf(interceptorMethods);
      i = new Interceptions(cb, mb, tb, interceptorBindingsBootstrap);
      if (setTarget) {
        return a -> {
          final State s = i.new State(new Property<>(targetBootstrap, true),
                                      new Property<>(a == null ? Interceptions::emptyObjectArray : () -> a,
                                                     argumentsValidator,
                                                     true));
          final State.Context c = s
            .new Context(ims.iterator(),
                         (t, a2) -> {
                           s.target(tbf.apply(t, a2));
                           return s.target();
            });
          final Object v = c.proceed();
          final Object t = c.getTarget();
          if (v != t && LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "around-construct proceed() return value: " + v + "; returning getTarget() return value: " + t);
          }
          return t;
        };
      }
      // We can get away with hoisting this Property out of the function scope because we know it will never change.
      final Property<Object> target = new Property<Object>(targetBootstrap, false);
      return a -> {
        return
          i.new State(target, new Property<Object[]>(a == null ? Interceptions::emptyObjectArray : () -> a,
                                                     argumentsValidator,
                                                     true))
          .new Context(ims.iterator(), tbf)
            .proceed();
      };
    }
  }

  /**
   * Creates and returns a {@link BiFunction} encapsulating the supplied {@link Constructor}.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param c a {@link Constructor}; must not be {@code null}
   *
   * @return a {@link BiFunction} encapsulating the supplied {@link Constructor}; never {@code null}
   *
   * @exception NullPointerException if any argumebnt is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflectConstructor(Constructor) unreflecting} fails
   */
  public static final BiFunction<Object, Object[], Object> terminalBiFunctionOf(final Lookup lookup, final Constructor<?> c)
    throws IllegalAccessException {
    return terminalBiFunctionOf(privateLookupIn(c.getDeclaringClass(), lookup).unreflectConstructor(c));
  }

  /**
   * Creates and returns a {@link BiFunction} encapsulating the supplied {@link Method}.
   *
   * @param lookup a {@link Lookup}; must not be {@code null}
   *
   * @param m a {@link Method}; must not be {@code null}
   *
   * @return a {@link BiFunction} encapsulating the supplied {@link Method}; never {@code null}
   *
   * @exception NullPointerException if {@code m} is {@code null}
   *
   * @exception IllegalAccessException if {@linkplain Lookup#unreflect(Method) unreflecting} fails
   */
  public static final BiFunction<Object, Object[], Object> terminalBiFunctionOf(final Lookup lookup, final Method m)
    throws IllegalAccessException {
    return terminalBiFunctionOf(privateLookupIn(m.getDeclaringClass(), lookup).unreflect(m));
  }

  /**
   * Creates and returns a {@link BiFunction} encapsulating the supplied {@link MethodHandle}.
   *
   * @param mh a {@link MethodHandle}; must not be {@code null}
   *
   * @return a {@link BiFunction} encapsulating the supplied {@link MethodHandle}; never {@code null}
   *
   * @exception NullPointerException if {@code mh} is {@code null}
   */
  public static final BiFunction<Object, Object[], Object> terminalBiFunctionOf(MethodHandle mh) {
    mh = mh.asType(mh.type().changeReturnType(Object.class));
    final MethodType mt = mh.type();
    final int pc = mt.parameterCount();
    final MethodHandle terminalBiFunction;
    if (pc == 0) {
      terminalBiFunction = mh;
      return (t, a) -> {
        try {
          return terminalBiFunction.invokeExact();
        } catch (final RuntimeException | Error e) {
          throw e;
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e.getMessage(), e);
        } catch (final Throwable e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
      };
    } else if (pc == 1) {
      if (mt.parameterType(0) == Object[].class) {
        terminalBiFunction = mh; // no need to spread
        return (t, a) -> {
          try {
            return terminalBiFunction.invokeExact(a);
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
      terminalBiFunction = mh.asType(mt.changeParameterType(0, Object.class));
      return (t, a) -> {
        try {
          return terminalBiFunction.invokeExact(t);
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
    terminalBiFunction = mh.asType((mt.changeParameterType(0, Object.class))).asSpreader(Object[].class, pc - 1);
    return (t, a) -> {
      try {
        return terminalBiFunction.invokeExact(t, a);
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

  private static final <T, U> T returnNull(final U ignored) {
    return null;
  }

  private static final <T> void throwIllegalStateException(final T ignored) {
    throw new IllegalStateException();

  }


  /*
   * Inner and nested classes.
   */


  private final class State {


    /*
     * Instance fields.
     */


    private final Property<Object> target;

    private final Property<Object[]> arguments;


    /*
     * Constructors.
     */


    private State(final Property<Object> target) {
      this(target, null);
    }

    private State(final Property<Object> target,
                  final Property<Object[]> arguments) {
      super();
      this.target = Objects.requireNonNull(target, "target");
      this.arguments = arguments == null ? EMPTY_OBJECT_ARRAY_PROPERTY : arguments;
    }


    /*
     * Instance methods.
     */


    private final Object target() {
      return this.target.get();
    }

    private final void target(final Object target) {
      this.target.accept(target);
    }

    private final Object[] arguments() {
      final Object[] a = this.arguments.get();
      return a == null ? EMPTY_OBJECT_ARRAY : a.clone();
    }

    private final void arguments(final Object[] a) {
      this.arguments.accept(a == null ? EMPTY_OBJECT_ARRAY : a.clone());
    }


    /*
     * Inner and nested classes.
     */


    private final class Context implements InvocationContext {


      /*
       * Instance fields.
       */


      private final Supplier<?> proceeder;


      /*
       * Constructors.
       */


      private Context(final Iterator<? extends InterceptorMethod> iterator) {
        this(iterator, (Supplier<?>)Interceptions::returnNull);
      }

      private Context(final Iterator<? extends InterceptorMethod> iterator,
                      final BiFunction<? super Object, ? super Object[], ?> tbf) {
        this(iterator, () -> tbf.apply(target(), arguments()));
      }

      private Context(final Iterator<? extends InterceptorMethod> iterator,
                      final Supplier<?> f) {
        super();
        if (iterator == null || !iterator.hasNext()) {
          this.proceeder = f == null ? Interceptions::returnNull : f;
        } else {
          final InterceptorMethod im = Objects.requireNonNull(iterator.next());
          final Context c = new Context(iterator, f);
          this.proceeder = () -> {
            try {
              return im.intercept(c);
            } catch (final RuntimeException e) {
              throw e;
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new InterceptorException(e.getMessage(), e);
            } catch (final Exception e) {
              throw new InterceptorException(e.getMessage(), e);
            }
          };
        }
      }


      /*
       * Instance methods.
       */


      @Override // InvocationContext
      public final Constructor<?> getConstructor() {
        return constructorBootstrap.get();
      }

      @Override // InvocationContext
      public final Map<String, Object> getContextData() {
        return data;
      }

      @Override // InvocationContext
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
      public final Object proceed() {
        return this.proceeder.get();
      }

      @Override // InvocationContext
      public final void setParameters(final Object[] arguments) {
        arguments(arguments);
      }

    }

  }

  private static final class Property<T> implements Consumer<T>, Supplier<T> {


    /*
     * Static fields.
     */


    private static final VarHandle VALUE;

    static {
      try {
        VALUE = lookup().findVarHandle(Property.class, "value", Optional.class);
      } catch (final IllegalAccessException | NoSuchFieldException e) {
        throw (ExceptionInInitializerError)new ExceptionInInitializerError(e.getMessage()).initCause(e);
      }
    }


    /*
     * Instance fields.
     */


    // set only through/using VALUE
    private volatile Optional<T> value;

    private final Supplier<T> reader;

    private final Consumer<? super T> writer;


    /*
     * Constructors.
     */


    private Property(final Supplier<? extends T> bootstrap, final boolean mutable) {
      this(bootstrap, null, mutable);
    }

    private Property(final Supplier<? extends T> bootstrap, final Consumer<? super T> validator, final boolean mutable) {
      super();
      if (bootstrap == null) {
        if (mutable) {
          VALUE.set(this, Optional.empty()); // no need for volatile semantics here
          this.reader = () -> ((Optional<T>)VALUE.getVolatile(this)).orElse(null);
          if (validator == null) {
            this.writer = v -> {
              VALUE.setVolatile(this, Optional.ofNullable(v));
            };
          } else {
            validator.accept(null); // make sure the Optional.empty() assignment above was OK
            this.writer = v -> {
              validator.accept(v);
              VALUE.setVolatile(this, Optional.ofNullable(v));
            };
          }
        } else {
          this.reader = Interceptions::returnNull;
          this.writer = Interceptions::throwIllegalStateException;
        }
      } else if (mutable) {
        if (validator == null) {
          this.reader = () -> {
            Optional<T> o = (Optional<T>)VALUE.getVolatile(this);
            if (o == null) {
              o = Optional.ofNullable(bootstrap.get());
              if (!VALUE.compareAndSet(this, null, o)) {
                o = (Optional<T>)VALUE.getVolatile(this);
              }
            }
            return o.orElse(null);
          };
          this.writer = v -> {
            VALUE.setVolatile(this, Optional.ofNullable(v));
          };
        } else {
          this.reader = () -> {
            Optional<T> o = (Optional<T>)VALUE.getVolatile(this);
            if (o == null) {
              final T v = bootstrap.get();
              validator.accept(v);
              o = Optional.ofNullable(v);
              if (!VALUE.compareAndSet(this, null, o)) {
                o = (Optional<T>)VALUE.getVolatile(this);
              }
            }
            return o.orElse(null);
          };
          this.writer = v -> {
            validator.accept(v);
            VALUE.setVolatile(this, Optional.ofNullable(v));
          };
        }
      } else { // !mutable
        this.writer = Interceptions::throwIllegalStateException;
        if (validator == null) {
          this.reader = () -> {
            Optional<T> o = (Optional<T>)VALUE.getVolatile(this);
            if (o == null) {
              o = Optional.ofNullable(bootstrap.get());
              if (!VALUE.compareAndSet(this, null, o)) {
                o = (Optional<T>)VALUE.getVolatile(this);
              }
            }
            return o.orElse(null);
          };
        } else {
          this.reader = () -> {
            Optional<T> o = (Optional<T>)VALUE.getVolatile(this);
            if (o == null) {
              final T v = bootstrap.get();
              validator.accept(v);
              o = Optional.ofNullable(v);
              if (!VALUE.compareAndSet(this, null, o)) {
                o = (Optional<T>)VALUE.getVolatile(this);
              }
            }
            return o.orElse(null);
          };
        }
      }
    }


    /*
     * Instance methods.
     */


    @Override // Supplier<T>
    public final T get() {
      return this.reader.get();
    }

    @Override // Consumer<T>
    public final void accept(final T value) {
      this.writer.accept(value);
    }

    @Override
    public final String toString() {
      return String.valueOf(this.get());
    }

  }

}
