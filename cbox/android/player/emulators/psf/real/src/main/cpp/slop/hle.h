/////////////////////////////////////////////////////////////////////////////
//
// hle - High-Level Emulation of the IOP BIOS / PS2 IOP kernel
//
// The HE core's only IOP path: PSF1/PSF2 boot through this HLE kernel,
// with no copyrighted PS2/PS1 BIOS blob.
//
/////////////////////////////////////////////////////////////////////////////

#ifndef __PSX_HLE_H__
#define __PSX_HLE_H__

#include "emuconfig.h"

#ifdef __cplusplus
extern "C" {
#endif

//
// The HLE sentinel opcode.
//
// This is the full 32-bit instruction word the core fetches at any address
// HLE has trampolined (BIOS exception vector, RAM call vectors 0x80/0xA0/
// 0xB0/0xC0). It decodes as SPECIAL with funct 0x0B, which the HE r3000
// decoder leaves unhandled (it would otherwise be a fatal "bad instruction").
// The value 11 matches the reference's FUNCT_HLECALL so ported trampolines need no
// rework.
//
#define R3000_HLE_FUNCT     (0x0B)
#define R3000_HLE_SENTINEL  ((uint32)0x0000000B)

// PS2 IOP idle-spin stub address (planted by hle.c at PS2 boot). Shared
// so the scheduler in hle_ps2.c can park the CPU here when no thread is
// runnable, instead of letting it busy-loop the just-frozen thread.
#define PS2_IDLE_PC         (0x00000800u)

//
// Memory-map callbacks for the 0x1FC00000 BIOS-ROM region: there is no
// BIOS image, so reads return the sentinel (trap into hle_dispatch) and
// stores are dropped.
//
uint32 EMU_CALL bios_hle_lw(void *iopstate, uint32 a, uint32 mask);

void   EMU_CALL bios_hle_sw(void *iopstate, uint32 a, uint32 d, uint32 mask);

//
// HLE-owned PS1 hardware callbacks. iop.c routes the interrupt-controller
// (0x1F801000-7F), root-counter (0x1F801100-2F / 0x1F801480-AF) and DMA
// (0x1F801080-FF) register ranges here in HLE mode, and calls hle_advance
// from iop_advance so the root counters run on HE's cycle clock.
//
uint32 EMU_CALL hle_intr_lw(void *iop, uint32 a, uint32 mask);

void   EMU_CALL hle_intr_sw(void *iop, uint32 a, uint32 d, uint32 mask);

uint32 EMU_CALL hle_timer_lw(void *iop, uint32 a, uint32 mask);

void   EMU_CALL hle_timer_sw(void *iop, uint32 a, uint32 d, uint32 mask);

uint32 EMU_CALL hle_dma_lw(void *iop, uint32 a, uint32 mask);

void   EMU_CALL hle_dma_sw(void *iop, uint32 a, uint32 d, uint32 mask);

void   EMU_CALL hle_advance(void *iop, uint32 elapse);

//
// PS2 IOP-kernel call. Invoked from r3000.c when `addiu $zero,$rs,imm`
// executes in HLE mode (the IRX syscall stub; imm = callnum). No-ops
// unless this is HLE PS2.
//
// Returns 1 if it redirected PC (module/thread switch -> cancel any
// pending delay-slot branch), 0 otherwise.
sint32 EMU_CALL hle_iop_call(void *r3000, void *iop, uint32 callnum);

//
// PS2 IOP kernel (he/hle_ps2.c). hle_ps2_boot returns the IRX entry PC
// (>=0) or negative on failure.
//
uint32 EMU_CALL hle_ps2_boot(void *iop);   // returns IRX entry PC, 0xffffffff on error
uint32 EMU_CALL hle_ps2_iop_call(void *iop, uint32 pc, uint32 callnum); // -> resume PC
uint32 EMU_CALL hle_ps2_thread_exit(void *iop); // -> next thread PC, 0xffffffff if idle
void   EMU_CALL hle_ps2_runcounters(void *iop);

// the reference psf2_gen per-sample frame model (vblank countdown + frame-boundary
// reschedule). Call once per output sample after hle_ps2_runcounters so
// WaitVblankStart sleeps a real video frame instead of returning instantly.
void   EMU_CALL hle_ps2_frame_tick(void);

// One sample of counter advance for the PSF2-HLE pump. Replicates
// hle_advance's per-768-cycle block (root counters + dma4 + runcounters)
// which hle_advance itself skips under the pump. Replaces the pump's
// bare hle_ps2_runcounters call.
void   EMU_CALL hle_ps2_pump_tick(void *iop);

uint32 EMU_CALL hle_ps2_check_resched(void *iop);

// PS2 SPU2 DMA-channel CHCR kick (the reference ps2_dma4/ps2_dma7), called from
// hle_dma_sw. Arms the async transfer + completion-handler delay.
void   EMU_CALL hle_ps2_dma4(uint32 madr, uint32 bcr, uint32 chcr);

void   EMU_CALL hle_ps2_dma7(uint32 madr, uint32 bcr, uint32 chcr);

// 1 while an SPU2 DMA (core 0=DMA4, 1=DMA7) is still in flight, so
// hle_dma_lw can report the CHCR busy bit truthfully for libsd's poll.
int    EMU_CALL hle_ps2_dma_busy(int core);

void   EMU_CALL hle_ps2_set_irq_mutex(int v);

int    EMU_CALL hle_ps2_irq_mutex(void);

void   EMU_CALL hle_ps2_spu_irq(void);   // SPU2 IRQ -> libsd irq9 handler
int    EMU_CALL hle_ps2_in_sync_drain(void); // 1 while a sync handler runs
int    EMU_CALL hle_ps2_pump_active(void);

void   EMU_CALL hle_ps2_set_pump(int v);

// Called from r3000.c at a safe instruction boundary (end of an execute
// slice) so the HLE can apply a deferred PS2 cooperative reschedule.
void   EMU_CALL hle_slice_end(void *r3000, void *iop);

// Nonzero while a softcall (queued IRQ/DMA/timer handler) is pending in
// the FIFO. The PSF2-HLE pump uses this to detect a completion raised
// by pump_tick this sample and drain it at-expiry (the reference timing).
sint32 EMU_CALL hle_ps1_softcall_pending(void);

//
// Invoked from r3000.c caseMINOR(R3000_HLE_FUNCT) when the CPU executes the
// sentinel. r3000 = the R3000 state (regs/PC), iop = the IOP_STATE (RAM).
// Returns 0/positive on success, negative to fault like a bad instruction.
//
sint32 EMU_CALL hle_dispatch(void *r3000, void *iop);

//
// Boot the HLE environment (the only IOP boot path). Writes sentinel
// trampolines and sets the initial register state.
//
void EMU_CALL hle_init_ps1(void *iop);

void EMU_CALL hle_init_ps2(void *iop);

#ifdef __cplusplus
}
#endif

#endif
