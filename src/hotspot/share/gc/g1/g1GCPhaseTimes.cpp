/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1GCParPhaseTimesTracker.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HotCardCache.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/workerDataArray.inline.hpp"
#include "memory/resourceArea.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/timer.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"

static const char* indent(uint level) {
  static const char* Indents[] = {"", "  ", "    ", "      ", "        ", "          "};
  assert(level < ARRAY_SIZE(Indents), "Too high indent level %u", level);
  return Indents[level];
}

G1GCPhaseTimes::G1GCPhaseTimes(STWGCTimer* gc_timer, uint max_gc_threads) :
  _max_gc_threads(max_gc_threads),
  _gc_start_counter(0),
  _gc_pause_time_ms(0.0),
  _ref_phase_times(gc_timer, max_gc_threads),
  _weak_phase_times(max_gc_threads)
{
  assert(max_gc_threads > 0, "Must have some GC threads");

  _gc_par_phases[GCWorkerStart] = new WorkerDataArray<double>("GCWorkerStart", "GC Worker Start (ms):", max_gc_threads);
  _gc_par_phases[ExtRootScan] = new WorkerDataArray<double>("ExtRootScan", "Ext Root Scanning (ms):", max_gc_threads);

  // Root scanning phases
  _gc_par_phases[ThreadRoots] = new WorkerDataArray<double>("ThreadRoots", "Thread Roots (ms):", max_gc_threads);
  _gc_par_phases[UniverseRoots] = new WorkerDataArray<double>("UniverseRoots", "Universe Roots (ms):", max_gc_threads);
  _gc_par_phases[ObjectSynchronizerRoots] = new WorkerDataArray<double>("ObjectSynchronizerRoots", "ObjectSynchronizer Roots (ms):", max_gc_threads);
  _gc_par_phases[ManagementRoots] = new WorkerDataArray<double>("ManagementRoots", "Management Roots (ms):", max_gc_threads);
  _gc_par_phases[CLDGRoots] = new WorkerDataArray<double>("CLDGRoots", "CLDG Roots (ms):", max_gc_threads);
  _gc_par_phases[JVMTIRoots] = new WorkerDataArray<double>("JVMTIRoots", "JVMTI Roots (ms):", max_gc_threads);
  AOT_ONLY(_gc_par_phases[AOTCodeRoots] = new WorkerDataArray<double>("AOTCodeRoots", "AOT Root Scan (ms):", max_gc_threads);)
  _gc_par_phases[CMRefRoots] = new WorkerDataArray<double>("CMRefRoots", "CM RefProcessor Roots (ms):", max_gc_threads);

  int index = G1GCPhaseTimes::StrongOopStorageSetRoots;
  for (OopStorageSet::Iterator it = OopStorageSet::strong_iterator(); !it.is_end(); ++it, ++index) {
    const char* phase_name_postfix = " Roots (ms):";
    char* oop_storage_phase_name = NEW_C_HEAP_ARRAY(char, strlen(phase_name_postfix) + strlen(it->name()) + 1, mtGC);
    strcpy(oop_storage_phase_name, it->name());
    strcat(oop_storage_phase_name, phase_name_postfix);
    _gc_par_phases[index] = new WorkerDataArray<double>(it->name(), oop_storage_phase_name, max_gc_threads);
  }

  _gc_par_phases[MergeER] = new WorkerDataArray<double>("MergeER", "Eager Reclaim (ms):", max_gc_threads);

  _gc_par_phases[MergeRS] = new WorkerDataArray<double>("MergeRS", "Remembered Sets (ms):", max_gc_threads);
  _gc_par_phases[MergeRS]->create_thread_work_items("Merged Sparse:", MergeRSMergedSparse);
  _gc_par_phases[MergeRS]->create_thread_work_items("Merged Fine:", MergeRSMergedFine);
  _gc_par_phases[MergeRS]->create_thread_work_items("Merged Coarse:", MergeRSMergedCoarse);
  _gc_par_phases[MergeRS]->create_thread_work_items("Dirty Cards:", MergeRSDirtyCards);

  _gc_par_phases[OptMergeRS] = new WorkerDataArray<double>("OptMergeRS", "Optional Remembered Sets (ms):", max_gc_threads);
  _gc_par_phases[OptMergeRS]->create_thread_work_items("Merged Sparse:", MergeRSMergedSparse);
  _gc_par_phases[OptMergeRS]->create_thread_work_items("Merged Fine:", MergeRSMergedFine);
  _gc_par_phases[OptMergeRS]->create_thread_work_items("Merged Coarse:", MergeRSMergedCoarse);
  _gc_par_phases[OptMergeRS]->create_thread_work_items("Dirty Cards:", MergeRSDirtyCards);

  _gc_par_phases[MergeLB] = new WorkerDataArray<double>("MergeLB", "Log Buffers (ms):", max_gc_threads);
  if (G1HotCardCache::default_use_cache()) {
    _gc_par_phases[MergeHCC] = new WorkerDataArray<double>("MergeHCC", "Hot Card Cache (ms):", max_gc_threads);
    _gc_par_phases[MergeHCC]->create_thread_work_items("Dirty Cards:", MergeHCCDirtyCards);
    _gc_par_phases[MergeHCC]->create_thread_work_items("Skipped Cards:", MergeHCCSkippedCards);
  } else {
    _gc_par_phases[MergeHCC] = NULL;
  }
  _gc_par_phases[ScanHR] = new WorkerDataArray<double>("ScanHR", "Scan Heap Roots (ms):", max_gc_threads);
  _gc_par_phases[OptScanHR] = new WorkerDataArray<double>("OptScanHR", "Optional Scan Heap Roots (ms):", max_gc_threads);
  _gc_par_phases[CodeRoots] = new WorkerDataArray<double>("CodeRoots", "Code Root Scan (ms):", max_gc_threads);
  _gc_par_phases[OptCodeRoots] = new WorkerDataArray<double>("OptCodeRoots", "Optional Code Root Scan (ms):", max_gc_threads);
  _gc_par_phases[ObjCopy] = new WorkerDataArray<double>("ObjCopy", "Object Copy (ms):", max_gc_threads);
  _gc_par_phases[OptObjCopy] = new WorkerDataArray<double>("OptObjCopy", "Optional Object Copy (ms):", max_gc_threads);
  _gc_par_phases[Termination] = new WorkerDataArray<double>("Termination", "Termination (ms):", max_gc_threads);
  _gc_par_phases[OptTermination] = new WorkerDataArray<double>("OptTermination", "Optional Termination (ms):", max_gc_threads);
  _gc_par_phases[GCWorkerTotal] = new WorkerDataArray<double>("GCWorkerTotal", "GC Worker Total (ms):", max_gc_threads);
  _gc_par_phases[GCWorkerEnd] = new WorkerDataArray<double>("GCWorkerEnd", "GC Worker End (ms):", max_gc_threads);
  _gc_par_phases[Other] = new WorkerDataArray<double>("Other", "GC Worker Other (ms):", max_gc_threads);

  _gc_par_phases[ScanHR]->create_thread_work_items("Scanned Cards:", ScanHRScannedCards);
  _gc_par_phases[ScanHR]->create_thread_work_items("Scanned Blocks:", ScanHRScannedBlocks);
  _gc_par_phases[ScanHR]->create_thread_work_items("Claimed Chunks:", ScanHRClaimedChunks);

  _gc_par_phases[OptScanHR]->create_thread_work_items("Scanned Cards:", ScanHRScannedCards);
  _gc_par_phases[OptScanHR]->create_thread_work_items("Scanned Blocks:", ScanHRScannedBlocks);
  _gc_par_phases[OptScanHR]->create_thread_work_items("Claimed Chunks:", ScanHRClaimedChunks);
  _gc_par_phases[OptScanHR]->create_thread_work_items("Scanned Refs:", ScanHRScannedOptRefs);
  _gc_par_phases[OptScanHR]->create_thread_work_items("Used Memory:", ScanHRUsedMemory);

  _gc_par_phases[MergeLB]->create_thread_work_items("Dirty Cards:", MergeLBDirtyCards);
  _gc_par_phases[MergeLB]->create_thread_work_items("Skipped Cards:", MergeLBSkippedCards);

  _gc_par_phases[MergePSS] = new WorkerDataArray<double>("MergePSS", "Merge Per-Thread State", 1 /* length */, true /* is_serial */);

  _gc_par_phases[MergePSS]->create_thread_work_items("Copied Bytes", MergePSSCopiedBytes, max_gc_threads);
  _gc_par_phases[MergePSS]->create_thread_work_items("LAB Waste", MergePSSLABWasteBytes, max_gc_threads);
  _gc_par_phases[MergePSS]->create_thread_work_items("LAB Undo Waste", MergePSSLABUndoWasteBytes, max_gc_threads);

  _gc_par_phases[Termination]->create_thread_work_items("Termination Attempts:");

  _gc_par_phases[OptTermination]->create_thread_work_items("Optional Termination Attempts:");

  if (UseStringDeduplication) {
    _gc_par_phases[StringDedupQueueFixup] = new WorkerDataArray<double>("StringDedupQueueFixup", "Queue Fixup (ms):", max_gc_threads);
    _gc_par_phases[StringDedupTableFixup] = new WorkerDataArray<double>("StringDedupTableFixup", "Table Fixup (ms):", max_gc_threads);
  } else {
    _gc_par_phases[StringDedupQueueFixup] = NULL;
    _gc_par_phases[StringDedupTableFixup] = NULL;
  }

  _gc_par_phases[RedirtyCards] = new WorkerDataArray<double>("RedirtyCards", "Parallel Redirty (ms):", max_gc_threads);
  _gc_par_phases[RedirtyCards]->create_thread_work_items("Redirtied Cards:");

  _gc_par_phases[ParFreeCSet] = new WorkerDataArray<double>("ParFreeCSet", "Parallel Free Collection Set (ms):", max_gc_threads);
  _gc_par_phases[YoungFreeCSet] = new WorkerDataArray<double>("YoungFreeCSet", "Young Free Collection Set (ms):", max_gc_threads);
  _gc_par_phases[NonYoungFreeCSet] = new WorkerDataArray<double>("NonYoungFreeCSet", "Non-Young Free Collection Set (ms):", max_gc_threads);
  _gc_par_phases[RebuildFreeList] = new WorkerDataArray<double>("RebuildFreeList", "Parallel Rebuild Free List (ms):", max_gc_threads);

  reset();
}

void G1GCPhaseTimes::reset() {
  _cur_collection_initial_evac_time_ms = 0.0;
  _cur_optional_evac_time_ms = 0.0;
  _cur_collection_code_root_fixup_time_ms = 0.0;
  _cur_strong_code_root_purge_time_ms = 0.0;
  _cur_merge_heap_roots_time_ms = 0.0;
  _cur_optional_merge_heap_roots_time_ms = 0.0;
  _cur_prepare_merge_heap_roots_time_ms = 0.0;
  _cur_optional_prepare_merge_heap_roots_time_ms = 0.0;
  _cur_evac_fail_recalc_used = 0.0;
  _cur_evac_fail_remove_self_forwards = 0.0;
  _cur_string_deduplication_time_ms = 0.0;
  _cur_prepare_tlab_time_ms = 0.0;
  _cur_resize_tlab_time_ms = 0.0;
  _cur_concatenate_dirty_card_logs_time_ms = 0.0;
  _cur_derived_pointer_table_update_time_ms = 0.0;
  _cur_clear_ct_time_ms = 0.0;
  _cur_expand_heap_time_ms = 0.0;
  _cur_ref_proc_time_ms = 0.0;
  _cur_collection_start_sec = 0.0;
  _root_region_scan_wait_time_ms = 0.0;
  _external_accounted_time_ms = 0.0;
  _recorded_prepare_heap_roots_time_ms = 0.0;
  _recorded_clear_claimed_marks_time_ms = 0.0;
  _recorded_young_cset_choice_time_ms = 0.0;
  _recorded_non_young_cset_choice_time_ms = 0.0;
  _recorded_redirty_logged_cards_time_ms = 0.0;
  _recorded_preserve_cm_referents_time_ms = 0.0;
  _recorded_start_new_cset_time_ms = 0.0;
  _recorded_total_free_cset_time_ms = 0.0;
  _recorded_serial_free_cset_time_ms = 0.0;
  _recorded_total_rebuild_freelist_time_ms = 0.0;
  _recorded_serial_rebuild_freelist_time_ms = 0.0;
  _cur_fast_reclaim_humongous_time_ms = 0.0;
  _cur_region_register_time = 0.0;
  _cur_fast_reclaim_humongous_total = 0;
  _cur_fast_reclaim_humongous_candidates = 0;
  _cur_fast_reclaim_humongous_reclaimed = 0;
  _cur_verify_before_time_ms = 0.0;
  _cur_verify_after_time_ms = 0.0;

  for (int i = 0; i < GCParPhasesSentinel; i++) {
    if (_gc_par_phases[i] != NULL) {
      _gc_par_phases[i]->reset();
    }
  }

  _ref_phase_times.reset();
  _weak_phase_times.reset();
}

void G1GCPhaseTimes::note_gc_start() {
  _gc_start_counter = os::elapsed_counter();
  reset();
}

#define ASSERT_PHASE_UNINITIALIZED(phase) \
    assert(_gc_par_phases[phase] == NULL || _gc_par_phases[phase]->get(i) == uninitialized, "Phase " #phase " reported for thread that was not started");

double G1GCPhaseTimes::worker_time(GCParPhases phase, uint worker) {
  if (_gc_par_phases[phase] == NULL) {
    return 0.0;
  }
  double value = _gc_par_phases[phase]->get(worker);
  if (value != WorkerDataArray<double>::uninitialized()) {
    return value;
  }
  return 0.0;
}

void G1GCPhaseTimes::note_gc_end() {
  _gc_pause_time_ms = TimeHelper::counter_to_millis(os::elapsed_counter() - _gc_start_counter);

  double uninitialized = WorkerDataArray<double>::uninitialized();

  for (uint i = 0; i < _max_gc_threads; i++) {
    double worker_start = _gc_par_phases[GCWorkerStart]->get(i);
    if (worker_start != uninitialized) {
      assert(_gc_par_phases[GCWorkerEnd]->get(i) != uninitialized, "Worker started but not ended.");
      double total_worker_time = _gc_par_phases[GCWorkerEnd]->get(i) - _gc_par_phases[GCWorkerStart]->get(i);
      record_time_secs(GCWorkerTotal, i , total_worker_time);

      double worker_known_time = worker_time(ExtRootScan, i) +
                                 worker_time(ScanHR, i) +
                                 worker_time(CodeRoots, i) +
                                 worker_time(ObjCopy, i) +
                                 worker_time(Termination, i);

      record_time_secs(Other, i, total_worker_time - worker_known_time);
    } else {
      // Make sure all slots are uninitialized since this thread did not seem to have been started
      ASSERT_PHASE_UNINITIALIZED(GCWorkerEnd);
      ASSERT_PHASE_UNINITIALIZED(ExtRootScan);
      ASSERT_PHASE_UNINITIALIZED(MergeER);
      ASSERT_PHASE_UNINITIALIZED(MergeRS);
      ASSERT_PHASE_UNINITIALIZED(OptMergeRS);
      ASSERT_PHASE_UNINITIALIZED(MergeHCC);
      ASSERT_PHASE_UNINITIALIZED(MergeLB);
      ASSERT_PHASE_UNINITIALIZED(ScanHR);
      ASSERT_PHASE_UNINITIALIZED(CodeRoots);
      ASSERT_PHASE_UNINITIALIZED(OptCodeRoots);
      ASSERT_PHASE_UNINITIALIZED(ObjCopy);
      ASSERT_PHASE_UNINITIALIZED(OptObjCopy);
      ASSERT_PHASE_UNINITIALIZED(Termination);
    }
  }
}

#undef ASSERT_PHASE_UNINITIALIZED

// record the time a phase took in seconds
void G1GCPhaseTimes::record_time_secs(GCParPhases phase, uint worker_id, double secs) {
  _gc_par_phases[phase]->set(worker_id, secs);
}

// add a number of seconds to a phase
void G1GCPhaseTimes::add_time_secs(GCParPhases phase, uint worker_id, double secs) {
  _gc_par_phases[phase]->add(worker_id, secs);
}

void G1GCPhaseTimes::record_or_add_time_secs(GCParPhases phase, uint worker_id, double secs) {
  if (_gc_par_phases[phase]->get(worker_id) == _gc_par_phases[phase]->uninitialized()) {
    record_time_secs(phase, worker_id, secs);
  } else {
    add_time_secs(phase, worker_id, secs);
  }
}

double G1GCPhaseTimes::get_time_secs(GCParPhases phase, uint worker_id) {
  return _gc_par_phases[phase]->get(worker_id);
}

void G1GCPhaseTimes::record_thread_work_item(GCParPhases phase, uint worker_id, size_t count, uint index) {
  _gc_par_phases[phase]->set_thread_work_item(worker_id, count, index);
}

void G1GCPhaseTimes::record_or_add_thread_work_item(GCParPhases phase, uint worker_id, size_t count, uint index) {
  _gc_par_phases[phase]->set_or_add_thread_work_item(worker_id, count, index);
}

size_t G1GCPhaseTimes::get_thread_work_item(GCParPhases phase, uint worker_id, uint index) {
  return _gc_par_phases[phase]->get_thread_work_item(worker_id, index);
}

// return the average time for a phase in milliseconds
double G1GCPhaseTimes::average_time_ms(GCParPhases phase) {
  if (_gc_par_phases[phase] == NULL) {
    return 0.0;
  }
  return _gc_par_phases[phase]->average() * 1000.0;
}

size_t G1GCPhaseTimes::sum_thread_work_items(GCParPhases phase, uint index) {
  if (_gc_par_phases[phase] == NULL) {
    return 0;
  }
  assert(_gc_par_phases[phase]->thread_work_items(index) != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items(index)->sum();
}

template <class T>
void G1GCPhaseTimes::details(T* phase, const char* indent_str) const {
  LogTarget(Trace, gc, phases, task) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("%s", indent_str);
    phase->print_details_on(&ls);
  }
}

void G1GCPhaseTimes::log_phase(WorkerDataArray<double>* phase, uint indent_level, outputStream* out, bool print_sum) const {
  out->print("%s", indent(indent_level));
  phase->print_summary_on(out, print_sum);
  details(phase, indent(indent_level));

  for (uint i = 0; i < phase->MaxThreadWorkItems; i++) {
    WorkerDataArray<size_t>* work_items = phase->thread_work_items(i);
    if (work_items != NULL) {
      out->print("%s", indent(indent_level + 1));
      work_items->print_summary_on(out, true);
      details(work_items, indent(indent_level + 1));
    }
  }
}

void G1GCPhaseTimes::debug_phase(WorkerDataArray<double>* phase, uint extra_indent) const {
  LogTarget(Debug, gc, phases) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    log_phase(phase, 2 + extra_indent, &ls, true);
  }
}

void G1GCPhaseTimes::trace_phase(WorkerDataArray<double>* phase, bool print_sum, uint extra_indent) const {
  LogTarget(Trace, gc, phases) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    log_phase(phase, 3 + extra_indent, &ls, print_sum);
  }
}

#define TIME_FORMAT "%.1lfms"

void G1GCPhaseTimes::info_time(const char* name, double value) const {
  log_info(gc, phases)("%s%s: " TIME_FORMAT, indent(1), name, value);
}

void G1GCPhaseTimes::debug_time(const char* name, double value) const {
  log_debug(gc, phases)("%s%s: " TIME_FORMAT, indent(2), name, value);
}

void G1GCPhaseTimes::debug_time_for_reference(const char* name, double value) const {
  LogTarget(Debug, gc, phases) lt;
  LogTarget(Debug, gc, phases, ref) lt2;

  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("%s%s: " TIME_FORMAT, indent(2), name, value);
  } else if (lt2.is_enabled()) {
    LogStream ls(lt2);
    ls.print_cr("%s%s: " TIME_FORMAT, indent(2), name, value);
  }
}

void G1GCPhaseTimes::trace_time(const char* name, double value) const {
  log_trace(gc, phases)("%s%s: " TIME_FORMAT, indent(3), name, value);
}

void G1GCPhaseTimes::trace_count(const char* name, size_t value) const {
  log_trace(gc, phases)("%s%s: " SIZE_FORMAT, indent(3), name, value);
}

double G1GCPhaseTimes::print_pre_evacuate_collection_set() const {
  const double sum_ms = _root_region_scan_wait_time_ms +
                        _cur_prepare_tlab_time_ms +
                        _cur_concatenate_dirty_card_logs_time_ms +
                        _recorded_young_cset_choice_time_ms +
                        _recorded_non_young_cset_choice_time_ms +
                        _cur_region_register_time +
                        _recorded_prepare_heap_roots_time_ms +
                        _recorded_clear_claimed_marks_time_ms;

  info_time("Pre Evacuate Collection Set", sum_ms);

  if (_root_region_scan_wait_time_ms > 0.0) {
    debug_time("Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }
  debug_time("Prepare TLABs", _cur_prepare_tlab_time_ms);
  debug_time("Concatenate Dirty Card Logs", _cur_concatenate_dirty_card_logs_time_ms);
  debug_time("Choose Collection Set", (_recorded_young_cset_choice_time_ms + _recorded_non_young_cset_choice_time_ms));
  debug_time("Region Register", _cur_region_register_time);
  if (G1EagerReclaimHumongousObjects) {
    trace_count("Humongous Total", _cur_fast_reclaim_humongous_total);
    trace_count("Humongous Candidate", _cur_fast_reclaim_humongous_candidates);
  }

  debug_time("Prepare Heap Roots", _recorded_prepare_heap_roots_time_ms);
  if (_recorded_clear_claimed_marks_time_ms > 0.0) {
    debug_time("Clear Claimed Marks", _recorded_clear_claimed_marks_time_ms);
  }
  return sum_ms;
}

double G1GCPhaseTimes::print_evacuate_optional_collection_set() const {
  const double sum_ms = _cur_optional_evac_time_ms + _cur_optional_merge_heap_roots_time_ms;
  if (sum_ms > 0) {
    info_time("Merge Optional Heap Roots", _cur_optional_merge_heap_roots_time_ms);

    debug_time("Prepare Optional Merge Heap Roots", _cur_optional_prepare_merge_heap_roots_time_ms);
    debug_phase(_gc_par_phases[OptMergeRS]);

    info_time("Evacuate Optional Collection Set", _cur_optional_evac_time_ms);
    debug_phase(_gc_par_phases[OptScanHR]);
    debug_phase(_gc_par_phases[OptObjCopy]);
    debug_phase(_gc_par_phases[OptCodeRoots]);
    debug_phase(_gc_par_phases[OptTermination]);
  }
  return sum_ms;
}

double G1GCPhaseTimes::print_evacuate_initial_collection_set() const {
  info_time("Merge Heap Roots", _cur_merge_heap_roots_time_ms);

  debug_time("Prepare Merge Heap Roots", _cur_prepare_merge_heap_roots_time_ms);
  debug_phase(_gc_par_phases[MergeER]);
  debug_phase(_gc_par_phases[MergeRS]);
  if (G1HotCardCache::default_use_cache()) {
    debug_phase(_gc_par_phases[MergeHCC]);
  }
  debug_phase(_gc_par_phases[MergeLB]);

  info_time("Evacuate Collection Set", _cur_collection_initial_evac_time_ms);

  trace_phase(_gc_par_phases[GCWorkerStart], false);
  debug_phase(_gc_par_phases[ExtRootScan]);
  for (int i = ExtRootScanSubPhasesFirst; i <= ExtRootScanSubPhasesLast; i++) {
    trace_phase(_gc_par_phases[i]);
  }
  debug_phase(_gc_par_phases[ScanHR]);
  debug_phase(_gc_par_phases[CodeRoots]);
  debug_phase(_gc_par_phases[ObjCopy]);
  debug_phase(_gc_par_phases[Termination]);
  debug_phase(_gc_par_phases[Other]);
  debug_phase(_gc_par_phases[GCWorkerTotal]);
  trace_phase(_gc_par_phases[GCWorkerEnd], false);

  return _cur_collection_initial_evac_time_ms + _cur_merge_heap_roots_time_ms;
}

double G1GCPhaseTimes::print_post_evacuate_collection_set() const {
  const double evac_fail_handling = _cur_evac_fail_recalc_used +
                                    _cur_evac_fail_remove_self_forwards;
  assert(_gc_par_phases[MergePSS]->get(0) != WorkerDataArray<double>::uninitialized(), "must be set");
  const double merge_pss = _gc_par_phases[MergePSS]->get(0) * MILLIUNITS;
  const double sum_ms = evac_fail_handling +
                        _cur_collection_code_root_fixup_time_ms +
                        _recorded_preserve_cm_referents_time_ms +
                        _cur_ref_proc_time_ms +
                        (_weak_phase_times.total_time_sec() * MILLIUNITS) +
                        _cur_clear_ct_time_ms +
                        merge_pss +
                        _cur_strong_code_root_purge_time_ms +
                        _recorded_redirty_logged_cards_time_ms +
                        _recorded_total_free_cset_time_ms +
                        _recorded_total_rebuild_freelist_time_ms +
                        _cur_fast_reclaim_humongous_time_ms +
                        _cur_expand_heap_time_ms +
                        _cur_string_deduplication_time_ms;

  info_time("Post Evacuate Collection Set", sum_ms);

  debug_time("Code Roots Fixup", _cur_collection_code_root_fixup_time_ms);

  debug_time("Clear Card Table", _cur_clear_ct_time_ms);

  debug_time_for_reference("Reference Processing", _cur_ref_proc_time_ms);
  _ref_phase_times.print_all_references(2, false);
  _weak_phase_times.log_print(2);

  if (G1StringDedup::is_enabled()) {
    debug_time("String Deduplication", _cur_string_deduplication_time_ms);
    debug_phase(_gc_par_phases[StringDedupQueueFixup], 1);
    debug_phase(_gc_par_phases[StringDedupTableFixup], 1);
  }

  if (G1CollectedHeap::heap()->evacuation_failed()) {
    debug_time("Evacuation Failure", evac_fail_handling);
    trace_time("Recalculate Used", _cur_evac_fail_recalc_used);
    trace_time("Remove Self Forwards",_cur_evac_fail_remove_self_forwards);
  }

  debug_phase(_gc_par_phases[MergePSS], 0);
  debug_time("Code Roots Purge", _cur_strong_code_root_purge_time_ms);

  debug_time("Redirty Cards", _recorded_redirty_logged_cards_time_ms);
  trace_phase(_gc_par_phases[RedirtyCards]);
#if COMPILER2_OR_JVMCI
  debug_time("DerivedPointerTable Update", _cur_derived_pointer_table_update_time_ms);
#endif

  debug_time("Free Collection Set", _recorded_total_free_cset_time_ms);
  trace_time("Serial Free Collection Set", _recorded_serial_free_cset_time_ms);
  trace_phase(_gc_par_phases[ParFreeCSet]);
  trace_phase(_gc_par_phases[YoungFreeCSet], true, 1);
  trace_phase(_gc_par_phases[NonYoungFreeCSet], true, 1);

  debug_time("Rebuild Free List", _recorded_total_rebuild_freelist_time_ms);
  trace_time("Serial Rebuild Free List ", _recorded_serial_rebuild_freelist_time_ms);
  trace_phase(_gc_par_phases[RebuildFreeList]);

  if (G1EagerReclaimHumongousObjects) {
    debug_time("Humongous Reclaim", _cur_fast_reclaim_humongous_time_ms);
    trace_count("Humongous Reclaimed", _cur_fast_reclaim_humongous_reclaimed);
  }
  debug_time("Start New Collection Set", _recorded_start_new_cset_time_ms);
  if (UseTLAB && ResizeTLAB) {
    debug_time("Resize TLABs", _cur_resize_tlab_time_ms);
  }
  debug_time("Expand Heap After Collection", _cur_expand_heap_time_ms);


  return sum_ms;
}

void G1GCPhaseTimes::print_other(double accounted_ms) const {
  info_time("Other", _gc_pause_time_ms - accounted_ms);
}

void G1GCPhaseTimes::print() {
  note_gc_end();

  if (_cur_verify_before_time_ms > 0.0) {
    debug_time("Verify Before", _cur_verify_before_time_ms);
  }

  double accounted_ms = 0.0;
  accounted_ms += print_pre_evacuate_collection_set();
  accounted_ms += print_evacuate_initial_collection_set();
  accounted_ms += print_evacuate_optional_collection_set();
  accounted_ms += print_post_evacuate_collection_set();
  print_other(accounted_ms);

  if (_cur_verify_after_time_ms > 0.0) {
    debug_time("Verify After", _cur_verify_after_time_ms);
  }
}

const char* G1GCPhaseTimes::phase_name(GCParPhases phase) {
  G1GCPhaseTimes* phase_times = G1CollectedHeap::heap()->phase_times();
  return phase_times->_gc_par_phases[phase]->short_name();
}

G1EvacPhaseWithTrimTimeTracker::G1EvacPhaseWithTrimTimeTracker(G1ParScanThreadState* pss, Tickspan& total_time, Tickspan& trim_time) :
  _pss(pss),
  _start(Ticks::now()),
  _total_time(total_time),
  _trim_time(trim_time),
  _stopped(false) {

  assert(_pss->trim_ticks().value() == 0, "Possibly remaining trim ticks left over from previous use");
}

G1EvacPhaseWithTrimTimeTracker::~G1EvacPhaseWithTrimTimeTracker() {
  if (!_stopped) {
    stop();
  }
}

void G1EvacPhaseWithTrimTimeTracker::stop() {
  assert(!_stopped, "Should only be called once");
  _total_time += (Ticks::now() - _start) - _pss->trim_ticks();
  _trim_time += _pss->trim_ticks();
  _pss->reset_trim_ticks();
  _stopped = true;
}

G1GCParPhaseTimesTracker::G1GCParPhaseTimesTracker(G1GCPhaseTimes* phase_times, G1GCPhaseTimes::GCParPhases phase, uint worker_id, bool must_record) :
  _start_time(), _phase(phase), _phase_times(phase_times), _worker_id(worker_id), _event(), _must_record(must_record) {
  if (_phase_times != NULL) {
    _start_time = Ticks::now();
  }
}

G1GCParPhaseTimesTracker::~G1GCParPhaseTimesTracker() {
  if (_phase_times != NULL) {
    if (_must_record) {
      _phase_times->record_time_secs(_phase, _worker_id, (Ticks::now() - _start_time).seconds());
    } else {
      _phase_times->record_or_add_time_secs(_phase, _worker_id, (Ticks::now() - _start_time).seconds());
    }
    _event.commit(GCId::current(), _worker_id, G1GCPhaseTimes::phase_name(_phase));
  }
}

G1EvacPhaseTimesTracker::G1EvacPhaseTimesTracker(G1GCPhaseTimes* phase_times,
                                                 G1ParScanThreadState* pss,
                                                 G1GCPhaseTimes::GCParPhases phase,
                                                 uint worker_id) :
  G1GCParPhaseTimesTracker(phase_times, phase, worker_id),
  _total_time(),
  _trim_time(),
  _trim_tracker(pss, _total_time, _trim_time) {
}

G1EvacPhaseTimesTracker::~G1EvacPhaseTimesTracker() {
  if (_phase_times != NULL) {
    // Explicitly stop the trim tracker since it's not yet destructed.
    _trim_tracker.stop();
    // Exclude trim time by increasing the start time.
    _start_time += _trim_time;
    _phase_times->record_or_add_time_secs(G1GCPhaseTimes::ObjCopy, _worker_id, _trim_time.seconds());
  }
}

