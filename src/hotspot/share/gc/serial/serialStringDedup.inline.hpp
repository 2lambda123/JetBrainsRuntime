/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifndef SHARE_GC_SERIAL_STRINGDEDUP_INLINE_HPP
#define SHARE_GC_SERIAL_STRINGDEDUP_INLINE_HPP

#include "gc/serial/serialStringDedup.hpp"

#include "classfile/javaClasses.inline.hpp"
#include "oops/oop.inline.hpp"

bool SerialStringDedup::is_candidate_from_evacuation(oop obj,
                                                     bool obj_is_tenured) {
  return StringDedup::is_enabled() &&
         java_lang_String::is_instance_inlined(obj) &&
         (obj_is_tenured ?
          StringDedup::is_below_threshold_age(obj->age()) :
          StringDedup::is_threshold_age(obj->age()));
}

#endif // SHARE_GC_SERIAL_STRINGDEDUP_INLINE_HPP
