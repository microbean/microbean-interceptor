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


  /*
   * Static fields.
   */


  /**
   * The default priority returned by the default implementation of
   * the {@link #priority()} method.
   */
  public static final int DEFAULT_PRIORITY = 0;


  /*
   * Instance methods.
   */


  /**
   * Returns the priority for this {@link Prioritized} implementation.
   *
   * <p>The smallest number "wins" ("quality is job one"; "first
   * place"; "X is your first priority").</p>
   *
   * <p>Note particularly that a negative priority trumps a positive
   * one.</p>
   *
   * @return the priority for this {@link Prioritized} implementation
   */
  public default int priority() {
    return DEFAULT_PRIORITY;
  }

  /**
   * Returns a negative {@code int} if this {@link Prioritized}
   * implementation is considered to be less important than the
   * supplied {@link Prioritized}, {@code 0} if it is considered to be
   * of equal importance, and a positive {@code int} if it is
   * considered to be more important.
   *
   * @param other the {@link Prioritized} to compare against; may be
   * {@code null}
   *
   * @return a negative {@code int} if this {@link Prioritized}
   * implementation is considered to be less important than the
   * supplied {@link Prioritized}, {@code 0} if it is considered to be
   * of equal importance, and a positive {@code int} if it is
   * considered to be more important
   */
  @Override // Comparable<Prioritized>
  public default int compareTo(final Prioritized other) {
    return Comparator.INSTANCE.compare(this, other);
  }


  /*
   * Nested classes.
   */


  /**
   * A {@link java.util.Comparator} that compares {@linkplain
   * Prioritized#priority() priorities}.
   *
   * <p>Instances of this class are consistent with equals.</p>
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   */
  public static final class Comparator implements java.util.Comparator<Prioritized> {


    /*
     * Static fields.
     */


    /**
     * The sole instance of this class.
     *
     * @nullability This field is never {@code null}.
     *
     * @threadsafety The value of this field is safe for concurrent
     * use by multiple threads.
     */
    public static final java.util.Comparator<Prioritized> INSTANCE = new Comparator();


    /*
     * Constructors.
     */


    private Comparator() {
      super();
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a negative {@code int} if the first argument is
     * considered to be less important than the second argument,
     * {@code 0} if it is considered to be of equal importance, and a
     * positive {@code int} if it is considered to be more important.
     *
     * @param p0 the first {@link Prioritized}; may be {@code null}
     *
     * @param p1 the second {@link Prioritized}; may be {@code null}
     *
     * @return a negative {@code int} if the first argument is
     * considered to be less important than the second argument,
     * {@code 0} if it is considered to be of equal importance, and a
     * positive {@code int} if it is considered to be more important
     */
    @Override
    public final int compare(final Prioritized p0, final Prioritized p1) {
      if (p0 == null) {
        return p1 == null ? 0 : 1;
      } else if (p1 == null) {
        return -1;
      } else {
        return p0.equals(p1) ? 0 : Integer.compare(p0.priority(), p1.priority());
      }
    }

  }

}
