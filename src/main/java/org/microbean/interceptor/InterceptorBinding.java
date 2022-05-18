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

import java.util.Map;

import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;

import org.microbean.qualifier.Binding;

import static java.lang.constant.ConstantDescs.CD_Map;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;

import static java.lang.constant.DirectMethodHandleDesc.Kind.STATIC;

import static org.microbean.interceptor.ConstantDescs.CD_InterceptorBinding;

/**
 * A {@link Binding} used to qualify objects.
 *
 * <p>This is a <a
 * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class.</p>
 *
 * @param <K> the type of a {@link InterceptorBinding}'s {@linkplain
 * #attributes() attribute keys}
 *
 * @param <V> the type of a {@link InterceptorBinding}'s {@linkplain #value()
 * value} and of its {@linkplain #attributes() attribute values}
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see Binding
 */
public final class InterceptorBinding<K extends Comparable<? super K>, V> extends Binding<K, V, InterceptorBinding<K, V>> {

  private InterceptorBinding(final String name,
                    final V value,
                    final Map<? extends K, ? extends V> attributes,
                    final Map<? extends K, ? extends V> info) {
    super(name, value, attributes, info);
  }

  /**
   * Returns a {@link MethodHandleDesc} describing the constructor or
   * {@code static} method that will be used to create a <a
   * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/package-summary.html#condycon">dynamic
   * constant</a> representing this {@link InterceptorBinding}.
   *
   * <p>End users have no need to call this method.</p>
   *
   * @return a {@link MethodHandleDesc} describing the constructor or
   * {@code static} method that will be used to create a <a
   * href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/package-summary.html#condycon">dynamic
   * constant</a> representing this {@link InterceptorBinding}
   *
   * @nullability This method does not return {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  @Override // Binding<K, V, InterceptorBinding<K, V>>
  protected final MethodHandleDesc describeConstructor() {
    return
      MethodHandleDesc.ofMethod(STATIC,
                                CD_InterceptorBinding,
                                "of",
                                MethodTypeDesc.of(CD_InterceptorBinding, CD_String, CD_Object, CD_Map, CD_Map));
  }

  /**
   * Returns a {@link InterceptorBinding}, which may or may not be
   * newly created, representing the supplied arguments.
   *
   * @param <K> the type of the {@link InterceptorBinding}'s
   * {@linkplain #attributes() attribute keys}
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain #value() value} and of its {@linkplain #attributes()
   * attribute values}
   *
   * @param name the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#name() name}; must not be {@code null}
   *
   * @return a {@link InterceptorBinding}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final <K extends Comparable<? super K>, V> InterceptorBinding<K, V> of(final String name) {
    return of(name, null, null, null);
  }

  /**
   * Returns a {@link InterceptorBinding}, which may or may not be
   * newly created, representing the supplied arguments.
   *
   * @param <K> the type of the {@link InterceptorBinding}'s
   * {@linkplain #attributes() attribute keys}
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain #value() value} and of its {@linkplain #attributes()
   * attribute values}
   *
   * @param name the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#name() name}; must not be {@code null}
   *
   * @param value the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#value() value}; may be {@code null}
   *
   * @return a {@link InterceptorBinding}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final <K extends Comparable<? super K>, V> InterceptorBinding<K, V> of(final String name, final V value) {
    return of(name, value, null, null);
  }

  /**
   * Returns a {@link InterceptorBinding}, which may or may not be
   * newly created, representing the supplied arguments.
   *
   * @param <K> the type of the {@link InterceptorBinding}'s
   * {@linkplain #attributes() attribute keys}
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain #value() value} and of its {@linkplain #attributes()
   * attribute values}
   *
   * @param name the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#name() name}; must not be {@code null}
   *
   * @param value the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#value() value}; may be {@code null}
   *
   * @param attributes the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#attributes() attributes}; may be {@code null}
   *
   * @return a {@link InterceptorBinding}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final <K extends Comparable<? super K>, V> InterceptorBinding<K, V> of(final String name,
                                                                                       final V value,
                                                                                       final Map<? extends K, ? extends V> attributes) {
    return of(name, value, attributes, null);
  }

  /**
   * Returns a {@link InterceptorBinding}, which may or may not be
   * newly created, representing the supplied arguments.
   *
   * @param <K> the type of the {@link InterceptorBinding}'s
   * {@linkplain #attributes() attribute keys}
   *
   * @param <V> the type of the {@link InterceptorBinding}'s
   * {@linkplain #value() value} and of its {@linkplain #attributes()
   * attribute values}
   *
   * @param name the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#name() name}; must not be {@code null}
   *
   * @param value the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#value() value}; may be {@code null}
   *
   * @param attributes the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#attributes() attributes}; may be {@code null}
   *
   * @param info the {@link InterceptorBinding}'s {@linkplain
   * InterceptorBinding#info() informational attributes}; may be
   * {@code null}
   *
   * @return a {@link InterceptorBinding}
   *
   * @nullability This method never returns {@code null}.
   *
   * @idempotency This method is idempotent and deterministic.
   *
   * @threadsafety This method is safe for concurrent use by multiple
   * threads.
   */
  public static final <K extends Comparable<? super K>, V> InterceptorBinding<K, V> of(final String name,
                                                                                       final V value,
                                                                                       final Map<? extends K, ? extends V> attributes,
                                                                                       final Map<? extends K, ? extends V> info) {
    return new InterceptorBinding<>(name, value, attributes, info);
  }

}
