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

/**
 * A {@linkplain FunctionalInterface functional interface} whose implementations represent an interception of some kind.
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 *
 * @see #apply(Object...)
 */
@FunctionalInterface
public interface InterceptionFunction {

  /**
   * Applies the interception represented by this {@link InterceptionFunction}, with the supplied arguments, and returns
   * the result.
   *
   * @param arguments arguments to the interception; must not be a {@code null} array
   *
   * @return the result of the interception, which may be {@code null}
   *
   * @exception NullPointerException if {@code arguments} is a {@code null} array
   *
   * @see Interceptions
   */
  public Object apply(final Object... arguments);

}
