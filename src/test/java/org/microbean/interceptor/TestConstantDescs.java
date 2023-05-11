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

import java.lang.constant.ClassDesc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestConstantDescs {

  private TestConstantDescs() {
    super();
  }

  @Test
  final void testConstantDescs() throws ClassNotFoundException, IllegalAccessException {
    for (final Field f : ConstantDescs.class.getFields()) {
      assertSame(ClassDesc.class, f.getType());
      assertTrue(Modifier.isStatic(f.getModifiers()));
      final ClassDesc cd = (ClassDesc)f.get(null);
      final String packageName = cd.packageName();
      assertEquals(ConstantDescs.class.getPackage().getName(), packageName);
      Class.forName(packageName + "." + cd.displayName());
    }
  }

}
