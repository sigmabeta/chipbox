/////////////////////////////////////////////////////////////////////////////
//
// spu - Top-level SPU emulation, for SPU and SPU2
//
/////////////////////////////////////////////////////////////////////////////

#ifndef __PSX_SPU_H__
#define __PSX_SPU_H__

#include "emuconfig.h"

#ifdef __cplusplus
extern "C" {
#endif

sint32 EMU_CALL spu_init(void);
/* version = 1 for PS1, 2 for PS2 */
uint32 EMU_CALL spu_get_state_size(uint8 version);
void   EMU_CALL spu_clear_state(void *state, uint8 version);

void   EMU_CALL spu_render(void *state, sint16 *buf, uint32 samples);

void   EMU_CALL spu_render_ext(void *state, sint16 *buf, sint16 *ext, uint32 samples);
uint16 EMU_CALL spu_lh(void *state, uint32 a);
void   EMU_CALL spu_sh(void *state, uint32 a, uint16 d);

void   EMU_CALL
spu_dma(void *state, uint32 core, void *mem, uint32 mem_ofs, uint32 mem_mask, uint32 bytes,
        int iswrite);

void   EMU_CALL spu_set_dma_complete(void *state, uint32 core);

uint32 EMU_CALL spu_cycles_until_interrupt(void *state, uint32 samples);

////////////////////////////////////////////////////////////////////////////////
/*
** Batched SPU rendering -- INTERNAL, used only by the PSF2 pump in iop.c.
** Not a user-facing option; it is always on. Output must stay bit-identical.
**
** THE INVARIANT: every guest-visible sound-chip entry point flushes the
** pending batch before it touches SPU state, because the guest can only
** observe a sample-accurate SPU through this API. So spu_lh, spu_sh,
** spu_dma, spu_set_dma_complete, spu_cycles_until_interrupt, spu_render
** (+_ext) and the enable_* setters all begin with spu_flush_batch();
** iop_execute() flushes before it returns (never hand back unrendered
** samples). The lifecycle functions (spu_init, spu_get_state_size,
** spu_clear_state) do not: they take no live state, and spu_clear_state()
** discards the pending batch along with the rest of the state. ANYTHING
** ADDED TO THIS SOUND-CHIP API LATER MUST FLUSH TOO.
**
** spu_render_queued() appends one or more samples to the pending batch
** instead of rendering them now; spu_flush_batch() renders the batch.
** spu_batch_allowed() answers the two state guards (no sound IRQ on either
** core; no running voice sampling, or about to sample, its core's reverb
** work area within the batch) -- while it is false the caller renders one
** sample at a time via spu_render().
*/
void EMU_CALL spu_render_queued(void *state, sint16 *buf, uint32 samples);
void EMU_CALL spu_flush_batch(void *state);
int  EMU_CALL spu_batch_allowed(void *state);

/*
** Bounded SPU-IRQ look-ahead.
**
** spu_cycles_until_interrupt() predicts when the SPU decoder will next
** cross its programmed IRQ address by speculatively rendering ahead on a
** throw-away copy of the SPU state. With SPU IRQ enabled but the crossing
** far away, an unbounded scan re-rendered the whole pending buffer on
** every IOP slice -- O(n^2), since each short slice rescans the full
** remaining window (Koudelka ran ~10x slower than real time and tripped
** the test harness's per-game timeout, looking like a crash).
**
** The scan is now bounded to SPUIRQ_LOOKAHEAD_SAMPLES. If the crossing is
** not within that window the function returns SPUIRQ_NOT_NEAR ("armed but
** not imminent") -- distinct from 0xFFFFFFFF ("IRQ disabled / never").
** The caller then does NOT fire the IRQ this slice but DOES cap the slice
** to the look-ahead so the next (cheap, bounded) scan cannot step past
** the crossing. Once the crossing is within the window the exact cycle is
** returned, so IRQ delivery stays sample-accurate -- only the prediction
** *cost* is bounded, never its timing.
*/
#define SPUIRQ_LOOKAHEAD_SAMPLES (200u)            /* == spucore RENDERMAX */
#define SPUIRQ_LOOKAHEAD_CYCLES  (SPUIRQ_LOOKAHEAD_SAMPLES * 768u)
#define SPUIRQ_NOT_NEAR          (0xFFFFFFFEu)

/*
** Enable/disable main or reverb
*/
void EMU_CALL spu_enable_main(void *state, uint8 enable);
void EMU_CALL spu_enable_reverb(void *state, uint8 enable);

/*
** Enable/disable mute for a given channel
*/
void EMU_CALL spu_enable_mute(void *state, uint8 channel, uint8 enable);

#ifdef __cplusplus
}
#endif

#endif
