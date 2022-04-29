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
 * A {@link RuntimeException} indicating that an error has occurred
 * while setting up an interception chain.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 */
public class InterceptorException extends RuntimeException {

  /**
   * The version of this class for {@linkplain java.io.Serializable
   * serialization} purposes.
   */
  private static final long serialVersionUID = 1L;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link InterceptorException}.
   */
  public InterceptorException() {
    super();
  }

  /**
   * Creates a new {@link InterceptorException}.
   *
   * @param message a message describing the error; may be {@code
   * null}
   */
  public InterceptorException(final String message) {
    super(message);
  }

  /**
   * Creates a new {@link InterceptorException}.
   *
   * @param cause the {@link Throwable} that caused this {@link
   * InterceptorException} to be thrown; may be {@code null}
   */
  public InterceptorException(final Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new {@link InterceptorException}.
   *
   * @param message a message describing the error; may be {@code
   * null}
   *
   * @param cause the {@link Throwable} that caused this {@link
   * InterceptorException} to be thrown; may be {@code null}
   */
  public InterceptorException(final String message, final Throwable cause) {
    super(message, cause);
  }

}
