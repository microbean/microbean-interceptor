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

import jakarta.interceptor.InvocationContext;

/**
 * An interface whose implementations perform interceptions.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #intercept(Object, InvocationContext)
 */
@FunctionalInterface
public interface InterceptorFunction extends Prioritized {

  /**
   * Intercepts the interception event represented by the supplied
   * {@code invocationContext} and returns the result.
   *
   * @param interceptor the interceptor performing the interception;
   * must not be {@code null}
   *
   * @param invocationContext the {@link InvocationContext}
   * representing the interception event; must not be {@code null}
   *
   * @return the result of the interception; may be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception Exception if an error occurs
   */
  public Object intercept(final Object interceptor, final InvocationContext invocationContext) throws Exception;

}
