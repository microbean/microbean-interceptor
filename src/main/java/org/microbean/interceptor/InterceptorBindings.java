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

import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import java.util.function.Function;

import org.microbean.qualifier.Bindings;

import static java.lang.constant.ConstantDescs.CD_Collection;

import static java.lang.constant.DirectMethodHandleDesc.Kind.STATIC;

import static org.microbean.interceptor.ConstantDescs.CD_InterceptorBindings;
import static org.microbean.interceptor.ConstantDescs.CD_Iterable;

/**
 * An immutable {@link Bindings} containing {@link InterceptorBinding}
 * instances.
 *
 * <p>This is a <a
 * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class.</p>
 *
 * @param <V> the type of a {@link InterceptorBinding}'s {@linkplain
 * InterceptorBinding#attributes() attribute values}
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see Bindings
 */
public final class InterceptorBindings<V> extends Bindings<V, InterceptorBinding<V>> {


  /*
   * Static fields.
   */


  private static final InterceptorBindings<?> EMPTY = new InterceptorBindings<>(List.of());


  /*
   * Constructors.
   */


  private InterceptorBindings(final Iterable<? extends InterceptorBinding<V>> interceptorBindings) {
    super(interceptorBindings);
  }


  /*
   * Instance methods.
   */


  /**
   * Returns a <strong>usually new</strong> {@link
   * InterceptorBindings} with this {@link InterceptorBindings}'
   * entries and an additional entry consisting of the supplied {@link
   * InterceptorBinding}.
   *
   * <p>The returned {@link InterceptorBindings} <strong>will be
   * new</strong> unless {@code interceptorBinding} is {@code null},
   * in which case {@code this} will be returned.</p>
   *
   * @param interceptorBinding a {@link InterceptorBinding}; may be
   * {@code null} in which case {@code this} will be returned
   *
   * @return a {@link InterceptorBindings} with this {@link
   * InterceptorBindings}' entries and an additional entry consisting
   * of the supplied {@link InterceptorBinding}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   *
   * @see #plus(Iterable)
   */
  public final InterceptorBindings<V> plus(final InterceptorBinding<V> interceptorBinding) {
    if (interceptorBinding == null) {
      return this;
    } else if (this.isEmpty()) {
      return of(interceptorBinding);
    } else {
      return this.plus(List.of(interceptorBinding));
    }
  }

  /**
   * Returns a <strong>usually new</strong> {@link
   * InterceptorBindings} with this {@link InterceptorBindings}'
   * entries and additional entries represented by the supplied {@code
   * interceptorBindings}.
   *
   * <p>The returned {@link InterceptorBindings} <strong>will be
   * new</strong> unless {@code interceptorBinding} is {@code null},
   * in which case {@code this} will be returned.</p>
   *
   * @param interceptorBindings additional {@link
   * InterceptorBinding}s; may be {@code null} in which case {@code
   * this} will be returned
   *
   * @return a {@link InterceptorBindings} with this {@link
   * InterceptorBindings}' entries and additional entries represented
   * by the supplied {@code interceptorBindings}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public final InterceptorBindings<V> plus(final Iterable<? extends InterceptorBinding<V>> interceptorBindings) {
    if (interceptorBindings == null) {
      return this;
    } else if (this.isEmpty()) {
      return of(interceptorBindings);
    }
    final Collection<InterceptorBinding<V>> newInterceptorBindings = new TreeSet<>();
    for (final InterceptorBinding<V> q : this) {
      newInterceptorBindings.add(q);
    }
    for (final InterceptorBinding<V> q : interceptorBindings) {
      newInterceptorBindings.add(q);
    }
    return of(newInterceptorBindings);
  }

  /**
   * Returns a <strong>usually new</strong> {@link
   * InterceptorBindings} whose {@link InterceptorBinding}s'
   * {@linkplain InterceptorBinding#attributes() attribute keys} are
   * prefixed with the supplied {@code prefix}.
   *
   * <p>If this {@link InterceptorBindings} is {@linkplain #isEmpty()
   * empty}, then {@code this} is returned.</p>
   *
   * @param prefix a prefix; if {@code null} then {@code this} will be
   * returned
   *
   * @return a <strong>usually new</strong> {@link
   * InterceptorBindings} whose {@link InterceptorBinding}s'
   * {@linkplain InterceptorBinding#attributes() attribute keys} are
   * prefixed with the supplied {@code prefix}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic,
   * assuming the supplied {@link Function} is.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads
   */
  public final InterceptorBindings<V> withPrefix(final String prefix) {
    if (prefix == null || this.isEmpty()) {
      return this;
    }
    return this.withPrefix(q -> prefix + q.name());
  }

  /**
   * Returns a <strong>usually new</strong> {@link
   * InterceptorBindings} whose {@link InterceptorBinding}s'
   * {@linkplain InterceptorBinding#attributes() attribute keys} are
   * produced by the supplied {@link Function}, which is expected to
   * prepend a prefix to the original key and return the result.
   *
   * <p>If this {@link InterceptorBindings} is {@linkplain #isEmpty()
   * empty}, then {@code this} is returned.</p>
   *
   * @param f a deterministic, idempotent {@link Function} that
   * accepts keys drawn from this {@link InterceptorBindings}' {@link
   * InterceptorBinding}s' {@linkplain InterceptorBinding#attributes()
   * attribute keys} and returns a non-{@code null} prefixed version
   * of that key; may be {@code null} in which case {@code this} will
   * be returned
   *
   * @return a <strong>usually new</strong> {@link
   * InterceptorBindings} whose {@link InterceptorBinding}s'
   * {@linkplain InterceptorBinding#attributes() attribute keys} have
   * been prefixed by the actions of the supplied {@link Function}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic,
   * assuming the supplied {@link Function} is.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads, assuming the supplied {@link Function} is
   */
  public final InterceptorBindings<V> withPrefix(final Function<? super InterceptorBinding<V>, ? extends String> f) {
    if (f == null || this.isEmpty()) {
      return this;
    }
    final Collection<InterceptorBinding<V>> newInterceptorBindings = new TreeSet<>();
    for (final InterceptorBinding<V> q : this) {
      newInterceptorBindings.add(InterceptorBinding.of(f.apply(q),
                                                       q.value(),
                                                       q.attributes(),
                                                       q.info()));
    }
    return of(newInterceptorBindings);
  }

  /**
   * Returns a {@link MethodHandleDesc} describing the constructor or
   * {@code static} method that will be used to create a <a
   * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/package-summary.html#condycon">dynamic
   * constant</a> representing this {@link InterceptorBindings}.
   *
   * @return a {@link MethodHandleDesc} describing the constructor or
   * {@code static} method that will be used to create a <a
   * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/package-summary.html#condycon">dynamic
   * constant</a> representing this {@link InterceptorBindings}
   *
   * @nullability This method does not, and its overrides must not,
   * return {@code null}.
   *
   * @idempotency This method is, and its overrides must be,
   * idempotent and deterministic.
   *
   * @threadsafety This method is, and its overrides must be, safe for
   * concurrent use by multiple threads.
   */
  @Override // Bindings<V, InterceptorBinding<V>>
  protected final MethodHandleDesc describeConstructor() {
    return
      MethodHandleDesc.ofMethod(STATIC,
                                CD_InterceptorBindings,
                                "of",
                                MethodTypeDesc.of(CD_InterceptorBindings, CD_Iterable));
  }


  /*
   * Static methods.
   */


  /**
   * Returns a {@link InterceptorBindings}, which may or may not be
   * newly created, whose {@link #isEmpty() isEmpty()} method will
   * return {@code true}.
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain InterceptorBinding#attributes() attribute values}
   *
   * @return a {@link InterceptorBindings}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  @SuppressWarnings("unchecked")
  public static final <V> InterceptorBindings<V> of() {
    return (InterceptorBindings<V>)EMPTY;
  }

  /**
   * Returns a {@link InterceptorBindings}, which may or may not be
   * newly created, representing the supplied arguments.
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain InterceptorBinding#attributes() attribute values}
   *
   * @param interceptorBinding the sole {@link InterceptorBinding} the
   * {@link InterceptorBindings} will contain; must not be {@code
   * null}
   *
   * @return a {@link InterceptorBindings}
   *
   * @exception NullPointerException if {@code interceptorBinding} is {@code
   * null}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final <V> InterceptorBindings<V> of(final InterceptorBinding<V> interceptorBinding) {
    return of(List.of(Objects.requireNonNull(interceptorBinding, "interceptorBinding")));
  }

  /**
   * Returns a {@link InterceptorBindings}, which may or may not be
   * newly created, representing the supplied arguments.
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain InterceptorBinding#attributes() attribute values}
   *
   * @param interceptorBindings an {@link Iterable} representing
   * {@link InterceptorBinding} instances the {@link
   * InterceptorBindings} will contain; may be {@code null}
   *
   * @return a {@link InterceptorBindings}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  // This method is used by the describeConstructor() method.
  public static final <V> InterceptorBindings<V> of(final Iterable<? extends InterceptorBinding<V>> interceptorBindings) {
    if (interceptorBindings == null) {
      return of();
    }
    final Iterator<? extends InterceptorBinding<V>> i = interceptorBindings.iterator();
    if (i.hasNext()) {
      final Collection<InterceptorBinding<V>> newInterceptorBindings = new TreeSet<>();
      newInterceptorBindings.add(i.next());
      while (i.hasNext()) {
        newInterceptorBindings.add(i.next());
      }
      return new InterceptorBindings<>(newInterceptorBindings);
    }
    return of();
  }

}
