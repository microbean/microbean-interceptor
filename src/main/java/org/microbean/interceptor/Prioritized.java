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

/**
 * An interface indicating that its implementations can supply an
 * {@code int} indicating a relative priority (smallest number wins;
 * "quality is job one"; "first place"; "X is your first priority").
 *
 * <p>Note particularly that a negative priority trumps a positive
 * one.</p>
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see #priority()
 */
public interface Prioritized extends Comparable<Prioritized> {

  public static final int DEFAULT_PRIORITY = 0;

  public default int priority() {
    return DEFAULT_PRIORITY;
  }

  @Override // Comparable<Prioritized>
  public default int compareTo(final Prioritized other) {
    return Comparator.INSTANCE.compare(this, other);
  }


  /*
   * Nested classes.
   */


  public static final class Comparator implements java.util.Comparator<Prioritized> {

    public static final java.util.Comparator<Prioritized> INSTANCE = new Comparator();

    private Comparator() {
      super();
    }

    @Override
    public final int compare(final Prioritized f1, final Prioritized f2) {
      if (f1 == null) {
        return f2 == null ? 0 : 1;
      } else if (f2 == null) {
        return -1;
      } else {
        return f1.equals(f2) ? 0 : Integer.compare(f1.priority(), f2.priority());
      }
    }

  }

}
