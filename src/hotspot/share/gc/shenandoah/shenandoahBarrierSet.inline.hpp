/*
 * Copyright (c) 2015, Red Hat, Inc. and/or its affiliates.
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
 *
 */

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP

#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahConnectionMatrix.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"

inline oop ShenandoahBarrierSet::resolve_forwarded_not_null(oop p) {
  return BrooksPointer::forwardee(p);
}

inline oop ShenandoahBarrierSet::resolve_forwarded(oop p) {
  if (((HeapWord*) p) != NULL) {
    return resolve_forwarded_not_null(p);
  } else {
    return p;
  }
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_cmpxchg_in_heap(oop new_value, T* addr, oop compare_value) {
  oop res;
  oop expected = compare_value;
  do {
    compare_value = expected;
    res = Raw::oop_atomic_cmpxchg(new_value, addr, compare_value);
    expected = res;
  } while ((! oopDesc::unsafe_equals(compare_value, expected)) && oopDesc::unsafe_equals(BarrierSet::barrier_set()->read_barrier(compare_value), BarrierSet::barrier_set()->read_barrier(expected)));
  if (oopDesc::unsafe_equals(expected, compare_value)) {
    if (ShenandoahSATBBarrier && !oopDesc::is_null(compare_value)) {
      ShenandoahBarrierSet::enqueue(compare_value);
    }
    if (UseShenandoahMatrix && ! oopDesc::is_null(new_value)) {
      ShenandoahConnectionMatrix* matrix = ShenandoahHeap::heap()->connection_matrix();
      matrix->set_connected(addr, new_value);
    }
  }
  return res;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
inline oop ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_atomic_xchg_in_heap(oop new_value, T* addr) {
  oop previous = Raw::oop_atomic_xchg(new_value, addr);
  if (ShenandoahSATBBarrier) {
    if (!oopDesc::is_null(previous)) {
      ShenandoahBarrierSet::enqueue(previous);
    }
  }
  if (UseShenandoahMatrix && ! oopDesc::is_null(new_value)) {
    ShenandoahConnectionMatrix* matrix = ShenandoahHeap::heap()->connection_matrix();
    matrix->set_connected(addr, new_value);
  }
  return previous;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
bool ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::arraycopy_in_heap(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
  if (!oopDesc::is_null(src_obj)) {
    size_t src_offset = pointer_delta((void*) src, (void*) src_obj, sizeof(T));
    src_obj = arrayOop(((ShenandoahBarrierSet*) BarrierSet::barrier_set())->read_barrier(src_obj));
    src =  ((T*)(void*) src_obj) + src_offset;
  }
  if (!oopDesc::is_null(dst_obj)) {
    size_t dst_offset = pointer_delta((void*) dst, (void*) dst_obj, sizeof(T));
    dst_obj = arrayOop(((ShenandoahBarrierSet*) BarrierSet::barrier_set())->write_barrier(dst_obj));
    dst = ((T*)(void*) dst_obj) + dst_offset;
  }
  return Raw::arraycopy(src, dst, length);
}

template <typename T>
bool ShenandoahBarrierSet::arraycopy_loop_1(T* src, T* dst, size_t length, Klass* bound,
                                            bool checkcast, bool satb, bool matrix, ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode) {
  if (checkcast) {
    return arraycopy_loop_2<T, true>(src, dst, length, bound, satb, matrix, storeval_mode);
  } else {
    return arraycopy_loop_2<T, false>(src, dst, length, bound, satb, matrix, storeval_mode);
  }
}

template <typename T, bool CHECKCAST>
bool ShenandoahBarrierSet::arraycopy_loop_2(T* src, T* dst, size_t length, Klass* bound,
                                            bool satb, bool matrix, ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode) {
  if (satb) {
    return arraycopy_loop_3<T, CHECKCAST, true>(src, dst, length, bound, matrix, storeval_mode);
  } else {
    return arraycopy_loop_3<T, CHECKCAST, false>(src, dst, length, bound, matrix, storeval_mode);
  }
}

template <typename T, bool CHECKCAST, bool SATB>
bool ShenandoahBarrierSet::arraycopy_loop_3(T* src, T* dst, size_t length, Klass* bound,
                                            bool matrix, ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode) {
  if (matrix) {
    return arraycopy_loop_4<T, CHECKCAST, SATB, true>(src, dst, length, bound, storeval_mode);
  } else {
    return arraycopy_loop_4<T, CHECKCAST, SATB, false>(src, dst, length, bound, storeval_mode);
  }
}

template <typename T, bool CHECKCAST, bool SATB, bool MATRIX>
bool ShenandoahBarrierSet::arraycopy_loop_4(T* src, T* dst, size_t length, Klass* bound,
                                            ShenandoahBarrierSet::ArrayCopyStoreValMode storeval_mode) {
  switch (storeval_mode) {
    case NONE:
      return arraycopy_loop<T, CHECKCAST, SATB, MATRIX, NONE>(src, dst, length, bound);
    case READ_BARRIER:
      return arraycopy_loop<T, CHECKCAST, SATB, MATRIX, READ_BARRIER>(src, dst, length, bound);
    case WRITE_BARRIER_MAYBE_ENQUEUE:
      return arraycopy_loop<T, CHECKCAST, SATB, MATRIX, WRITE_BARRIER_MAYBE_ENQUEUE>(src, dst, length, bound);
    case WRITE_BARRIER_ALWAYS_ENQUEUE:
      return arraycopy_loop<T, CHECKCAST, SATB, MATRIX, WRITE_BARRIER_ALWAYS_ENQUEUE>(src, dst, length, bound);
    default:
      ShouldNotReachHere();
      return true; // happy compiler
  }
}

template <typename T, bool CHECKCAST, bool SATB, bool MATRIX, ShenandoahBarrierSet::ArrayCopyStoreValMode STOREVAL_MODE>
bool ShenandoahBarrierSet::arraycopy_loop(T* src, T* dst, size_t length, Klass* bound) {
  Thread* thread = Thread::current();

  ShenandoahEvacOOMScope oom_evac_scope;

  // We need to handle four cases:
  //
  // a) src < dst, intersecting, can only copy backward only
  //   [...src...]
  //         [...dst...]
  //
  // b) src < dst, non-intersecting, can copy forward/backward
  //   [...src...]
  //              [...dst...]
  //
  // c) src > dst, intersecting, can copy forward only
  //         [...src...]
  //   [...dst...]
  //
  // d) src > dst, non-intersecting, can copy forward/backward
  //              [...src...]
  //   [...dst...]
  //
  if (src > dst) {
    // copy forward:
    T* cur_src = src;
    T* cur_dst = dst;
    T* src_end = src + length;
    for (; cur_src < src_end; cur_src++, cur_dst++) {
      if (!arraycopy_element<T, CHECKCAST, SATB, MATRIX, STOREVAL_MODE>(cur_src, cur_dst, bound, thread)) {
        return false;
      }
    }
  } else {
    // copy backward:
    T* cur_src = src + length - 1;
    T* cur_dst = dst + length - 1;
    for (; cur_src >= src; cur_src--, cur_dst--) {
      if (!arraycopy_element<T, CHECKCAST, SATB, MATRIX, STOREVAL_MODE>(cur_src, cur_dst, bound, thread)) {
        return false;
      }
    }
  }
  return true;
}

template <typename T, bool CHECKCAST, bool SATB, bool MATRIX, ShenandoahBarrierSet::ArrayCopyStoreValMode STOREVAL_MODE>
bool ShenandoahBarrierSet::arraycopy_element(T* cur_src, T* cur_dst, Klass* bound, Thread* thread) {
  T o = oopDesc::load_heap_oop(cur_src);

  if (SATB) {
    T prev = oopDesc::load_heap_oop(cur_dst);
    if (!oopDesc::is_null(prev)) {
      oop prev_obj = oopDesc::decode_heap_oop_not_null(prev);
      enqueue(prev_obj);
    }
  }

  if (!oopDesc::is_null(o)) {
    oop obj = oopDesc::decode_heap_oop_not_null(o);

    if (CHECKCAST) {
      assert(bound != NULL, "need element klass for checkcast");
      if (!bound->is_instanceof_or_null(obj)) {
        return false;
      }
    }

    switch (STOREVAL_MODE) {
    case NONE:
      break;
    case READ_BARRIER:
      obj = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      break;
    case WRITE_BARRIER_MAYBE_ENQUEUE:
      if (_heap->in_collection_set(obj)) {
        oop forw = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
        if (oopDesc::unsafe_equals(forw, obj)) {
          bool evac;
          forw = _heap->evacuate_object(forw, thread, evac);
          if (evac) {
            enqueue(forw);
          }
        }
        obj = forw;
      }
      break;
    case WRITE_BARRIER_ALWAYS_ENQUEUE:
      if (_heap->in_collection_set(obj)) {
        oop forw = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
        if (oopDesc::unsafe_equals(forw, obj)) {
          bool evac;
          forw = _heap->evacuate_object(forw, thread, evac);
        }
        obj = forw;
      }
      enqueue(obj);
      break;
    default:
      ShouldNotReachHere();
    }

    if (MATRIX) {
      _heap->connection_matrix()->set_connected(cur_dst, obj);
    }

    oopDesc::encode_store_heap_oop_not_null(cur_dst, obj);
  } else {
    // Store null.
    oopDesc::store_heap_oop(cur_dst, o);
  }
  return true;
}

template <DecoratorSet decorators, typename BarrierSetT>
template <typename T>
bool ShenandoahBarrierSet::AccessBarrier<decorators, BarrierSetT>::oop_arraycopy_in_heap(arrayOop src_obj, arrayOop dst_obj, T* src, T* dst, size_t length) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  if (!oopDesc::is_null(src_obj)) {
    size_t src_offset = pointer_delta((void*) src, (void*) src_obj, sizeof(T));
    src_obj = arrayOop(((ShenandoahBarrierSet*) BarrierSet::barrier_set())->read_barrier(src_obj));
    src =  ((T*)(void*) src_obj) + src_offset;
  }
  if (!oopDesc::is_null(dst_obj)) {
    size_t dst_offset = pointer_delta((void*) dst, (void*) dst_obj, sizeof(T));
    dst_obj = arrayOop(((ShenandoahBarrierSet*) BarrierSet::barrier_set())->write_barrier(dst_obj));
    dst = ((T*)(void*) dst_obj) + dst_offset;
  }

  bool satb = (ShenandoahSATBBarrier || ShenandoahConditionalSATBBarrier) && heap->is_concurrent_mark_in_progress();
  bool checkcast = HasDecorator<decorators, ARRAYCOPY_CHECKCAST>::value;
  ArrayCopyStoreValMode storeval_mode;
  if (heap->has_forwarded_objects()) {
    if (heap->is_concurrent_partial_in_progress()) {
      storeval_mode = WRITE_BARRIER_MAYBE_ENQUEUE;
    } else if (heap->is_concurrent_traversal_in_progress()) {
      storeval_mode = WRITE_BARRIER_ALWAYS_ENQUEUE;
    } else if (heap->is_concurrent_mark_in_progress() || heap->is_update_refs_in_progress()) {
      storeval_mode = READ_BARRIER;
    } else {
      assert(heap->is_idle() || heap->is_evacuation_in_progress(), "must not have anything in progress");
      storeval_mode = NONE; // E.g. during evac or outside cycle
    }
  } else {
    assert(heap->is_stable() || heap->is_concurrent_mark_in_progress(), "must not have anything in progress");
    storeval_mode = NONE;
  }

  if (!satb && !checkcast && !UseShenandoahMatrix && storeval_mode == NONE) {
    // Short-circuit to bulk copy.
    return Raw::oop_arraycopy(src_obj, dst_obj, src, dst, length);
  }

  Klass* bound = objArrayOop(dst_obj)->element_klass();
  ShenandoahBarrierSet* bs = (ShenandoahBarrierSet*) BarrierSet::barrier_set();
  return bs->arraycopy_loop_1(src, dst, length, bound, checkcast, satb, UseShenandoahMatrix, storeval_mode);
}

#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHBARRIERSET_INLINE_HPP
