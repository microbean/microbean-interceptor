/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 microBean™.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.interceptor.InvocationContext;

public class SimpleInvocationContext implements InvocationContext {

  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private final Constructor<?> constructor;

  private final Map<String, Object> contextData;

  private final Method method;

  private volatile Object[] parameters;

  private volatile Object target;

  private final Object timer;

  public SimpleInvocationContext() {
    this(null, null, null, null, null, null);
  }

  public SimpleInvocationContext(final Constructor<?> constructor,
                                 final ConcurrentMap<String, Object> contextData,
                                 final Method method,
                                 final Object[] parameters,
                                 final Object target,
                                 final Object timer) {
    super();
    this.constructor = constructor;
    this.contextData = contextData == null ? new ConcurrentHashMap<>() : contextData;
    this.method = method;
    this.parameters = parameters == null ? EMPTY_OBJECT_ARRAY : parameters.clone();
    this.target = target;
    this.timer = timer;
  }

  @Override
  public Constructor<?> getConstructor() {
    return this.constructor;
  }

  @Override
  public Map<String, Object> getContextData() {
    return this.contextData;
  }

  @Override
  public Method getMethod() {
    return this.method;
  }

  @Override
  public Object[] getParameters() {
    return this.parameters.clone();
  }

  @Override
  public Object getTarget() {
    return this.target;
  }

  public void setTarget(final Object target) {
    this.target = Objects.requireNonNull(target, "target");
  }

  @Override
  public Object getTimer() {
    return this.timer;
  }

  @Override
  public Object proceed() throws Exception {
    final Method m = this.getMethod();
    if (m == null) {
      final Constructor<?> c = this.getConstructor();
      if (c == null) {
        return null;
      }
      this.setTarget(c.newInstance(this.getParameters()));
      return this.getTarget();
    }
    return m.invoke(this.getTarget(), this.getParameters());
  }

  @Override
  public void setParameters(final Object[] parameters) {
    this.parameters = parameters == null ? EMPTY_OBJECT_ARRAY : parameters.clone();
  }

}
