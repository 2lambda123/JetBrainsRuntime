/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "c1/c1_Defs.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "ci/ciUtilities.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/universe.hpp"
#include "nativeInst_s390.hpp"
#include "oops/compiledICHolder.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "register_s390.hpp"
#include "registerSaver_s390.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"
#include "vmreg_s390.inline.hpp"

// Implementation of StubAssembler

int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry_point, int number_of_arguments) {
  set_num_rt_args(0); // Nothing on stack.
  assert(!(oop_result1->is_valid() || metadata_result->is_valid()) || oop_result1 != metadata_result, "registers must be different");

  // We cannot trust that code generated by the C++ compiler saves R14
  // to z_abi_160.return_pc, because sometimes it spills R14 using stmg at
  // z_abi_160.gpr14 (e.g. InterpreterRuntime::_new()).
  // Therefore we load the PC into Z_R1_scratch and let set_last_Java_frame() save
  // it into the frame anchor.
  address pc = get_PC(Z_R1_scratch);
  int call_offset = (int)(pc - addr_at(0));
  set_last_Java_frame(Z_SP, Z_R1_scratch);

  // ARG1 must hold thread address.
  z_lgr(Z_ARG1, Z_thread);

  address return_pc = nullptr;
  align_call_far_patchable(this->pc());
  return_pc = call_c_opt(entry_point);
  assert(return_pc != nullptr, "const section overflow");

  reset_last_Java_frame();

  // Check for pending exceptions.
  {
    load_and_test_long(Z_R0_scratch, Address(Z_thread, Thread::pending_exception_offset()));

    // This used to conditionally jump to forward_exception however it is
    // possible if we relocate that the branch will not reach. So we must jump
    // around so we can always reach.

    Label ok;
    z_bre(ok); // Bcondequal is the same as bcondZero.

    // exception pending => forward to exception handler

    // Make sure that the vm_results are cleared.
    if (oop_result1->is_valid()) {
      clear_mem(Address(Z_thread, JavaThread::vm_result_offset()), sizeof(jlong));
    }
    if (metadata_result->is_valid()) {
      clear_mem(Address(Z_thread, JavaThread::vm_result_2_offset()), sizeof(jlong));
    }
    if (frame_size() == no_frame_size) {
      // Pop the stub frame.
      pop_frame();
      restore_return_pc();
      load_const_optimized(Z_R1, StubRoutines::forward_exception_entry());
      z_br(Z_R1);
    } else if (_stub_id == Runtime1::forward_exception_id) {
      should_not_reach_here();
    } else {
      load_const_optimized(Z_R1, Runtime1::entry_for (Runtime1::forward_exception_id));
      z_br(Z_R1);
    }

    bind(ok);
  }

  // Get oop results if there are any and reset the values in the thread.
  if (oop_result1->is_valid()) {
    get_vm_result(oop_result1);
  }
  if (metadata_result->is_valid()) {
    get_vm_result_2(metadata_result);
  }

  return call_offset;
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg1);
  return call_RT(oop_result1, metadata_result, entry, 1);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg1);
  assert(arg2 != Z_ARG2, "smashed argument");
  lgr_if_needed(Z_ARG3, arg2);
  return call_RT(oop_result1, metadata_result, entry, 2);
}


int StubAssembler::call_RT(Register oop_result1, Register metadata_result, address entry, Register arg1, Register arg2, Register arg3) {
  // Z_ARG1 is reserved for the thread.
  lgr_if_needed(Z_ARG2, arg1);
  assert(arg2 != Z_ARG2, "smashed argument");
  lgr_if_needed(Z_ARG3, arg2);
  assert(arg3 != Z_ARG3, "smashed argument");
  lgr_if_needed(Z_ARG4, arg3);
  return call_RT(oop_result1, metadata_result, entry, 3);
}


// Implementation of Runtime1

#define __ sasm->

#ifndef PRODUCT
#undef  __
#define __ (Verbose ? (sasm->block_comment(FILE_AND_LINE),sasm):sasm)->
#endif // !PRODUCT

#define BLOCK_COMMENT(str) if (PrintAssembly) __ block_comment(str)
#define BIND(label)        bind(label); BLOCK_COMMENT(#label ":")

static OopMap* generate_oop_map(StubAssembler* sasm) {
  RegisterSaver::RegisterSet reg_set = RegisterSaver::all_registers;
  int frame_size_in_slots =
    RegisterSaver::live_reg_frame_size(reg_set) / VMRegImpl::stack_slot_size;
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word);
  return RegisterSaver::generate_oop_map(sasm, reg_set);
}

static OopMap* save_live_registers(StubAssembler* sasm, bool save_fpu_registers = true, Register return_pc = Z_R14) {
  __ block_comment("save_live_registers");
  RegisterSaver::RegisterSet reg_set =
    save_fpu_registers ? RegisterSaver::all_registers : RegisterSaver::all_integer_registers;
  int frame_size_in_slots =
    RegisterSaver::live_reg_frame_size(reg_set) / VMRegImpl::stack_slot_size;
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word);
  return RegisterSaver::save_live_registers(sasm, reg_set, return_pc);
}

static OopMap* save_live_registers_except_r2(StubAssembler* sasm, bool save_fpu_registers = true) {
  if (!save_fpu_registers) {
    __ unimplemented(FILE_AND_LINE);
  }
  __ block_comment("save_live_registers");
  RegisterSaver::RegisterSet reg_set = RegisterSaver::all_registers_except_r2;
  int frame_size_in_slots =
      RegisterSaver::live_reg_frame_size(reg_set) / VMRegImpl::stack_slot_size;
  sasm->set_frame_size(frame_size_in_slots / VMRegImpl::slots_per_word);
  return RegisterSaver::save_live_registers(sasm, reg_set);
}

static void restore_live_registers(StubAssembler* sasm, bool restore_fpu_registers = true) {
  __ block_comment("restore_live_registers");
  RegisterSaver::RegisterSet reg_set =
    restore_fpu_registers ? RegisterSaver::all_registers : RegisterSaver::all_integer_registers;
  RegisterSaver::restore_live_registers(sasm, reg_set);
}

static void restore_live_registers_except_r2(StubAssembler* sasm, bool restore_fpu_registers = true) {
  if (!restore_fpu_registers) {
    __ unimplemented(FILE_AND_LINE);
  }
  __ block_comment("restore_live_registers_except_r2");
  RegisterSaver::restore_live_registers(sasm, RegisterSaver::all_registers_except_r2);
}

void Runtime1::initialize_pd() {
  // Nothing to do.
}

OopMapSet* Runtime1::generate_exception_throw(StubAssembler* sasm, address target, bool has_argument) {
  // Make a frame and preserve the caller's caller-save registers.
  OopMap* oop_map = save_live_registers(sasm);
  int call_offset;
  if (!has_argument) {
    call_offset = __ call_RT(noreg, noreg, target);
  } else {
    call_offset = __ call_RT(noreg, noreg, target, Z_R1_scratch, Z_R0_scratch);
  }
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  __ should_not_reach_here();
  return oop_maps;
}

void Runtime1::generate_unwind_exception(StubAssembler *sasm) {
  // Incoming parameters: Z_EXC_OOP and Z_EXC_PC.
  // Keep copies in callee-saved registers during runtime call.
  const Register exception_oop_callee_saved = Z_R11;
  const Register exception_pc_callee_saved = Z_R12;
  // Other registers used in this stub.
  const Register handler_addr = Z_R4;

  // Verify that only exception_oop, is valid at this time.
  __ invalidate_registers(Z_EXC_OOP, Z_EXC_PC);

  // Check that fields in JavaThread for exception oop and issuing pc are set.
  __ asm_assert_mem8_is_zero(in_bytes(JavaThread::exception_oop_offset()), Z_thread, "exception oop already set : " FILE_AND_LINE, 0);
  __ asm_assert_mem8_is_zero(in_bytes(JavaThread::exception_pc_offset()), Z_thread, "exception pc already set : " FILE_AND_LINE, 0);

  // Save exception_oop and pc in callee-saved register to preserve it
  // during runtime calls.
  __ verify_not_null_oop(Z_EXC_OOP);
  __ lgr_if_needed(exception_oop_callee_saved, Z_EXC_OOP);
  __ lgr_if_needed(exception_pc_callee_saved, Z_EXC_PC);

  __ push_frame_abi160(0); // Runtime code needs the z_abi_160.

  // Search the exception handler address of the caller (using the return address).
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address), Z_thread, Z_EXC_PC);
  // Z_RET(Z_R2): exception handler address of the caller.

  __ pop_frame();

  __ invalidate_registers(exception_oop_callee_saved, exception_pc_callee_saved, Z_RET);

  // Move result of call into correct register.
  __ lgr_if_needed(handler_addr, Z_RET);

  // Restore exception oop and pc to Z_EXC_OOP and Z_EXC_PC (required convention of exception handler).
  __ lgr_if_needed(Z_EXC_OOP, exception_oop_callee_saved);
  __ lgr_if_needed(Z_EXC_PC, exception_pc_callee_saved);

  // Verify that there is really a valid exception in Z_EXC_OOP.
  __ verify_not_null_oop(Z_EXC_OOP);

  __ z_br(handler_addr); // Jump to exception handler.
}

OopMapSet* Runtime1::generate_patching(StubAssembler* sasm, address target) {
  // Make a frame and preserve the caller's caller-save registers.
  OopMap* oop_map = save_live_registers(sasm);

  // Call the runtime patching routine, returns non-zero if nmethod got deopted.
  int call_offset = __ call_RT(noreg, noreg, target);
  OopMapSet* oop_maps = new OopMapSet();
  oop_maps->add_gc_map(call_offset, oop_map);

  // Re-execute the patched instruction or, if the nmethod was
  // deoptmized, return to the deoptimization handler entry that will
  // cause re-execution of the current bytecode.
  DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  assert(deopt_blob != nullptr, "deoptimization blob must have been created");

  __ z_ltr(Z_RET, Z_RET); // return value == 0

  restore_live_registers(sasm);

  __ z_bcr(Assembler::bcondZero, Z_R14);

  // Return to the deoptimization handler entry for unpacking and
  // rexecute if we simply returned then we'd deopt as if any call we
  // patched had just returned.
  AddressLiteral dest(deopt_blob->unpack_with_reexecution());
  __ load_const_optimized(Z_R1_scratch, dest);
  __ z_br(Z_R1_scratch);

  return oop_maps;
}

OopMapSet* Runtime1::generate_code_for(StubID id, StubAssembler* sasm) {

  // for better readability
  const bool must_gc_arguments = true;
  const bool dont_gc_arguments = false;

  // Default value; overwritten for some optimized stubs that are
  // called from methods that do not use the fpu.
  bool save_fpu_registers = true;

  // Stub code and info for the different stubs.
  OopMapSet* oop_maps = nullptr;
  switch (id) {
    case forward_exception_id:
      {
        oop_maps = generate_handle_exception(id, sasm);
        // will not return
      }
      break;

    case new_instance_id:
    case fast_new_instance_id:
    case fast_new_instance_init_check_id:
      {
        Register klass    = Z_R11; // Incoming
        Register obj      = Z_R2;  // Result

        if (id == new_instance_id) {
          __ set_info("new_instance", dont_gc_arguments);
        } else if (id == fast_new_instance_id) {
          __ set_info("fast new_instance", dont_gc_arguments);
        } else {
          assert(id == fast_new_instance_init_check_id, "bad StubID");
          __ set_info("fast new_instance init check", dont_gc_arguments);
        }

        OopMap* map = save_live_registers_except_r2(sasm);
        int call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_instance), klass);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r2(sasm);

        __ verify_oop(obj, FILE_AND_LINE);
        __ z_br(Z_R14);
      }
      break;

    case counter_overflow_id:
      {
        // Arguments :
        //   bci    : stack param 0
        //   method : stack param 1
        //
        Register bci = Z_ARG2, method = Z_ARG3;
        // frame size in bytes
        OopMap* map = save_live_registers(sasm);
        const int frame_size = sasm->frame_size() * VMRegImpl::slots_per_word * VMRegImpl::stack_slot_size;
        __ z_lg(bci,    0*BytesPerWord + FrameMap::first_available_sp_in_frame + frame_size, Z_SP);
        __ z_lg(method, 1*BytesPerWord + FrameMap::first_available_sp_in_frame + frame_size, Z_SP);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, counter_overflow), bci, method);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);
        __ z_br(Z_R14);
      }
      break;
    case new_type_array_id:
    case new_object_array_id:
      {
        Register length   = Z_R13; // Incoming
        Register klass    = Z_R11; // Incoming
        Register obj      = Z_R2;  // Result

        if (id == new_type_array_id) {
          __ set_info("new_type_array", dont_gc_arguments);
        } else {
          __ set_info("new_object_array", dont_gc_arguments);
        }

#ifdef ASSERT
        // Assert object type is really an array of the proper kind.
        {
          NearLabel ok;
          Register t0 = obj;
          __ mem2reg_opt(t0, Address(klass, Klass::layout_helper_offset()), false);
          __ z_sra(t0, Klass::_lh_array_tag_shift);
          int tag = ((id == new_type_array_id)
                     ? Klass::_lh_array_tag_type_value
                     : Klass::_lh_array_tag_obj_value);
          __ compare32_and_branch(t0, tag, Assembler::bcondEqual, ok);
          __ stop("assert(is an array klass)");
          __ should_not_reach_here();
          __ bind(ok);
        }
#endif // ASSERT

        OopMap* map = save_live_registers_except_r2(sasm);
        int call_offset;
        if (id == new_type_array_id) {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_type_array), klass, length);
        } else {
          call_offset = __ call_RT(obj, noreg, CAST_FROM_FN_PTR(address, new_object_array), klass, length);
        }

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r2(sasm);

        __ verify_oop(obj, FILE_AND_LINE);
        __ z_br(Z_R14);
      }
      break;

    case new_multi_array_id:
      { __ set_info("new_multi_array", dont_gc_arguments);
        // Z_R3,: klass
        // Z_R4,: rank
        // Z_R5: address of 1st dimension
        OopMap* map = save_live_registers(sasm);
        int call_offset = __ call_RT(Z_R2, noreg, CAST_FROM_FN_PTR(address, new_multi_array), Z_R3, Z_R4, Z_R5);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers_except_r2(sasm);

        // Z_R2,: new multi array
        __ verify_oop(Z_R2, FILE_AND_LINE);
        __ z_br(Z_R14);
      }
      break;

    case register_finalizer_id:
      {
        __ set_info("register_finalizer", dont_gc_arguments);

        // Load the klass and check the has finalizer flag.
        Register klass = Z_ARG2;
        __ load_klass(klass, Z_ARG1);
        __ testbit(Address(klass, Klass::access_flags_offset()), exact_log2(JVM_ACC_HAS_FINALIZER));
        __ z_bcr(Assembler::bcondAllZero, Z_R14); // Return if bit is not set.

        OopMap* oop_map = save_live_registers(sasm);
        int call_offset = __ call_RT(noreg, noreg,
                                     CAST_FROM_FN_PTR(address, SharedRuntime::register_finalizer), Z_ARG1);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);

        // Now restore all the live registers.
        restore_live_registers(sasm);

        __ z_br(Z_R14);
      }
      break;

    case throw_range_check_failed_id:
      { __ set_info("range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_range_check_exception), true);
      }
      break;

    case throw_index_exception_id:
      { __ set_info("index_range_check_failed", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_index_exception), true);
      }
      break;
    case throw_div0_exception_id:
      { __ set_info("throw_div0_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_div0_exception), false);
      }
      break;
    case throw_null_pointer_exception_id:
      { __ set_info("throw_null_pointer_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_null_pointer_exception), false);
      }
      break;
    case handle_exception_nofpu_id:
    case handle_exception_id:
      { __ set_info("handle_exception", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;
    case handle_exception_from_callee_id:
      { __ set_info("handle_exception_from_callee", dont_gc_arguments);
        oop_maps = generate_handle_exception(id, sasm);
      }
      break;
    case unwind_exception_id:
      { __ set_info("unwind_exception", dont_gc_arguments);
        // Note: no stubframe since we are about to leave the current
        // activation and we are calling a leaf VM function only.
        generate_unwind_exception(sasm);
      }
      break;
    case throw_array_store_exception_id:
      { __ set_info("throw_array_store_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_array_store_exception), true);
      }
      break;
    case throw_class_cast_exception_id:
    { // Z_R1_scratch: object
      __ set_info("throw_class_cast_exception", dont_gc_arguments);
      oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_class_cast_exception), true);
    }
    break;
    case throw_incompatible_class_change_error_id:
      { __ set_info("throw_incompatible_class_cast_exception", dont_gc_arguments);
        oop_maps = generate_exception_throw(sasm, CAST_FROM_FN_PTR(address, throw_incompatible_class_change_error), false);
      }
      break;
    case slow_subtype_check_id:
    {
      // Arguments :
      //   sub  : stack param 0
      //   super: stack param 1
      //   raddr: Z_R14, blown by call
      //
      // Result : condition code 0 for match (bcondEqual will be true),
      //          condition code 2 for miss  (bcondNotEqual will be true)
      NearLabel miss;
      const Register Rsubklass   = Z_ARG2; // sub
      const Register Rsuperklass = Z_ARG3; // super

      // No args, but tmp registers that are killed.
      const Register Rlength     = Z_ARG4; // cache array length
      const Register Rarray_ptr  = Z_ARG5; // Current value from cache array.

      if (UseCompressedOops) {
        assert(Universe::heap() != nullptr, "java heap must be initialized to generate partial_subtype_check stub");
      }

      const int frame_size = 4*BytesPerWord + frame::z_abi_160_size;
      // Save return pc. This is not necessary, but could be helpful
      // in the case of crashes.
      __ save_return_pc();
      __ push_frame(frame_size);
      // Save registers before changing them.
      int i = 0;
      __ z_stg(Rsubklass,   (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_stg(Rsuperklass, (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_stg(Rlength,     (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_stg(Rarray_ptr,  (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      assert(i*BytesPerWord + frame::z_abi_160_size == frame_size, "check");

      // Get sub and super from stack.
      __ z_lg(Rsubklass,   0*BytesPerWord + FrameMap::first_available_sp_in_frame + frame_size, Z_SP);
      __ z_lg(Rsuperklass, 1*BytesPerWord + FrameMap::first_available_sp_in_frame + frame_size, Z_SP);

      __ check_klass_subtype_slow_path(Rsubklass, Rsuperklass, Rarray_ptr, Rlength, nullptr, &miss);

      // Match falls through here.
      i = 0;
      __ z_lg(Rsubklass,   (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_lg(Rsuperklass, (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_lg(Rlength,     (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_lg(Rarray_ptr,  (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      assert(i*BytesPerWord + frame::z_abi_160_size == frame_size, "check");
      __ pop_frame();
      // Return pc is still in R_14.
      __ clear_reg(Z_R0_scratch);         // Zero indicates a match. Set CC 0 (bcondEqual will be true)
      __ z_br(Z_R14);

      __ BIND(miss);
      i = 0;
      __ z_lg(Rsubklass,   (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_lg(Rsuperklass, (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_lg(Rlength,     (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      __ z_lg(Rarray_ptr,  (i++)*BytesPerWord + frame::z_abi_160_size, Z_SP);
      assert(i*BytesPerWord + frame::z_abi_160_size == frame_size, "check");
      __ pop_frame();
      // return pc is still in R_14
      __ load_const_optimized(Z_R0_scratch, 1); // One indicates a miss.
      __ z_ltgr(Z_R0_scratch, Z_R0_scratch);    // Set CC 2 (bcondNotEqual will be true).
      __ z_br(Z_R14);
    }
    break;
    case monitorenter_nofpu_id:
    case monitorenter_id:
      { // Z_R1_scratch : object
        // Z_R13       : lock address (see LIRGenerator::syncTempOpr())
        __ set_info("monitorenter", dont_gc_arguments);

        int save_fpu_registers = (id == monitorenter_id);
        // Make a frame and preserve the caller's caller-save registers.
        OopMap* oop_map = save_live_registers(sasm, save_fpu_registers);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorenter), Z_R1_scratch, Z_R13);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm, save_fpu_registers);

        __ z_br(Z_R14);
      }
      break;

    case monitorexit_nofpu_id:
    case monitorexit_id:
      { // Z_R1_scratch : lock address
        // Note: really a leaf routine but must setup last java sp
        //   => Use call_RT for now (speed can be improved by
        //      doing last java sp setup manually).
        __ set_info("monitorexit", dont_gc_arguments);

        int save_fpu_registers = (id == monitorexit_id);
        // Make a frame and preserve the caller's caller-save registers.
        OopMap* oop_map = save_live_registers(sasm, save_fpu_registers);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, monitorexit), Z_R1_scratch);

        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm, save_fpu_registers);

        __ z_br(Z_R14);
      }
      break;

    case deoptimize_id:
      { // Args: Z_R1_scratch: trap request
        __ set_info("deoptimize", dont_gc_arguments);
        Register trap_request = Z_R1_scratch;
        OopMap* oop_map = save_live_registers(sasm);
        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, deoptimize), trap_request);
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, oop_map);
        restore_live_registers(sasm);
        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != nullptr, "deoptimization blob must have been created");
        AddressLiteral dest(deopt_blob->unpack_with_reexecution());
        __ load_const_optimized(Z_R1_scratch, dest);
        __ z_br(Z_R1_scratch);
      }
      break;

    case access_field_patching_id:
      { __ set_info("access_field_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, access_field_patching));
      }
      break;

    case load_klass_patching_id:
      { __ set_info("load_klass_patching", dont_gc_arguments);
        // We should set up register map.
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_klass_patching));
      }
      break;

    case load_mirror_patching_id:
      { __ set_info("load_mirror_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_mirror_patching));
      }
      break;

    case load_appendix_patching_id:
      { __ set_info("load_appendix_patching", dont_gc_arguments);
        oop_maps = generate_patching(sasm, CAST_FROM_FN_PTR(address, move_appendix_patching));
      }
      break;
#if 0
    case dtrace_object_alloc_id:
      { // rax,: object
        StubFrame f(sasm, "dtrace_object_alloc", dont_gc_arguments);
        // We can't gc here so skip the oopmap but make sure that all
        // the live registers get saved.
        save_live_registers(sasm, 1);

        __ NOT_LP64(push(rax)) LP64_ONLY(mov(c_rarg0, rax));
        __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, static_cast<int (*)(oopDesc*)>(SharedRuntime::dtrace_object_alloc))));
        NOT_LP64(__ pop(rax));

        restore_live_registers(sasm);
      }
      break;

    case fpu2long_stub_id:
      {
        // rax, and rdx are destroyed, but should be free since the result is returned there
        // preserve rsi,ecx
        __ push(rsi);
        __ push(rcx);
        LP64_ONLY(__ push(rdx);)

        // check for NaN
        Label return0, do_return, return_min_jlong, do_convert;

        Address value_high_word(rsp, wordSize + 4);
        Address value_low_word(rsp, wordSize);
        Address result_high_word(rsp, 3*wordSize + 4);
        Address result_low_word(rsp, 3*wordSize);

        __ subptr(rsp, 32);                    // more than enough on 32bit
        __ fst_d(value_low_word);
        __ movl(rax, value_high_word);
        __ andl(rax, 0x7ff00000);
        __ cmpl(rax, 0x7ff00000);
        __ jcc(Assembler::notEqual, do_convert);
        __ movl(rax, value_high_word);
        __ andl(rax, 0xfffff);
        __ orl(rax, value_low_word);
        __ jcc(Assembler::notZero, return0);

        __ bind(do_convert);
        __ fnstcw(Address(rsp, 0));
        __ movzwl(rax, Address(rsp, 0));
        __ orl(rax, 0xc00);
        __ movw(Address(rsp, 2), rax);
        __ fldcw(Address(rsp, 2));
        __ fwait();
        __ fistp_d(result_low_word);
        __ fldcw(Address(rsp, 0));
        __ fwait();
        // This gets the entire long in rax on 64bit
        __ movptr(rax, result_low_word);
        // testing of high bits
        __ movl(rdx, result_high_word);
        __ mov(rcx, rax);
        // What the heck is the point of the next instruction???
        __ xorl(rcx, 0x0);
        __ movl(rsi, 0x80000000);
        __ xorl(rsi, rdx);
        __ orl(rcx, rsi);
        __ jcc(Assembler::notEqual, do_return);
        __ fldz();
        __ fcomp_d(value_low_word);
        __ fnstsw_ax();
        __ testl(rax, 0x4100);  // ZF & CF == 0
        __ jcc(Assembler::equal, return_min_jlong);
        // return max_jlong
        __ mov64(rax, CONST64(0x7fffffffffffffff));
        __ jmp(do_return);

        __ bind(return_min_jlong);
        __ mov64(rax, UCONST64(0x8000000000000000));
        __ jmp(do_return);

        __ bind(return0);
        __ fpop();
        __ xorptr(rax, rax);

        __ bind(do_return);
        __ addptr(rsp, 32);
        LP64_ONLY(__ pop(rdx);)
        __ pop(rcx);
        __ pop(rsi);
        __ ret(0);
      }
      break;
#endif // TODO

    case predicate_failed_trap_id:
      {
        __ set_info("predicate_failed_trap", dont_gc_arguments);

        OopMap* map = save_live_registers(sasm);

        int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, predicate_failed_trap));
        oop_maps = new OopMapSet();
        oop_maps->add_gc_map(call_offset, map);
        restore_live_registers(sasm);

        DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
        assert(deopt_blob != nullptr, "deoptimization blob must have been created");

        __ load_const_optimized(Z_R1_scratch, deopt_blob->unpack_with_reexecution());
        __ z_br(Z_R1_scratch);
      }
      break;

    default:
      {
        __ should_not_reach_here(FILE_AND_LINE, id);
      }
      break;
  }
  return oop_maps;
}

OopMapSet* Runtime1::generate_handle_exception(StubID id, StubAssembler *sasm) {
  __ block_comment("generate_handle_exception");

  // incoming parameters: Z_EXC_OOP, Z_EXC_PC

  // Save registers if required.
  OopMapSet* oop_maps = new OopMapSet();
  OopMap* oop_map = nullptr;
  Register reg_fp = Z_R1_scratch;

  switch (id) {
    case forward_exception_id: {
      // We're handling an exception in the context of a compiled frame.
      // The registers have been saved in the standard places. Perform
      // an exception lookup in the caller and dispatch to the handler
      // if found. Otherwise unwind and dispatch to the callers
      // exception handler.
      oop_map = generate_oop_map(sasm);

      // Load and clear pending exception oop into.
      __ z_lg(Z_EXC_OOP, Address(Z_thread, Thread::pending_exception_offset()));
      __ clear_mem(Address(Z_thread, Thread::pending_exception_offset()), 8);

      // Different stubs forward their exceptions; they should all have similar frame layouts
      // (a) to find their return address (b) for a correct oop_map generated above.
      assert(RegisterSaver::live_reg_frame_size(RegisterSaver::all_registers) ==
             RegisterSaver::live_reg_frame_size(RegisterSaver::all_registers_except_r2), "requirement");

      // Load issuing PC (the return address for this stub).
      const int frame_size_in_bytes = sasm->frame_size() * VMRegImpl::slots_per_word * VMRegImpl::stack_slot_size;
      __ z_lg(Z_EXC_PC, Address(Z_SP, frame_size_in_bytes + _z_abi16(return_pc)));
      DEBUG_ONLY(__ z_lay(reg_fp, Address(Z_SP, frame_size_in_bytes));)

      // Make sure that the vm_results are cleared (may be unnecessary).
      __ clear_mem(Address(Z_thread, JavaThread::vm_result_offset()),   sizeof(oop));
      __ clear_mem(Address(Z_thread, JavaThread::vm_result_2_offset()), sizeof(Metadata*));
      break;
    }
    case handle_exception_nofpu_id:
    case handle_exception_id:
      // At this point all registers MAY be live.
      DEBUG_ONLY(__ z_lgr(reg_fp, Z_SP);)
      oop_map = save_live_registers(sasm, id != handle_exception_nofpu_id, Z_EXC_PC);
      break;
    case handle_exception_from_callee_id: {
      // At this point all registers except Z_EXC_OOP and Z_EXC_PC are dead.
      DEBUG_ONLY(__ z_lgr(reg_fp, Z_SP);)
      __ save_return_pc(Z_EXC_PC);
      const int frame_size_in_bytes = __ push_frame_abi160(0);
      oop_map = new OopMap(frame_size_in_bytes / VMRegImpl::stack_slot_size, 0);
      sasm->set_frame_size(frame_size_in_bytes / BytesPerWord);
      break;
    }
    default:  ShouldNotReachHere();
  }

  // Verify that only Z_EXC_OOP, and Z_EXC_PC are valid at this time.
  __ invalidate_registers(Z_EXC_OOP, Z_EXC_PC, reg_fp);
  // Verify that Z_EXC_OOP, contains a valid exception.
  __ verify_not_null_oop(Z_EXC_OOP);

  // Check that fields in JavaThread for exception oop and issuing pc
  // are empty before writing to them.
  __ asm_assert_mem8_is_zero(in_bytes(JavaThread::exception_oop_offset()), Z_thread, "exception oop already set : " FILE_AND_LINE, 0);
  __ asm_assert_mem8_is_zero(in_bytes(JavaThread::exception_pc_offset()), Z_thread, "exception pc already set : " FILE_AND_LINE, 0);

  // Save exception oop and issuing pc into JavaThread.
  // (Exception handler will load it from here.)
  __ z_stg(Z_EXC_OOP, Address(Z_thread, JavaThread::exception_oop_offset()));
  __ z_stg(Z_EXC_PC, Address(Z_thread, JavaThread::exception_pc_offset()));

#ifdef ASSERT
  { NearLabel ok;
    __ z_cg(Z_EXC_PC, Address(reg_fp, _z_abi16(return_pc)));
    __ branch_optimized(Assembler::bcondEqual, ok);
    __ stop("use throwing pc as return address (has bci & oop map)");
    __ bind(ok);
  }
#endif

  // Compute the exception handler.
  // The exception oop and the throwing pc are read from the fields in JavaThread.
  int call_offset = __ call_RT(noreg, noreg, CAST_FROM_FN_PTR(address, exception_handler_for_pc));
  oop_maps->add_gc_map(call_offset, oop_map);

  // Z_RET(Z_R2): handler address
  //   will be the deopt blob if nmethod was deoptimized while we looked up
  //   handler regardless of whether handler existed in the nmethod.

  // Only Z_R2, is valid at this time, all other registers have been destroyed by the runtime call.
  __ invalidate_registers(Z_R2);

  switch(id) {
    case forward_exception_id:
    case handle_exception_nofpu_id:
    case handle_exception_id:
      // Restore the registers that were saved at the beginning.
      __ z_lgr(Z_R1_scratch, Z_R2);   // Restoring live registers kills Z_R2.
      restore_live_registers(sasm, id != handle_exception_nofpu_id);  // Pops as well the frame.
      __ z_br(Z_R1_scratch);
      break;
    case handle_exception_from_callee_id: {
      __ pop_frame();
      __ z_br(Z_R2); // Jump to exception handler.
    }
    break;
    default:  ShouldNotReachHere();
  }

  return oop_maps;
}


#undef __

const char *Runtime1::pd_name_for_address(address entry) {
  return "<unknown function>";
}
