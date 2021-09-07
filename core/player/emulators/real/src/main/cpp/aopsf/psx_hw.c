/*
	Audio Overload SDK - PSX and IOP hardware emulation

	Copyright (c) 2007 R. Belmont and Richard Bannister.

	All rights reserved.

	Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

	* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
	* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
	* Neither the names of R. Belmont and Richard Bannister nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
	"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
	LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
	A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
	CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
	EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
	PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
	PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
	LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
	NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
	SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/*
    psx_hw.c - Minimal PSX/IOP hardware glue/emulation/whatever

    supported: main RAM (2 MB, mirrored to fill an 8 MB space like on real HW)
               DMA channel 4 (SPURAM) in both directions (including completion IRQ)
	       VBL IRQ
	       Root counters 2 and 3 including completion events and IRQs
	       Some BIOS services including exception handling (via HLE)
	       HLE emulation of IOP operating system, including multithreading
	       SPU(2), SPU(2)RAM (via PEOpS)



    Special notes:
    PSF1
    	- Chocobo's Dungeon 2 contains an illegal code sequence (patched)

    PSF2
	- Shadow Hearts assumes that the wave buffer alloc will go to 0x80060000 and the sequence buffer to 0x80170000.
	  Our memory management doesn't work out that way, so we have to (wait for it) cheese it.
*/

#include <stdio.h>
#include "cpuintrf.h"
#include "psx.h"
#include "spu/spu.h"
#include "spu/spucore.h"

#include "psx_external.h"
#include "psx_internal.h"

#if DEBUG_THREADING
static char *_ThreadStateNames[TS_MAXSTATE] = { "RUNNING", "DORMANT", "WAITEVFLAG", "WAITSEMA", "WAITDELAY", "SLEEPING" };
#endif

#if DEBUG_HLE_IOP
static char *seek_types[3] = { "SEEK_SET", "SEEK_CUR", "SEEK_END" };
#endif

#if DEBUG_HLE_IOP || DEBUG_HLE_BIOS || DEBUG_THREADING || DEBUG_UNK_RW
                                                                                                                        void printlog(PSX_STATE *psx, const char *fmt, ...)
{
	if (psx->console_callback)
	{
		char buffer[1024];

		va_list vl;
		va_start(vl, fmt);
		vsnprintf(buffer, 1023, fmt, vl);
		va_end(vl);

		buffer[1023] = '\0';

		psx->console_callback(psx->console_context, buffer);
	}
}
#endif

// take a snapshot of the CPU state for a thread
static void FreezeThread(PSX_STATE *psx, int32 iThread, int flag) {
    int i;
    union cpuinfo mipsinfo;

#if DEBUG_THREADING
    //	printlog(psx, "IOP: FreezeThread(%d)\n", iThread);
#endif

    for (i = 0; i < 32; i++) {
        if (i == 0 || i == 26 || i == 27) continue;
        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + i, &mipsinfo);
        psx->threads[iThread].save_regs[i] = mipsinfo.i;
    }
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
    psx->threads[iThread].save_regs[32] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
    psx->threads[iThread].save_regs[33] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYV, &mipsinfo);
    psx->threads[iThread].save_regs[35] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &mipsinfo);
    psx->threads[iThread].save_regs[36] = mipsinfo.i;


    // if a thread is freezing itself due to a IOP syscall, we must save the RA as the PC
    // to come back to or else the syscall will recurse
    if (flag) {
        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
    } else {
        mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
    }
    psx->threads[iThread].save_regs[34] = mipsinfo.i;

#if DEBUG_THREADING
                                                                                                                            {
		//char buffer[256];

		//DasmMIPS(buffer, mipsinfo.i, &psx->psx_ram[(mipsinfo.i & 0x7fffffff)/4]);

		printlog(psx, "IOP: FreezeThread(%d) => %08x\n", iThread, psx->threads[iThread].save_regs[34]);
	}
#endif
}

// restore the CPU state from a thread's snapshot
static void ThawThread(PSX_STATE *psx, int32 iThread) {
    int i;
    union cpuinfo mipsinfo;

    // the first time a thread is put on the CPU,
    // some special setup is required
#if DEBUG_THREADING
                                                                                                                            {
		//char buffer[256];

		//mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
		//DasmMIPS(buffer, mipsinfo.i, &psx->psx_ram[(mipsinfo.i & 0x7fffffff)/4]);

		printlog(psx, "IOP: ThawThread(%d) => %08x\n", iThread, psx->threads[iThread].save_regs[34]);
	}
#endif

    for (i = 0; i < 32; i++) {
        if (i == 0 || i == 26 || i == 27) continue;
        mipsinfo.i = psx->threads[iThread].save_regs[i];
        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + i, &mipsinfo);
    }

    mipsinfo.i = psx->threads[iThread].save_regs[32];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
    mipsinfo.i = psx->threads[iThread].save_regs[33];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
    mipsinfo.i = psx->threads[iThread].save_regs[34];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
    mipsinfo.i = psx->threads[iThread].save_regs[35];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYV, &mipsinfo);
    mipsinfo.i = psx->threads[iThread].save_regs[36];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &mipsinfo);
}

// find a new thread to run
void ps2_reschedule(PSX_STATE *psx) {
    int i, starti, iNextThread;

    iNextThread = -1;

    if (mips_get_status(&psx->mipscpu) & (1 << 1))
        return;

    psx->rescheduleNeeded = 0;

    // see if any thread other than the current one is ready to run
    i = psx->iCurThread + 1;
    if (i >= psx->iNumThreads) {
        i = 0;
    }

    starti = i;

    // starting with the next thread after this one,
    // see who wants to run
    while (i < psx->iNumThreads) {
        if (i != psx->iCurThread) {
            if (psx->threads[i].iState == TS_RUNNING) {
                iNextThread = i;
                break;
            }
        }

        i++;
    }

    // if we started above thread 0 and didn't pick one,
    // go around and try from zero
    if ((starti > 0) && (iNextThread == -1)) {
        for (i = 0; i < psx->iNumThreads; i++) {
            if (i != psx->iCurThread) {
                if (psx->threads[i].iState == TS_RUNNING) {
                    iNextThread = i;
                    break;
                }
            }
        }
    }

    if (iNextThread != -1) {
#if DEBUG_THREADING
                                                                                                                                for (i = 0; i < psx->iNumThreads; i++)
		{
			printlog(psx, "Thread %02d: %s\n", i, _ThreadStateNames[psx->threads[i].iState]);
		}
#endif

        if (psx->iCurThread != -1)
            FreezeThread(psx, psx->iCurThread, 0);

        ThawThread(psx, iNextThread);
        psx->iCurThread = iNextThread;
        psx->threads[psx->iCurThread].iState = TS_RUNNING;
    } else {
        if (psx->iCurThread != -1 && psx->threads[psx->iCurThread].iState != TS_RUNNING) {
            FreezeThread(psx, psx->iCurThread, 0);
            mips_shorten_frame(&psx->mipscpu);    // kill the CPU
            psx->iCurThread = -1;    // no threads are active
        }
    }
}

static void psx_irq_update(PSX_STATE *psx) {
    union cpuinfo mipsinfo;

    if (!(psx->irq_masked) && (psx->irq_data & psx->irq_mask) != 0) {    // assert the line
        psx->WAI = 0;
        mipsinfo.i = ASSERT_LINE;
        mips_set_info(&psx->mipscpu, CPUINFO_INT_INPUT_STATE + MIPS_IRQ0, &mipsinfo);
    } else {
        // clear the line
        mipsinfo.i = CLEAR_LINE;
        mips_set_info(&psx->mipscpu, CPUINFO_INT_INPUT_STATE + MIPS_IRQ0, &mipsinfo);
    }
}

void psx_irq_set(PSX_STATE *psx, uint32 irq) {
    psx->irq_data |= irq;

    psx_irq_update(psx);
}

uint32 ioptimer_lw(struct IOPTIMER_STATE *state, offs_t offset, uint32 mem_mask) {
    uint32 d = 0;
    uint32 c = (offset >> 4) & 0xf;
    struct IOPTIMER_COUNTER *ctr;
    if (offset >= 0x1f801480)
        c -= 5;
    if (c >= COUNTERS) return 0;
    ctr = state->counter + c;
    switch (offset & 0xc) {
        case 0:
            if (ctr->delta) { d = (uint32) ((ctr->counter) / (uint64) (ctr->delta)); }
            break;
        case 4:
            d = ctr->status;
            ctr->status = 0;
            break;
        case 8:
            d = (uint32) (ctr->compare);
            break;
    }
    if (c < 3) d &= 0xffff;
    return d & ~mem_mask;
}

uint32 psx_hw_read(PSX_STATE *psx, offs_t offset, uint32 mem_mask) {
    if (offset >= 0x00000000 && offset <= 0x007fffff) {
        offset &= 0x1fffff;
        return LE32(psx->psx_ram[offset >> 2]);
    }

    if (offset >= 0x80000000 && offset <= 0x807fffff) {
        offset &= 0x1fffff;
        return LE32(psx->psx_ram[offset >> 2]);
    }

    if (offset >= 0x1f800000 && offset <= 0x1f800fff) {
        offset &= 0x3ff;
        return LE32(psx->scratch[offset >> 2]);
    }

    if (offset == 0xbfc00180 || offset == 0xbfc00184)    // exception vector
    {
        return FUNCT_HLECALL;
    }

    if (offset == 0x1f801014) {
        return psx->spu_delay;
    }

    if (offset == 0xbf801014) {
        return psx->spu_delay;
    }

    if (offset == 0x1f801814) {
        psx->gpu_stat ^= 0xffffffff;
        return psx->gpu_stat;
    }

    if (offset >= 0x1f801c00 && offset <= 0x1f801dff) {
        if ((mem_mask == 0xffff0000) || (mem_mask == 0xffffff00)) {
            return spu_lh(SPUSTATE, offset) & ~mem_mask;
        } else if (mem_mask == 0x0000ffff) {
            return spu_lh(SPUSTATE, offset) << 16;
        } else {
            psx->error_ptr += sprintf(psx->error_ptr, "SPU: read unknown mask %08x\n", mem_mask);
        }
    }

    if (offset >= 0xbf900000 && offset <= 0xbf9007ff) {
        if ((mem_mask == 0xffff0000) || (mem_mask == 0xffffff00)) {
            return spu_lh(SPUSTATE, offset) & ~mem_mask;
        } else if (mem_mask == 0x0000ffff) {
            return spu_lh(SPUSTATE, offset) << 16;
        } else if (mem_mask == 0) {
            return spu_lh(SPUSTATE, offset) | spu_lh(SPUSTATE, offset + 2) << 16;
        } else {
            psx->error_ptr += sprintf(psx->error_ptr, "SPU2: read unknown mask %08x\n", mem_mask);
        }
    }

    if ((offset >= 0x1f801100 && offset <= 0x1f80112f) ||
        (offset >= 0x1f801480 && offset <= 0x1f8014af)) {
        return ioptimer_lw(&psx->root_cnts, offset, mem_mask);
    }

    if (offset == 0x1f8010f0) {
        return psx->dma_pcr;
    } else if (offset == 0x1f8010f4) {
        return psx->dma_icr;
    } else if (offset == 0x1f801070) {
//		printf("Read IRQ_data %x (mask %08x)\n", irq_data, mem_mask);
        return psx->irq_data;
    } else if (offset == 0x1f801074) {
        return psx->irq_mask;
    } else if (offset == 0x1f801078) {
        uint32 val = psx->irq_masked ? 0 : 1;
        psx->irq_masked = 1;
        psx_irq_update(psx);
        return val;
    }

/*	if (offset == 0xbf801508)
	{
		return psx->dma7_bcr;
	}*/

    if (offset == 0xbf920344) {
        return 0x80808080;
    }

#if DEBUG_UNK_RW
                                                                                                                            {
		union cpuinfo mipsinfo;

		mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
		printlog(psx, "Unknown read: %08x, mask %08x (PC=%x)\n", offset&~3, mem_mask, mipsinfo.i);
	}
#endif
    return 0;
}

#define DMA_SPU_CYCLES_PER_HALFWORD (8)

static void psx_dma4(PSX_STATE *psx, uint32 madr, uint32 bcr, uint32 chcr) {
    int delay = 0;
    if (chcr == 0x01000201)    // cpu to SPU
    {
//		printf("DMA4: RAM %08x to SPU\n", madr);
        bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
        spu_dma(SPUSTATE, 0, psx->psx_ram, madr & 0x1ffffc, 0x1ffffc, bcr, 1);
        delay = (bcr / 2) * DMA_SPU_CYCLES_PER_HALFWORD;
    } else {
//		printf("DMA4: SPU to RAM %08x\n", madr);
        bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
        spu_dma(SPUSTATE, 0, psx->psx_ram, madr & 0x1ffffc, 0x1ffffc, bcr, 0);
        delay = (bcr / 2) * DMA_SPU_CYCLES_PER_HALFWORD;
    }

    delay /= 768;
    if (!delay)
        delay = 1;

    psx->dma_timer = delay;
    psx->dma4_delay = delay;
}

static void ps2_dma4(PSX_STATE *psx, uint32 madr, uint32 bcr, uint32 chcr) {
    int delay = 0;

    if (chcr == 0x01000201)    // cpu to SPU2
    {
#if DEBUG_HLE_IOP
        printlog(psx, "DMA4: RAM %08x to SPU2\n", madr);
#endif
        bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
        spu_dma(SPUSTATE, 0, psx->psx_ram, madr & 0x1ffffc, 0x1ffffc, bcr, 1);
        delay = (bcr / 2) * DMA_SPU_CYCLES_PER_HALFWORD;
    } else {
#if DEBUG_HLE_IOP
        printlog(psx, "DMA4: SPU2 to RAM %08x\n", madr);
#endif
        bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
        spu_dma(SPUSTATE, 0, psx->psx_ram, madr & 0x1ffffc, 0x1ffffc, bcr, 0);
        delay = (bcr / 2) * DMA_SPU_CYCLES_PER_HALFWORD;
    }

    delay /= 768;
    if (!delay)
        delay = 1;

    psx->dma_timer = delay;
    psx->dma4_delay = delay;
}

static void ps2_dma7(PSX_STATE *psx, uint32 madr, uint32 bcr, uint32 chcr) {
    int delay = 0;

    if ((chcr == 0x01000201) || (chcr == 0x00100010) || (chcr == 0x000f0010) ||
        (chcr == 0x00010010))    // cpu to SPU2
    {
#if DEBUG_HLE_IOP
        printlog(psx, "DMA7: RAM %08x to SPU2\n", madr);
#endif
        bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
        spu_dma(SPUSTATE, 1, psx->psx_ram, madr & 0x1ffffc, 0x1ffffc, bcr, 1);
        delay = (bcr / 2) * DMA_SPU_CYCLES_PER_HALFWORD;
    } else {
#if DEBUG_HLE_IOP
        printlog(psx, "DMA7: SPU2 to RAM %08x\n", madr);
#endif
        bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
//		SPU2readDMA7Mem(madr&0x1fffff, bcr);
        delay = (bcr / 2) * DMA_SPU_CYCLES_PER_HALFWORD;
    }

    delay /= 768;
    if (!delay)
        delay = 1;

    psx->dma7_delay = delay;
}

#define IOP_INT_VBLANK  (1<<0)
#define IOP_INT_RTC0    (1<<4)
#define IOP_INT_RTC1    (1<<5)
#define IOP_INT_RTC2    (1<<6)
#define IOP_INT_RTC3    (1<<14)
#define IOP_INT_RTC4    (1<<15)
#define IOP_INT_RTC5    (1<<16)

static const uint32 intrflag[COUNTERS] = {
        IOP_INT_RTC0, IOP_INT_RTC1, IOP_INT_RTC2,
        IOP_INT_RTC3, IOP_INT_RTC4, IOP_INT_RTC5
};

void ioptimer_set_rates(struct IOPTIMER_STATE *state, uint32 sysclock, uint32 dots, uint32 lines,
                        uint32 lines_visible, uint32 refresh_rate) {
    state->hz_sysclock = sysclock;
    state->hz_hline = lines * refresh_rate;
    state->hz_pixel = state->hz_hline * dots;

    state->field_counter = 0;
    state->field_vblank = ((uint64) (lines_visible)) * ((uint64) (sysclock));
    state->field_total = ((uint64) (lines)) * ((uint64) (sysclock));
}

static uint32 EMU_CALL cycles_until_gate(struct IOPTIMER_STATE *state) {
    uint64 diff;
    if (!(state->hz_hline)) return 0xFFFFFFFF;
    if (state->field_counter < state->field_vblank) {
        diff = state->field_vblank - state->field_counter;
    } else {
        diff = state->field_total - state->field_counter;
    }
    diff += (state->hz_hline - 1);
    diff /= ((uint64) (state->hz_hline));
    if (diff > 0xFFFFFFFF) diff = 0xFFFFFFFF;
    if (diff < 1) diff = 1;
    return (uint32) diff;
}

uint32 ioptimer_cycles_until_interrupt(struct IOPTIMER_STATE *state) {
    uint32 min = cycles_until_gate(state);
    uint32 c;
    //
    // counters
    //
    for (c = 0; c < COUNTERS; c++) {
        uint64 diff;
        if (!(state->counter[c].delta)) continue;
        if (state->counter[c].counter >= state->counter[c].target) {
            diff = 0;
        } else {
            diff = state->counter[c].target - state->counter[c].counter;
            diff += (state->counter[c].delta - 1);
            diff /= ((uint64) (state->counter[c].delta));
        }
        if (diff < ((uint64) (min))) min = (uint32) diff;
    }
    if (min < 1) min = 1;
    return min;
}

static uint32 counters_advance(struct IOPTIMER_STATE *state, uint32 cycles) {
    uint32 intr = 0;
    uint32 c;
    for (c = 0; c < COUNTERS; c++) {
        struct IOPTIMER_COUNTER *ctr = state->counter + c;;
        if (!ctr->delta) continue;
        ctr->counter += ((uint64) (cycles)) * ((uint64) (ctr->delta));
        //
        // timer loop handling
        //
        for (;;) {
            //
            // if we're below the given target, then good - quit.
            //
            if (ctr->counter < ctr->target) break;
            //
            // otherwise, we have a transition to make.
            //
            if (ctr->target_is_overflow) {
                ctr->status |= 0x1000;
                if (ctr->mode & 0x20) intr |= intrflag[c];
                // counter always loops on overflow (duh!)
                ctr->counter -= ctr->target;
                // counter now becomes the compare target
                ctr->target = ((uint64) (state->hz_sysclock)) * ((uint64) (ctr->compare));
                ctr->target_is_overflow = 0;
            } else {
                ctr->status |= 0x800;
                if (ctr->mode & 0x10) intr |= intrflag[c];
                // counter only loops on target if the appropriate bit is set
                if (ctr->mode & 8) {
                    // no change to target, just loop counter
                    ctr->counter -= ctr->target;
                    // no target loop - proceed to overflow
                } else {
                    if (c < 3) {
                        ctr->target = ((uint64) (state->hz_sysclock)) << 16;
                    } else {
                        ctr->target = ((uint64) (state->hz_sysclock)) << 32;
                    }
                    ctr->target_is_overflow = 1;
                }
            }
        }
    }
    return intr;
}

static void counter_start(struct IOPTIMER_STATE *state, uint32 c) {
    struct IOPTIMER_COUNTER *ctr = state->counter + c;
    uint32 delta = state->hz_sysclock;
    switch (c) {
        case 0:
            if (ctr->mode & 0x100) { delta = state->hz_pixel; }
            break;
        case 1:
            if (ctr->mode & 0x100) { delta = state->hz_hline; }
            break;
        case 2:
            if (ctr->mode & 0x200) { delta /= 8; }
            break;
        case 3:
            if (ctr->mode & 0x100) { delta = state->hz_hline; }
            break;
        case 4:
        case 5:
            switch ((ctr->mode >> 13) & 3) {
                case 0:
                    delta /= 1;
                    break;
                case 1:
                    delta /= 8;
                    break;
                case 2:
                    delta /= 16;
                    break;
                case 3:
                    delta /= 256;
                    break;
            }
            break;
    }
    ctr->counter = 0;
    ctr->delta = delta;
    ctr->target = ((uint64) (ctr->compare)) * ((uint64) (state->hz_sysclock));
    ctr->target_is_overflow = 0;
}

static void counter_stop(struct IOPTIMER_STATE *state, uint32 c) {
    state->counter[c].delta = 0;
}

static void gate_transition(struct IOPTIMER_STATE *state) {
    uint32 c;
    for (c = 0; c < COUNTERS; c++) {
        // must be both enabled and gate-enabled
        if ((state->counter[c].mode & 0x41) != 0x41) continue;
        switch (state->counter[c].mode & 0x6) {
            case 0x0: // TM_GATE_ON_Count
                if (state->gate) { counter_start(state, c); }
                else { counter_stop(state, c); }
                break;
            case 0x2: // TM_GATE_ON_ClearStart
                if (state->gate) { counter_start(state, c); }
                break;
            case 0x4: // TM_GATE_ON_Clear_OFF_Start
                if (state->gate) { counter_stop(state, c); }
                else { counter_start(state, c); }
                break;
            case 0x6: // TM_GATE_ON_Start
                if (state->gate) {
                    // one-time start: disable gate bit
                    state->counter[c].mode &= ~1;
                    counter_start(state, c);
                }
                break;
        }
    }
}

static uint32 gate_advance(struct IOPTIMER_STATE *state, uint32 cycles) {
    uint32 intr = 0;
    state->field_counter += ((uint64) (cycles)) * ((uint64) (state->hz_hline));
    //
    // gate overflow loop
    //
    for (;;) {
        //
        // if we're below the given target, then good - quit.
        //
        if (state->gate) {
            if (state->field_counter < state->field_vblank) break;
            //
            // gate transition 1->0
            //
            state->gate = 0;
            gate_transition(state);
            intr |= IOP_INT_VBLANK;
        } else {
            if (state->field_counter < state->field_total) break;
            //
            // gate transition 0->1
            //
            state->gate = 1;
            gate_transition(state);
            state->field_counter -= state->field_total;
        }
    }
    return intr;
}

uint32 ioptimer_advance(struct IOPTIMER_STATE *state, uint32 cycles) {
    uint32 intr = 0;
    uint32 cycles_left = cycles;
    while (cycles_left) {
        uint32 g = cycles_until_gate(state);
        if (g > cycles_left) g = cycles_left;
        intr |= counters_advance(state, g);
        intr |= gate_advance(state, g);
        cycles_left -= g;
    }
    return intr;
}

void ioptimer_sw(struct IOPTIMER_STATE *state, offs_t offset, uint32 data, uint32 mem_mask) {
    int c = (offset >> 4) & 0xf;
    struct IOPTIMER_COUNTER *ctr;
    if (offset >= 0x1f801480)
        c -= 5;
    if (c >= COUNTERS) return;
    ctr = state->counter + c;

    data &= ~mem_mask;
    if (c < 3)
        data &= 0xffff;

    switch (offset & 0xc) {
        case 4:
            ctr->delta = 0;
            ctr->mode = data;
            if (data & 0x40) {
                if ((data & 7) != 7) {
                    counter_start(state, c);
                }
            }
            break;
        case 8:
            ctr->compare = data;
            if (!ctr->compare) {
                if (c < 3) {
                    ctr->compare = 0x10000;
                } else {
                    ctr->compare = 0x100000000;
                }
            }

            if (ctr->delta) {
                ctr->target = ctr->compare * ((uint64) (state->hz_sysclock));
                ctr->target_is_overflow = 0;
                if (ctr->counter >= ctr->target) {
                    if (c < 3) {
                        ctr->target = ((uint64) (state->hz_sysclock)) << 16;
                    } else {
                        ctr->target = ((uint64) (state->hz_sysclock)) << 32;
                    }
                    ctr->target_is_overflow = 1;
                }
            }
            break;
    }
}

void psx_hw_write(PSX_STATE *psx, offs_t offset, uint32 data, uint32 mem_mask) {
    union cpuinfo mipsinfo;

    if (offset >= 0x00000000 && offset <= 0x007fffff) {
        offset &= 0x1fffff;
//		if (offset < 0x10000) printf("Write %x to kernel @ %x\n", data, offset);

        mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

        psx->psx_ram[offset >> 2] &= LE32(mem_mask);
        psx->psx_ram[offset >> 2] |= LE32(data);
        return;
    }

    if (offset >= 0x80000000 && offset <= 0x807fffff) {
        offset &= 0x1fffff;
//		if (offset < 0x10000) printf("Write %x to kernel @ %x\n", data, offset);
        mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
        psx->psx_ram[offset >> 2] &= LE32(mem_mask);
        psx->psx_ram[offset >> 2] |= LE32(data);
        return;
    }

    if (offset >= 0x1f800000 && offset <= 0x1f800fff) {
        offset &= 0x3ff;
        psx->scratch[offset >> 2] &= LE32(mem_mask);
        psx->scratch[offset >> 2] |= LE32(data);
        return;
    }

    if (offset == 0x1f801014 || offset == 0xbf801014) {
        psx->spu_delay &= mem_mask;
        psx->spu_delay |= data;
        return;
    }

    if (offset >= 0x1f801c00 && offset <= 0x1f801dff) {
        //		printf("SPU2 wrote %x to SPU1 address %x!\n", data, offset);
        if (mem_mask == 0xffff0000) {
            spu_sh(SPUSTATE, offset, data);
            return;
        } else if (mem_mask == 0x0000ffff) {
            spu_sh(SPUSTATE, offset, data >> 16);
            return;
        } else {
            psx->error_ptr += sprintf(psx->error_ptr, "SPU: write unknown mask %08x\n", mem_mask);
        }
    }

    if (offset >= 0xbf900000 && offset <= 0xbf9007ff) {
        if (mem_mask == 0xffff0000) {
            spu_sh(SPUSTATE, offset, data);
            return;
        } else if (mem_mask == 0x0000ffff) {
            spu_sh(SPUSTATE, offset, data >> 16);
            return;
        } else if (mem_mask == 0) {
            spu_sh(SPUSTATE, offset, data & 0xffff);
            spu_sh(SPUSTATE, offset + 2, data >> 16);
            return;
        } else {
            psx->error_ptr += sprintf(psx->error_ptr, "SPU2: write unknown mask %08x\n", mem_mask);
        }
    }

    if ((offset >= 0x1f801100 && offset <= 0x1f80112f) ||
        (offset >= 0x1f801480 && offset <= 0x1f8014af)) {
        ioptimer_sw(&psx->root_cnts, offset, data, mem_mask);
        return;
    }

    // DMA4
    if (offset == 0x1f8010c0) {
        psx->dma4_madr = data;
        return;
    } else if (offset == 0x1f8010c4 || offset == 0x1f8010c6) {
        psx->dma4_bcr &= mem_mask;
        psx->dma4_bcr |= data;
        return;
    } else if (offset == 0x1f8010c8) {
        psx->dma4_chcr = data;
        psx_dma4(psx, psx->dma4_madr, psx->dma4_bcr, psx->dma4_chcr);

        return;
    } else if (offset == 0x1f8010f0) {
        psx->dma_pcr = (psx->dma_pcr & mem_mask) | (data & ~mem_mask);

        return;
    } else if (offset == 0x1f8010f4) {
        psx->dma_icr = (psx->dma_icr & mem_mask) |
                       (~mem_mask & 0x80000000 & psx->dma_icr) |
                       (~data & ~mem_mask & 0x7f000000 & psx->dma_icr) |
                       (data & ~mem_mask & 0x00ffffff);

        if ((psx->dma_icr & 0x7f000000) != 0) {
            psx->dma_icr &= ~0x80000000;
        }

        return;
    } else if (offset == 0x1f801070) {
        psx->irq_data = (psx->irq_data & mem_mask) | (psx->irq_data & psx->irq_mask & data);
        psx_irq_update(psx);
        return;
    } else if (offset == 0x1f801074) {
        psx->irq_mask &= mem_mask;
        psx->irq_mask |= data;
        psx_irq_update(psx);
        return;
    } else if (offset == 0x1f801078) {
        psx->irq_masked = (data ^ 1) & 1;
        psx_irq_update(psx);
        return;
    }

    // PS2 DMA4
    if (offset == 0xbf8010c0) {
        psx->dma4_madr = data;
        return;
    } else if (offset == 0xbf8010c8) {
        psx->dma4_chcr = data;
        ps2_dma4(psx, psx->dma4_madr, psx->dma4_bcr, psx->dma4_chcr);

        return;
    }

    if (offset == 0xbf8010c4 || offset == 0xbf8010c6) {
        psx->dma4_bcr &= mem_mask;
        psx->dma4_bcr |= data;
        return;
    }

    // PS2 DMA7
    if (offset == 0xbf801500) {
        psx->dma7_madr = data;
        return;
    } else if (offset == 0xbf801508) {
        psx->dma7_chcr = data;
        ps2_dma7(psx, psx->dma7_madr, psx->dma7_bcr, psx->dma7_chcr);
        return;
    }

    if (offset == 0xbf801504 || offset == 0xbf801506) {
        psx->dma7_bcr &= mem_mask;
        psx->dma7_bcr |= data;
        return;
    }

#if DEBUG_UNK_RW
                                                                                                                            {
		union cpuinfo mipsinfo;

		mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
		printlog(psx, "Unknown write: %08x to %08x, mask %08x (PC=%x)\n", data, offset&~3, mem_mask, mipsinfo.i);
	}
#endif
}

// called per sample, 1/44100th of a second (768 clock cycles)
void psx_hw_slice(PSX_STATE *psx) {
    int oldICount;
    union cpuinfo mipsinfo;

    psx_hw_runcounters(psx);

    if (!psx->WAI)
        mips_execute(&psx->mipscpu, 768 / CLOCK_DIV);

    if (psx->dma_timer) {
        psx->dma_timer--;
        if (psx->dma_timer == 0) {
            if (psx->eventsAllocated) {
                const int eventToCheck = 0xf0000009;
                int i;
                for (i = 0; i < MAX_EVENT; i++) {
                    if (!psx->Event[i].isValid) continue;
                    if (psx->Event[i].classId != eventToCheck) continue;
                    if (!psx->Event[i].enabled) continue;

                    psx->Event[i].fired = 1;

                    if (!psx->Event[i].func) continue;

                    call_irq_routine(psx, psx->Event[i].func, 0);
                }
            }
            if (psx->dma_icr & (1 << (16 + 4))) {
                spu_interrupt_dma4(SPUSTATE);
                psx->dma_icr |= (1 << (24 + 4));
                psx_irq_set(psx, 0x0008);
            }
        }
    }
}

void ps2_hw_slice(PSX_STATE *psx) {
    int i = 0;

    psx->timerexp = 0;
    psx_hw_runcounters(psx);

    if (psx->iCurThread != -1) {
        mips_execute(&psx->mipscpu, 768 / CLOCK_DIV);
    } else    // no thread, don't run CPU, just update counters
    {
        if (psx->timerexp || psx->rescheduleNeeded) {
            ps2_reschedule(psx);

            if (psx->iCurThread != -1) {
                mips_execute(&psx->mipscpu, (768 / CLOCK_DIV) - i);
                i = (768 / CLOCK_DIV);
            }
        }
    }
}

void psx_hw_frame(PSX_STATE *psx) {
    //psx_irq_set(psx, 1);
}

void ps2_hw_frame(PSX_STATE *psx) {
    if (!psx->irq_mutex)
        ps2_reschedule(psx);
}

// BIOS HLE
#define LONGJMP_BUFFER                (0x0200)
#define EVENTS_BEGIN                (0x3000)
#define EVENTS_SIZE                    (sizeof(EvtCtrlBlk) * MAX_EVENT)
#define B0TABLE_BEGIN                (EVENTS_BEGIN + EVENTS_SIZE)
#define B0TABLE_SIZE                (0x5D * 4)
#define C0TABLE_BEGIN                (B0TABLE_BEGIN + B0TABLE_SIZE)
#define C0TABLE_SIZE                (0x1C * 4)
#define C0_EXCEPTIONHANDLER_BEGIN    (C0TABLE_BEGIN + C0TABLE_SIZE)
#define C0_EXCEPTIONHANDLER_SIZE    (0x1000)

// heap block struct offsets
static void call_irq_routine(PSX_STATE *psx, uint32 routine, uint32 parameter) {
    int j, oldICount;
    union cpuinfo mipsinfo;

    if (!psx->irq_mutex) {
        psx->irq_mutex = 1;
    } else {
        psx->error_ptr += sprintf(psx->error_ptr, "IOP: ERROR!  IRQ reentry!\n");
        return;
    }

    // save regs for IRQ
    for (j = 0; j < 32; j++) {
        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + j, &mipsinfo);
        psx->irq_regs[j] = mipsinfo.i;
    }
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
    psx->irq_regs[32] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
    psx->irq_regs[33] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
    psx->irq_regs[34] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYV, &mipsinfo);
    psx->irq_regs[35] = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &mipsinfo);
    psx->irq_regs[36] = mipsinfo.i;

    // PC = timer handler routine
    mipsinfo.i = routine;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

    // parameter in a0
    mipsinfo.i = parameter;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);

    // RA = a trap address we can set
    mipsinfo.i = 0x80001000;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);

    // make sure we're set
    psx->psx_ram[0x1000 / 4] = LE32(FUNCT_HLECALL);

    psx->softcall_target = 0;
    oldICount = mips_get_icount(&psx->mipscpu);
    while (!psx->softcall_target) {
        mips_execute(&psx->mipscpu, 10);
    }
    mips_set_icount(&psx->mipscpu, oldICount);

    // restore IRQ regs
    for (j = 0; j < 32; j++) {
        mipsinfo.i = psx->irq_regs[j];
        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + j, &mipsinfo);
    }

    mipsinfo.i = psx->irq_regs[32];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
    mipsinfo.i = psx->irq_regs[33];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
    mipsinfo.i = psx->irq_regs[34];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
    mipsinfo.i = psx->irq_regs[35];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYV, &mipsinfo);
    mipsinfo.i = psx->irq_regs[36];
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &mipsinfo);

    psx->irq_mutex = 0;
}

void psx_bios_exception(PSX_STATE *psx, uint32 pc) {
    uint32 a0, status;
    union cpuinfo mipsinfo;
    int i, oldICount;

    psx->rescheduleNeeded = 0;

//	printf("bios_exception: cause %x\n", mips_get_cause() & 0x3c);

    // get a0
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);
    a0 = mipsinfo.i;

    switch (mips_get_cause(&psx->mipscpu) & 0x3c) {
        case 0:    // IRQ
//			printf("IRQ: %x, mask %x\n", irq_data, irq_mask);
            // save all regs
            for (i = 0; i < 32; i++) {
                mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + i, &mipsinfo);
                psx->irq_regs[i] = mipsinfo.i;
            }
            mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
            psx->irq_regs[32] = mipsinfo.i;
            mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
            psx->irq_regs[33] = mipsinfo.i;

            // check BIOS-driven interrupts
            if (psx->irq_data & 0x70)    // root counters
            {
                const int eventToCheck = 0xF2000002;
                int needClearInt = 0;

                for (i = 0; i < MAX_EVENT; i++) {
                    if (!psx->Event[i].isValid) continue;
                    if (psx->Event[i].classId != eventToCheck) continue;

                    needClearInt = 1;

                    if (!psx->Event[i].enabled) continue;

                    psx->Event[i].fired = 1;

                    if (!psx->Event[i].func) continue;

                    mipsinfo.i = LE32(psx->Event[i].func);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
                    mipsinfo.i = 0x80001000;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);

                    psx->psx_ram[0x1000 / 4] = LE32(FUNCT_HLECALL);

                    psx->softcall_target = 0;
                    oldICount = mips_get_icount(&psx->mipscpu);
                    while (!psx->softcall_target) {
                        mips_execute(&psx->mipscpu, 10);
                    }
                    mips_set_icount(&psx->mipscpu, oldICount);
                }

                if (needClearInt) {
                    psx->irq_data &= ~0x70;
                }
            }

            if (psx->psx_ram[LONGJMP_BUFFER / 4]) {
                psx_hw_write(psx, 0x1f801070, 0xffffffff, 0);

                a0 = LE32(psx->psx_ram[LONGJMP_BUFFER / 4]);

//				printf("taking entry_int\n");

                // RA (and PC)
                mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 0) / 4]);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
                // SP
                mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 4) / 4]);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
                // FP
                mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 8) / 4]);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);

                // S0-S7 are next
                for (i = 0; i < 8; i++) {
                    mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 12 + (i * 4)) / 4]);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R16 + i, &mipsinfo);
                }

                // GP
                mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 44) / 4]);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R28, &mipsinfo);

                // v0 = 1
                mipsinfo.i = 1;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            } else {
                psx_hw_write(psx, 0x1f801070, 0, 0xffff0000);

                // note: the entry_int won't be bailing us out here, so do it ourselves
                for (i = 0; i < 32; i++) {
                    mipsinfo.i = psx->irq_regs[i];
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + i, &mipsinfo);
                }

                mipsinfo.i = psx->irq_regs[32];
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
                mipsinfo.i = psx->irq_regs[33];
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
                mipsinfo.i = mips_get_ePC(&psx->mipscpu);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

                status = mips_get_status(&psx->mipscpu);
                status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
                mips_set_status(&psx->mipscpu, status);
            }
            break;

        case 0x20:    // syscall
            // syscall always farks with the status, so get it now
            status = mips_get_status(&psx->mipscpu);

            switch (a0) {
                case 1: // EnterCritical
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: EnterCritical\n");
#endif
                    status &= ~0x0404;
                    break;

                case 2:    // ExitCritical
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: ExitCritical\n");
#endif
                    status |= 0x0404;
                    break;

                default:
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: Unknown syscall %x\n", a0);
#endif
                    break;
            }

            // PC = ePC + 4
            mipsinfo.i = mips_get_ePC(&psx->mipscpu) + 4;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

            // and update the status accordingly
            status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
            mips_set_status(&psx->mipscpu, status);
            break;

        default:
#if DEBUG_HLE_BIOS
            printlog(psx, "HLEBIOS: Unknown exception %x\n", mips_get_cause(&psx->mipscpu));
#endif
            break;
    }

    if (psx->rescheduleNeeded) {
        ps2_reschedule(psx);
    }
}

uint32 psx_get_state_size(uint32 version) {
    return sizeof(PSX_STATE) + spu_get_state_size(version);
}

const char *psx_get_last_error(PSX_STATE *psx) {
    if (psx->error_buffer[0]) return psx->error_buffer;
    else return NULL;
}

void psx_register_console_callback(PSX_STATE *psx, psx_console_callback_t callback, void *context) {
    psx->console_callback = callback;
    psx->console_context = context;
}

void psx_hw_init(PSX_STATE *psx, uint32 version) {
    spucore_init();

    psx->mipscpu.psx = psx;

    psx->timerexp = 0;

    memset(psx->filestat, 0, sizeof(psx->filestat));
    memset(psx->filename, 0, sizeof(psx->filename));

    psx->dma4_cb = psx->dma7_cb = 0;

    psx->sys_time = 0;

    // clear registered libraries table
    memset(psx->reglibs, 0, sizeof(psx->reglibs));
    psx->iNumLibs = 0;

    memset(psx->evflags, 0, sizeof(psx->evflags));
    psx->iNumFlags = 0;

    memset(psx->threads, 0, sizeof(psx->threads));
    psx->iNumThreads = 1;    // we always have at least one thread

    memset(psx->semaphores, 0, sizeof(psx->semaphores));
    psx->iNumSema = 0;

    // set the initial thread to "RUNNING"
    psx->threads[0].iState = TS_RUNNING;
    psx->iCurThread = 0;

    memset(psx->iop_timers, 0, sizeof(psx->iop_timers));
    psx->iNumTimers = 0;

    // set PS1 BIOS HLE breakpoints
    psx->psx_ram[0xa0 / 4] = LE32(FUNCT_HLECALL);
    psx->psx_ram[0xb0 / 4] = LE32(FUNCT_HLECALL);
    psx->psx_ram[0xc0 / 4] = LE32(FUNCT_HLECALL);

    //Setup B0 table
    {
        uint32 *table = (uint32 *) (&psx->psx_ram[B0TABLE_BEGIN / 4]);
        table[0x5B] = LE32(C0_EXCEPTIONHANDLER_BEGIN);
    }

    //Setup C0 table
    {
        uint32 *table = (uint32 *) (&psx->psx_ram[C0TABLE_BEGIN / 4]);
        table[0x06] = LE32(C0_EXCEPTIONHANDLER_BEGIN);
    }

    psx->psx_ram[LONGJMP_BUFFER / 4] = 0;

    psx->eventsAllocated = 0;

    psx->Event = (EvtCtrlBlk *) &psx->psx_ram[EVENTS_BEGIN / 4];
    memset(psx->Event, 0, sizeof(EvtCtrlBlk) * MAX_EVENT);

    psx->dma_pcr = 0;
    psx->dma_icr = 0;
    psx->spu_delay = 0;
    psx->irq_data = 0;
    psx->irq_mask = 0;
    psx->softcall_target = 0;
    psx->gpu_stat = 0;
    psx->dma4_madr = psx->dma4_bcr = psx->dma4_chcr = 0;
    psx->dma7_madr = psx->dma7_bcr = psx->dma7_chcr = 0;
    psx->heap_addr = 0;

    psx->WAI = 0;

    psx->stop = 0;

    psx->rescheduleNeeded = 0;

    memset(&psx->scratch, 0, sizeof(psx->scratch));

    if (psx->psf_refresh != 50 && psx->psf_refresh != 60)
        psx->psf_refresh = 60;

    memset(&psx->root_cnts, 0, sizeof(psx->root_cnts));
    if (psx->psf_refresh == 50 || psx->psf_refresh == 60) {
        ioptimer_set_rates(&psx->root_cnts,
                           (version == 1) ? 33868800 : 36864000,
                           (version == 1) ? 429 : 858,
                           (psx->psf_refresh == 60) ? 262 : 312,
                           (psx->psf_refresh == 60) ? 224 : 240,
                           psx->psf_refresh
        );
    }

    psx->offset_to_spu = sizeof(PSX_STATE);
}

void psx_bios_hle(PSX_STATE *psx, uint32 pc) {
    uint32 subcall, status;
    union cpuinfo mipsinfo;
    uint32 a0, a1, a2, a3;
    int i;

    if ((pc == 0) || (pc == 0x80000000))        // IOP "null" state
    {
#if DEBUG_HLE_IOP
        printlog(psx, "IOP 'null' state\n");
#endif
//		ao_song_done = 1;
//        psx->stop = 1;
        // emergency, we may be stuck inside an interrupt handler
        psx->softcall_target = 1;
        mips_shorten_frame(&psx->mipscpu);
        return;
    }

    if (pc == 0xbfc00180 || pc == 0xbfc00184)    // exception, not BIOS call
    {
        psx_bios_exception(psx, pc);
        return;
    }

    if (pc == 0x80001000) {
//		printf("hit softcall target\n");
        psx->softcall_target = 1;
        return;
    }

    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R9, &mipsinfo);

    subcall = mipsinfo.i & 0xff;

    // most calls have a0/a1 as parameters, so prefetch them
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);
    a0 = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R5, &mipsinfo);
    a1 = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R6, &mipsinfo);
    a2 = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R7, &mipsinfo);
    a3 = mipsinfo.i;

//	mips_get_info(CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
//	printf("HLEBIOS: return is %08x\n", mipsinfo.i);

    switch (pc) {
        case 0xa0:    // a0 syscalls
            switch (subcall) {
                case 0x13:    // setjmp
                    // RA
                    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
                    psx->psx_ram[((a0 & 0x1fffff) + 0) / 4] = LE32(mipsinfo.i);
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: setjmp(%08x) => PC %08x\n", a0, mipsinfo.i);
#endif
                    // SP
                    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
                    psx->psx_ram[((a0 & 0x1fffff) + 4) / 4] = LE32(mipsinfo.i);
                    // FP
                    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);
                    psx->psx_ram[((a0 & 0x1fffff) + 8) / 4] = LE32(mipsinfo.i);

                    // S0-S7 are next
                    for (i = 0; i < 8; i++) {
                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R16 + i,
                                      &mipsinfo);
                        psx->psx_ram[((a0 & 0x1fffff) + 12 + (i * 4)) / 4] = LE32(mipsinfo.i);
                    }

                    // GP
                    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R28, &mipsinfo);
                    psx->psx_ram[((a0 & 0x1fffff) + 44) / 4] = LE32(mipsinfo.i);

                    // v0 = 0
                    mipsinfo.i = 0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;

                case 0x14: // longjmp
                    // RA
                    mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 0) / 4]);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: longjmp(%08x %d) => PC %08x\n", a0, a1, mipsinfo.i);
#endif
                    // SP
                    mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 4) / 4]);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
                    // FP
                    mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 8) / 4]);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);

                    // S0-S7 are next
                    for (i = 0; i < 8; i++) {
                        mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 12 + (i * 4)) / 4]);
                        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R16 + i,
                                      &mipsinfo);
                    }

                    // GP
                    mipsinfo.i = LE32(psx->psx_ram[((a0 & 0x1fffff) + 44) / 4]);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R28, &mipsinfo);

                    // v0 = a1
                    mipsinfo.i = a1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;

                case 0x18:    // strncmp
                {
                    uint8 *dst, *src;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: strncmp(%08x, %08x, %d)\n", a0, a1, a2);
#endif

                    dst = (uint8 *) psx->psx_ram;
                    src = (uint8 *) psx->psx_ram;
                    dst += (a0 & 0x1fffff);
                    src += (a1 & 0x1fffff);

                    // v0 = result
                    mipsinfo.i = strncmp((char *) dst, (char *) src, a2);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x19:    // strcpy
                {
                    uint8 *dst, *src;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: strcpy(%08x, %08x)\n", a0, a1);
#endif

                    dst = (uint8 *) psx->psx_ram;
                    src = (uint8 *) psx->psx_ram;
                    dst += (a0 & 0x1fffff);
                    src += (a1 & 0x1fffff);

                    while (*src) {
                        *dst = *src;
                        dst++;
                        src++;
                    }

                    // v0 = a0
                    mipsinfo.i = a0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x28:    // bzero
                {
                    uint8 *dst;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: bzero(%08x, %08x)\n", a0, a1);
#endif

                    dst = (uint8 *) psx->psx_ram;
                    dst += (a0 & 0x1fffff);
                    memset(dst, 0, a1);
                }
                    break;

                case 0x2a:    // memcpy
                {
                    uint8 *dst, *src;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: memcpy(%08x, %08x, %08x)\n", a0, a1, a2);
#endif

                    dst = (uint8 *) psx->psx_ram;
                    src = (uint8 *) psx->psx_ram;
                    dst += (a0 & 0x1fffff);
                    src += (a1 & 0x1fffff);

                    while (a2) {
                        *dst = *src;
                        dst++;
                        src++;
                        a2--;
                    }

                    // v0 = a0
                    mipsinfo.i = a0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x2b:    // memset
                {
                    uint8 *dst;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: memset(%08x, %08x, %08x)\n", a0, a1, a2);
#endif

                    dst = (uint8 *) psx->psx_ram;
                    dst += (a0 & 0x1fffff);

                    while (a2) {
                        *dst = a1;
                        dst++;
                        a2--;
                    }

                    // v0 = a0
                    mipsinfo.i = a0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x2f:    // rand
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: rand\n");
#endif

                    // v0 = result
                    mipsinfo.i = 1 + (int) (32767.0 * rand() / (RAND_MAX + 1.0));
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;

                case 0x30:    // srand
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: srand(%x)\n", a0);
#endif
                    srand(a0);
                    break;

                case 0x33:    // malloc
                {
                    uint32 chunk, fd, size;

                    size = a0;
                    if (size & 15) {
                        size &= ~15;
                        size += 16;
                    }

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: malloc(%x) -- rounding up to %x\n", a0, size);
#endif

                    chunk = psx->heap_addr;

                    // find a free block that's big enough
                    while ((size > LE32(psx->psx_ram[(chunk + BLK_SIZE) / 4])) ||
                           (LE32(psx->psx_ram[(chunk + BLK_STAT) / 4]) == 1)) {
                        chunk = LE32(psx->psx_ram[(chunk + BLK_FD)]);
                    }

                    // split free block
                    fd = chunk + 16 +
                         size;    // free block starts after block record and allocation size
                    psx->psx_ram[(fd + BLK_STAT) / 4] = psx->psx_ram[(chunk + BLK_STAT) / 4];
                    psx->psx_ram[(fd + BLK_SIZE) / 4] = LE32(
                            LE32(psx->psx_ram[(chunk + BLK_SIZE) / 4]) - size - 16);
                    psx->psx_ram[(fd + BLK_FD) / 4] = psx->psx_ram[(chunk + BLK_FD) / 4];
                    psx->psx_ram[(fd + BLK_BK) / 4] = chunk;

                    psx->psx_ram[(chunk + BLK_STAT) / 4] = LE32(1);
                    psx->psx_ram[(chunk + BLK_SIZE) / 4] = LE32(size);
                    psx->psx_ram[(chunk + BLK_FD) / 4] = LE32(fd);

                    mipsinfo.i = chunk + 16;
                    mipsinfo.i |= 0x80000000;
#if DEBUG_HLE_BIOS
                    printlog(psx, "== %08x\n", mipsinfo.i);
#endif
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x34:  // free
                {
                    uint32 chunk, size, fd, lastfd;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: free(%08x)\n", a0);
#endif

                    chunk = (a0 & 0x1fffff) - 16;

                    if ((LE32(psx->psx_ram[(chunk + BLK_STAT) / 4])) != 1) {
                        // freeing already free block
                        mipsinfo.i = 0xffffffff;
                        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                        break;
                    }


                    size = LE32(psx->psx_ram[(chunk + BLK_SIZE) / 4]);
                    fd = LE32(psx->psx_ram[(chunk + BLK_FD) / 4]);
                    lastfd = 0;

                    // Search for and connect contiguous blocks
                    while (fd && LE32(psx->psx_ram[(fd + BLK_STAT) / 4]) != 1) {
                        size += LE32(psx->psx_ram[(fd + BLK_SIZE) / 4]) + 16; // include header
                        lastfd = fd;
                        fd = LE32(psx->psx_ram[(fd + BLK_FD)]);
                    }

                    psx->psx_ram[(chunk + BLK_SIZE) / 4] = LE32(size);
                    psx->psx_ram[(chunk + BLK_FD) / 4] = LE32(lastfd);
                    psx->psx_ram[(chunk + BLK_STAT) / 4] = LE32(0);

                    if (lastfd)
                        psx->psx_ram[(lastfd + BLK_BK) / 4] = chunk;

                    mipsinfo.i = 0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x39:    // InitHeap
                    // heap address in A0, length in A1
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: InitHeap(%08x, %08x)\n", a0, a1);
#endif

                    // align block, subtracting overflow from requested size
                    if (a0 & 15) {
                        a1 -= 16 - (a0 & 15);
                        a0 &= ~15;
                        a0 += 16;
                    }

                    // subtract initial block header
                    if (a1 >= 16)
                        a1 -= 16;

                    // align size down
                    if (a1 & 15) {
                        a1 &= ~15;
                    }

                    psx->heap_addr = a0 & 0x3fffffff;

                    psx->psx_ram[(psx->heap_addr + BLK_STAT) / 4] = LE32(0);
                    psx->psx_ram[(psx->heap_addr + BLK_FD) / 4] = LE32(0);
                    psx->psx_ram[(psx->heap_addr + BLK_BK) / 4] = LE32(0);

                    // if heap size out of range, clamp it
                    if (((a0 & 0x1fffff) + a1) >= 2 * 1024 * 1024) {
                        psx->psx_ram[(psx->heap_addr + BLK_SIZE) / 4] = LE32(
                                0x1ffffc - (a0 & 0x1fffff));
                    } else {
                        psx->psx_ram[(psx->heap_addr + BLK_SIZE) / 4] = LE32(a1);
                    }
                    break;

                case 0x3f:    // printf
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: printf(%08x) = %s\n", a0, &psx->psx_ram[(a0&0x1fffff)/4]);
#endif
                    break;

                case 0x44: // FlushCache
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: FlushCache\n");
#endif
                    break;

                case 0x70: // bu_init
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: bu_init\n");
#endif
                    break;

                case 0x72:    //__96_remove
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: __96_remove\n");
#endif
                    break;

                default:
#if DEBUG_HLE_BIOS
                    printlog(psx, "Unknown BIOS A0 call = %x\n", subcall);
#endif
                    break;
            }
            break;

        case 0xb0:    // b0 syscalls
            switch (subcall) {
                case 0x07:    // DeliverEvent
                {
                    uint32 classId = a0;
                    uint32 spec = a1;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: DeliverEvent(%08x %08x)\n", classId, spec);
#endif

                    if (psx->eventsAllocated) {
                        for (i = 0; i < MAX_EVENT; i++) {
                            if (!psx->Event[i].isValid) continue;
                            if (psx->Event[i].classId != classId) continue;
                            if (!psx->Event[i].enabled) continue;

                            psx->Event[i].fired = 1;

                            if (!psx->Event[i].func) continue;

                            call_irq_routine(psx, psx->Event[i].func, 0);
                        }
                    }
                }
                    break;

                case 0x08:    // OpenEvent
                {
                    uint32 eventId = -1;
                    uint32 classId = a0;
                    uint32 spec = a1;
                    uint32 mode = a2;
                    uint32 func = a3;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: OpenEvent(%08x, %08x, %08x, %08x)\n", a0, a1, a2, a3);
#endif

                    if (psx->eventsAllocated == MAX_EVENT) {
#if DEBUG_HLE_BIOS
                        printlog(psx, "HLEBIOS: Too many events allocated!\n");
#endif
                        return;
                    }

                    psx->eventsAllocated++;

                    for (i = 0; i < MAX_EVENT; i++) {
                        if (psx->Event[i].isValid == 0) {
                            eventId = i;
                            break;
                        }
                    }

                    psx->Event[eventId].isValid = 1;
                    psx->Event[eventId].classId = classId;
                    psx->Event[eventId].spec = spec;
                    psx->Event[eventId].mode = mode;
                    psx->Event[eventId].func = func;
                    psx->Event[eventId].fired = 0;

                    // v0 = ev | spec<<8;
                    mipsinfo.i = eventId + 1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x0a:    // WaitEvent
                {
                    uint32 eventId = a0 - 1;

#if DEBUG_HLE_BIOS
                                                                                                                                            mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
						printlog(psx, "HLEBIOS: WaitEvent(%d) PC=%x\n", eventId, mipsinfo.i);
#endif

                    if (psx->Event[eventId].isValid) {
                        if (psx->Event[eventId].enabled) {
                            if (!psx->Event[eventId].fired) {
                                psx->WAI = 1;
                                mips_shorten_frame(&psx->mipscpu);
                            }
                        }
                    }

                    // v0 = 1
                    mipsinfo.i = 1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x0b:    // TestEvent
                {
                    uint32 eventId = a0 - 1;
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: TestEvent(%d)\n", eventId);
#endif

                    if (psx->Event[eventId].isValid) {
                        mipsinfo.i = psx->Event[eventId].fired;
                    } else {
                        mipsinfo.i = 0;
                    }

                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);

                    // it looks like this sets v1 to something non-zero too
                    // (code in Crash 2 & 3 actually relies on that behavior)
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R3, &mipsinfo);
                }
                    break;

                case 0x0c:    // EnableEvent
                {
                    uint32 eventId = a0 - 1;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: EnableEvent(%d)\n", eventId);
#endif

                    if (psx->Event[eventId].isValid) {
                        psx->Event[eventId].enabled = 1;
                        psx->Event[eventId].fired = 0;
                    }

                    // v0 = 1
                    mipsinfo.i = 1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x0d:    // DisableEvent
                {
                    uint32 eventId = a0 - 1;

#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: DisableEvent(%d)\n", eventId);
#endif

                    if (psx->Event[eventId].isValid) {
                        psx->Event[eventId].enabled = 0;
                    }

                    // v0 = 1
                    mipsinfo.i = 1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                }
                    break;

                case 0x17:    // ReturnFromException
                    for (i = 0; i < 32; i++) {
                        mipsinfo.i = psx->irq_regs[i];
                        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R0 + i, &mipsinfo);
                    }

                    mipsinfo.i = psx->irq_regs[32];
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_HI, &mipsinfo);
                    mipsinfo.i = psx->irq_regs[33];
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_LO, &mipsinfo);
                    mipsinfo.i = mips_get_ePC(&psx->mipscpu);
//					printlog(psx, "ReturnFromException: IRQ state %x\n", irq_data & irq_mask);
//					printlog(psx, "HLEBIOS: ReturnFromException, cause = %08x, PC = %08x\n", mips_get_cause(), mipsinfo.i);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

                    status = mips_get_status(&psx->mipscpu);
                    status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
                    mips_set_status(&psx->mipscpu, status);

                    return;    // force return to avoid PC=RA below
                    break;

                case 0x19:    // HookEntryInt
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: HookEntryInt(%08x)\n", a0);
#endif
                    psx->psx_ram[LONGJMP_BUFFER / 4] = LE32(a0);
                    break;

                case 0x3f:    // puts
//					printlog(psx, "HLEBIOS: puts\n");
                    break;

                case 0x5b:    // ChangeClearPAD
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: ChangeClearPAD\n");
#endif
                    break;

                case 0x4a: // InitCard
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: InitCard\n");
#endif
                    break;

                case 0x4b: // StartCard
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: StartCard\n");
#endif
                    break;

                case 0x56: // GetC0Table
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: GetC0Table\n");
#endif
                    mipsinfo.i = C0TABLE_BEGIN;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;

                case 0x57: // GetB0Table
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: GetB0Table\n");
#endif
                    mipsinfo.i = B0TABLE_BEGIN;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;

                default:
#if DEBUG_HLE_BIOS
                    printlog(psx, "Unknown BIOS B0 call = %x\n", subcall);
#endif
                    break;
            }
            break;

        case 0xc0:    // c0 syscalls
            switch (subcall) {
                case 0xa:    // ChangeClearRCnt
#if DEBUG_HLE_BIOS
                    printlog(psx, "HLEBIOS: ChangeClearRCnt(%08x, %08x)\n", a0, a1);
#endif

                    // v0 = (a0*4)+0x8600
                    mipsinfo.i = LE32(psx->psx_ram[((a0 << 2) + 0x8600) / 4]);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);

                    // (a0*4)+0x8600 = a1;
                    psx->psx_ram[((a0 << 2) + 0x8600) / 4] = LE32(a1);
                    break;

                default:
#if DEBUG_HLE_BIOS
                    printlog(psx, "Unknown BIOS C0 call = %x\n", subcall);
#endif
                    break;
            }
            break;
    }

    // PC = RA
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
}

// root counters

void psx_hw_runcounters(PSX_STATE *psx) {
    int i, j, intr;
    union cpuinfo mipsinfo;

    // don't process any IRQ sources when interrupts are suspended
    if (!psx->intr_susp) {
        if (psx->dma4_delay) {
            psx->dma4_delay--;

            if (psx->dma4_delay == 0) {
                spu_interrupt_dma4(SPUSTATE);

                if (psx->dma4_cb) {
                    call_irq_routine(psx, psx->dma4_cb, psx->dma4_flag);
                }
            }
        }

        if (psx->dma7_delay) {
            psx->dma7_delay--;

            if (psx->dma7_delay == 0) {
                spu_interrupt_dma7(SPUSTATE);

                if (psx->dma7_cb) {
                    call_irq_routine(psx, psx->dma7_cb, psx->dma7_flag);
                }
            }
        }

        for (i = 0; i < psx->iNumThreads; i++) {
            if (psx->threads[i].iState == TS_WAITDELAY) {
                if (psx->threads[i].waitparm > CLOCK_DIV) {
                    psx->threads[i].waitparm -= CLOCK_DIV;
                } else    // time's up
                {
                    psx->threads[i].waitparm = 0;
                    psx->threads[i].iState = TS_RUNNING;

                    psx->timerexp = 1;

                    psx->rescheduleNeeded = 1;
                }
            }
        }

        psx->sys_time += 768;

        if (psx->iNumTimers > 0) {
            for (i = 0; i < psx->iNumTimers; i++) {
                if (psx->iop_timers[i].iActive > 0) {
                    psx->iop_timers[i].count += 768;
                    if (psx->iop_timers[i].count >= psx->iop_timers[i].target) {
                        psx->iop_timers[i].count -= psx->iop_timers[i].target;

                        //					printlog(psx, "Timer %d: handler = %08x, param = %08x\n", i, iop_timers[i].handler, iop_timers[i].hparam);
                        call_irq_routine(psx, psx->iop_timers[i].handler,
                                         psx->iop_timers[i].hparam);

                        psx->timerexp = 1;
                    }
                }
            }
        }
    }

// PS1 root counters
    intr = ioptimer_advance(&psx->root_cnts, 768);
    if (intr && !psx->intr_susp)
        psx_irq_set(psx, intr);
}

// PEOpS callbacks

// PSXCPU callbacks

uint8 program_read_byte_32le(void *state, offs_t address) {
    PSX_STATE *psx = (PSX_STATE *) state;
    switch (address & 0x3) {
        default:
        case 0:
            return psx_hw_read(psx, address, 0xffffff00);
            break;
        case 1:
            return psx_hw_read(psx, address, 0xffff00ff) >> 8;
            break;
        case 2:
            return psx_hw_read(psx, address, 0xff00ffff) >> 16;
            break;
        case 3:
            return psx_hw_read(psx, address, 0x00ffffff) >> 24;
            break;
    }
}

uint16 program_read_word_32le(void *state, offs_t address) {
    PSX_STATE *psx = (PSX_STATE *) state;
    if (address & 2)
        return psx_hw_read(psx, address, 0x0000ffff) >> 16;

    return psx_hw_read(psx, address, 0xffff0000);
}

uint32 program_read_dword_32le(void *state, offs_t address) {
    return psx_hw_read((PSX_STATE *) state, address, 0);
}

void program_write_byte_32le(void *state, offs_t address, uint8 data) {
    PSX_STATE *psx = (PSX_STATE *) state;
    switch (address & 0x3) {
        case 0:
            psx_hw_write(psx, address, data, 0xffffff00);
            break;
        case 1:
            psx_hw_write(psx, address, data << 8, 0xffff00ff);
            break;
        case 2:
            psx_hw_write(psx, address, data << 16, 0xff00ffff);
            break;
        case 3:
            psx_hw_write(psx, address, data << 24, 0x00ffffff);
            break;
    }
}

void program_write_word_32le(void *state, offs_t address, uint16 data) {
    PSX_STATE *psx = (PSX_STATE *) state;
    if (address & 2) {
        psx_hw_write(psx, address, data << 16, 0x0000ffff);
        return;
    }

    psx_hw_write(psx, address, data, 0xffff0000);
}

void program_write_dword_32le(void *state, offs_t address, uint32 data) {
    psx_hw_write((PSX_STATE *) state, address, data, 0);
}

static uint32 ccallArgumentIterator_GetNext(PSX_STATE *psx, uint32 current) {
    union cpuinfo mipsinfo;
    if (current > 3) {
        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo); // SP
        mipsinfo.i += (current - 4) * 4 + 0x10;
        return psx_hw_read(psx, mipsinfo.i, 0);
    } else {
        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4 + current,
                      &mipsinfo); // A0 - A3
        return mipsinfo.i;
    }
}

// sprintf replacement
static void iop_sprintf(PSX_STATE *psx, char *out, char *fmt, uint32 pstart) {
    char temp[64], tfmt[64];
    char *cf, *pstr;
    int curparm, fp, isnum;
    uint32 value;

    curparm = pstart;
    cf = fmt;

    while (*cf != '\0') {
        if (*cf != '%') {
            if (*cf == 27) {
                *out++ = '[';
                *out++ = 'E';
                *out++ = 'S';
                *out++ = 'C';
                *out = ']';
            } else {
                *out = *cf;
            }
            out++;
            cf++;
        } else    // got format
        {
            cf++;

            tfmt[0] = '%';
            fp = 1;
            while (((*cf >= '0') && (*cf <= '9')) || (*cf == '.') || (*cf == 'l')) {
                tfmt[fp] = *cf;
                fp++;
                cf++;
            }

            tfmt[fp] = *cf;
            tfmt[fp + 1] = '\0';

            isnum = 0;
            switch (*cf) {
                case 'x':
                case 'X':
                case 'd':
                case 'D':
                case 'c':
                case 'C':
                case 'u':
                case 'U':
                    isnum = 1;
                    break;
            }

//			printf("]]] temp format: [%s] [%d]\n", tfmt, isnum);

            if (isnum) {
                value = ccallArgumentIterator_GetNext(psx, curparm);
//				printf("parameter %d = %x\n", curparm-pstart, mipsinfo.i);
                curparm++;
                sprintf(temp, tfmt, (int32) value);
            } else {
                value = ccallArgumentIterator_GetNext(psx, curparm);
                curparm++;

                pstr = (char *) psx->psx_ram;
                pstr += (value & 0x1fffff);

                sprintf(temp, tfmt, pstr);
            }

            pstr = &temp[0];
            while (*pstr != '\0') {
                *out = *pstr;
                out++;
                pstr++;
            }

            cf++;
        }
    }

    *out = '\0';
}

int ProcessEventFlag(uint32 mode, uint32 *value, uint32 mask, uint32 *resultPtr) {
    int success = 0;
    uint32 maskResult = *value & mask;

    if (mode & WEF_OR) {
        success = (maskResult != 0);
    } else {
        success = (maskResult == mask);
    }

    if (success) {
        if (resultPtr) {
            *resultPtr = *value;
        }

        if (mode & WEF_CLEAR) {
            *value = 0;
        }
    }

    return success;
}

// PS2 IOP callbacks
void psx_iop_call(PSX_STATE *psx, uint32 pc, uint32 callnum) {
    uint32 scan;
    char *mname, *str1, *str2, *str3, name[9], out[512];
    uint32 a0, a1, a2, a3;
    union cpuinfo mipsinfo;
    int i;

//	printf("IOP call @ %08x\n", pc);

    // prefetch parameters
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);
    a0 = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R5, &mipsinfo);
    a1 = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R6, &mipsinfo);
    a2 = mipsinfo.i;
    mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R7, &mipsinfo);
    a3 = mipsinfo.i;

    scan = (pc & 0x0fffffff) / 4;
    while ((psx->psx_ram[scan] != LE32(0x41e00000)) && (scan >= (0x10000 / 4))) {
        scan--;
    }

    if (psx->psx_ram[scan] != LE32(0x41e00000)) {
        psx->error_ptr += sprintf(psx->error_ptr,
                                  "FATAL ERROR: couldn't find IOP link signature\n");
        return;
    }

    scan += 3;    // skip zero and version
    memcpy(name, &psx->psx_ram[scan], 8);
    name[8] = '\0';

#if 0
                                                                                                                            if (psx->console_callback)
	{
		sprintf(out, "IOP: call module [%s] service %d (PC=%08x)\n", name, callnum, pc);
		psx->console_callback(psx->console_context, out);
	}
#endif

    if (!strcmp(name, "stdio")) {
        switch (callnum) {
            case 4:    // printf
                mname = (char *) psx->psx_ram;
                mname += a0 & 0x1fffff;
                mname += (a0 & 3);

                iop_sprintf(psx, out, mname, 1);    // a1 is first parm

                /*	if (out[strlen(out)-1] != '\n')
				{
					strcat(out, "\n");
				}*/
                if (psx->console_callback) {
                    psx->console_callback(psx->console_context, out);
                }

                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "sifman")) {
        switch (callnum) {
            case 5:    // sceSifInit
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: sceSifInit()\n");
#endif

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 7: // sceSifSetDma
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: sceSifSetDma(%08x %08x)\n", a0, a1);
#endif

                mipsinfo.i = a1;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 8:    // sceSifDmaStat
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: sceSifDmaStat(%08x)\n", a0);
#endif

                mipsinfo.i = -1;    // dma completed
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 29: // sceSifCheckInit
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: sceSifCheckInit()\n");
#endif

                mipsinfo.i = 1;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "thbase")) {
        uint32 newAlloc;

        switch (callnum) {
            case 4:    // CreateThread
#if DEBUG_THREADING
                printlog(psx, "IOP: CreateThread(%08x)\n", a0);
#endif
                a0 &= 0x1fffff;
                a0 /= 4;
#if DEBUG_THREADING
                                                                                                                                        printlog(psx, "   : flags %x routine %08x pri %x stacksize %d refCon %08x\n",
					psx->psx_ram[a0], psx->psx_ram[a0+1], psx->psx_ram[a0+2], psx->psx_ram[a0+3], psx->psx_ram[a0+4]);
#endif
                if (psx->iNumThreads == 32) {
                    psx->stop = 1;
                }

                psx->threads[psx->iNumThreads].iState = TS_DORMANT;
                psx->threads[psx->iNumThreads].flags = LE32(psx->psx_ram[a0]);
                psx->threads[psx->iNumThreads].routine = LE32(psx->psx_ram[a0 + 2]);
                psx->threads[psx->iNumThreads].stacksize = LE32(psx->psx_ram[a0 + 3]);
                psx->threads[psx->iNumThreads].refCon = LE32(psx->psx_ram[a0 + 4]);
                psx->threads[psx->iNumThreads].wakeupcount = 0;

                if (psx->threads[psx->iNumThreads].stacksize == 0) {
                    psx->threads[psx->iNumThreads].stacksize = 0x4000;
                }

                psx->threads[psx->iNumThreads].stacksize =
                        (psx->threads[psx->iNumThreads].stacksize + 3) & ~0x3;
                mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R28, &mipsinfo);
                psx->threads[psx->iNumThreads].save_regs[28] = mipsinfo.i;

                newAlloc = psf2_get_loadaddr(psx);
                // force 16-byte alignment
                if (newAlloc & 0xf) {
                    newAlloc &= ~0xf;
                    newAlloc += 16;
                }
                psf2_set_loadaddr(psx, newAlloc + psx->threads[psx->iNumThreads].stacksize);

                psx->threads[psx->iNumThreads].stackloc = newAlloc;

                memset(&psx->psx_ram[newAlloc / 4], 0, psx->threads[psx->iNumThreads].stacksize);

                psx->threads[psx->iNumThreads].save_regs[29] =
                        (psx->threads[psx->iNumThreads].stackloc +
                         psx->threads[psx->iNumThreads].stacksize - 0x10) | 0x80000000;
                psx->threads[psx->iNumThreads].save_regs[35] = psx->threads[psx->iNumThreads].save_regs[36] = 0;

                mipsinfo.i = psx->iNumThreads;
                psx->iNumThreads++;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 6:    // StartThread
#if DEBUG_THREADING
                printlog(psx, "IOP: StartThread(%d %d)\n", a0, a1);
#endif

                if (psx->threads[a0].iState != TS_DORMANT) {
#if DEBUG_THREADING
                    printlog(psx, "IOP: Thread not ready!\n");
#endif
                    return;
                }

                psx->threads[a0].iState = TS_RUNNING;
                psx->threads[a0].save_regs[4] = a1;
                psx->threads[a0].save_regs[34] = psx->threads[a0].routine;
                psx->threads[a0].save_regs[29] =
                        psx->threads[a0].stackloc + psx->threads[a0].stacksize - 0x10;
                psx->rescheduleNeeded = 1;

                break;

            case 7: // StartThreadArgs
#if DEBUG_THREADING
                printlog(psx, "IOP: StartThreadArgs(%d %d %08X", a0, a1, a2);
#endif

                if (psx->threads[a0].iState != TS_DORMANT) {
#if DEBUG_THREADING
                    printlog(psx, "IOP: Thread not ready!\n");
#endif
                    return;
                }

                psx->threads[a0].iState = TS_RUNNING;
                psx->threads[a0].save_regs[4] = a1;

                {
                    uint32 stackAddress = psx->threads[a0].stackloc + psx->threads[a0].stacksize;
                    uint32 fixedSize = ((a1 + 0x3) & ~0x3);
                    uint32 copyAddress = stackAddress - a1;
                    stackAddress -= fixedSize;
                    memcpy(((uint8 *) psx->psx_ram) + copyAddress, ((uint8 *) psx->psx_ram) + a2,
                           a1);
                    psx->threads[a0].save_regs[29] = stackAddress - 0x10;
                    psx->threads[a0].save_regs[5] = copyAddress;
                }

                psx->threads[a0].save_regs[34] = psx->threads[a0].routine;

                psx->rescheduleNeeded = 1;

                break;

            case 20:// GetThreadID
#if DEBUG_THREADING
                printlog(psx, "IOP: GetThreadId()\n");
#endif

                mipsinfo.i = psx->iCurThread;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 24:// SleepThread
#if DEBUG_THREADING
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: SleepThread() [curThread %d, PC=%x]\n", psx->iCurThread, mipsinfo.i);
#endif

                if (psx->threads[psx->iCurThread].iState != TS_RUNNING) {
#if DEBUG_THREADING
                    printlog(psx, "IOP: Thread not running!\n");
#endif
                    psx->stop = 1;
                    return;
                }

                if (psx->threads[psx->iCurThread].wakeupcount == 0) {
                    psx->threads[psx->iCurThread].iState = TS_SLEEPING;
                    psx->rescheduleNeeded = 1;
                } else {
                    psx->threads[psx->iCurThread].wakeupcount--;
                }
                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 25:// WakeupThread
#if DEBUG_THREADING
                printlog(psx, "IOP: WakeupThread(%d)\n", a0);
#endif

                // set thread to "ready to go"
                if (psx->threads[a0].iState == TS_SLEEPING) {
                    psx->threads[a0].iState = TS_RUNNING;
                    psx->rescheduleNeeded = 1;
                } else {
                    psx->threads[a0].wakeupcount++;
                }
                mipsinfo.i = psx->threads[a0].wakeupcount;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 26:// iWakeupThread
#if DEBUG_THREADING
                printlog(psx, "IOP: iWakeupThread(%d)\n", a0);
#endif

                // set thread to "ready to go" if it's not running
                if (psx->threads[a0].iState == TS_SLEEPING) {
                    psx->threads[a0].iState = TS_RUNNING;
                    psx->rescheduleNeeded = 1;
                } else {
                    psx->threads[a0].wakeupcount++;
                }
                mipsinfo.i = psx->threads[a0].wakeupcount;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 33:// DelayThread
            {
                double dTicks;
                int i;

#if DEBUG_THREADING
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
					printlog(psx, "IOP: DelayThread(%d) (PC=%x) [curthread = %d]\n", a0, mipsinfo.i, psx->iCurThread);
#endif

                if (a0 < 100) {
                    a0 = 100;
                }
                dTicks = (double) a0;

                psx->threads[psx->iCurThread].iState = TS_WAITDELAY;
                dTicks /= (double) 1000000.0;
                dTicks *= (double) 36864000.0;    // 768*48000 = IOP native-mode clock rate
                psx->threads[psx->iCurThread].waitparm = (uint32) dTicks;
                psx->rescheduleNeeded = 1;
            }
                break;

            case 34://GetSystemTime
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: GetSystemTime(%x)\n", a0);
#endif

                a0 &= 0x1fffff;
                a0 /= 4;

                psx->psx_ram[a0] = LE32(psx->sys_time & 0xffffffff);    // low
                psx->psx_ram[a0 + 1] = LE32(psx->sys_time >> 32);    // high

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 39:// USec2SysClock
            {
                uint64 dTicks = (uint64) a0;
                uint32 hi, lo;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: USec2SysClock(%d %08x)\n", a0, a1);
#endif

                dTicks *= (uint64) 36864000;
                dTicks /= (uint64) 1000000;

                hi = dTicks >> 32;
                lo = dTicks & 0xffffffff;

                psx->psx_ram[((a1 & 0x1fffff) / 4)] = LE32(lo);
                psx->psx_ram[((a1 & 0x1fffff) / 4) + 1] = LE32(hi);

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;

            case 40://SysClock2USec
            {
                uint64 temp;
                uint32 seconds, usec;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: SysClock2USec(%08x %08x %08x)\n", a0, a1, a2);
#endif

                a0 &= 0x1fffff;
                a1 &= 0x1fffff;
                a2 &= 0x1fffff;
                a0 /= 4;
                a1 /= 4;
                a2 /= 4;

                temp = LE32(psx->psx_ram[a0]);
                temp |= (uint64) LE32(psx->psx_ram[a0 + 1]) << 32;

                temp *= (uint64) 1000000;
                temp /= (uint64) 36864000;

                // temp now is USec
                seconds = (temp / 1000000) & 0xffffffff;
                usec = (temp % 1000000) & 0xffffffff;

                psx->psx_ram[a1] = LE32(seconds);
                psx->psx_ram[a2] = LE32(usec);
            }
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "thevent")) {
        switch (callnum) {
            case 4:    // CreateEventFlag
                mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: CreateEventFlag(%08x) (PC=%x)\n", a0, (uint32)mipsinfo.i);
#endif

                a0 &= 0x1fffff;
                a0 /= 4;

                psx->evflags[psx->iNumFlags].type = LE32(psx->psx_ram[a0]);
                psx->evflags[psx->iNumFlags].value = LE32(psx->psx_ram[a0 + 1]);
                psx->evflags[psx->iNumFlags].param = LE32(psx->psx_ram[a0 + 2]);
                psx->evflags[psx->iNumFlags].inUse = 1;

#if DEBUG_HLE_IOP
                printlog(psx, "     Flag %02d: type %d init %08x param %08x\n", psx->iNumFlags, psx->evflags[psx->iNumFlags].type, psx->evflags[psx->iNumFlags].value, psx->evflags[psx->iNumFlags].param);
#endif

                mipsinfo.i = psx->iNumFlags + 1;
                psx->iNumFlags++;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 6: // SetEventFlag
                a0--;
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: SetEventFlag(%d %08x)\n", a0, a1);
#endif

                psx->evflags[a0].value |= a1;

                for (i = 0; i < psx->iNumThreads; i++) {
                    if (psx->threads[i].iState != TS_WAITEVFLAG) continue;
                    if (psx->threads[i].waitparm == a0) {
                        int success = ProcessEventFlag(psx->threads[i].waiteventmode,
                                                       &psx->evflags[a0].value,
                                                       psx->threads[i].waiteventmask,
                                                       (psx->threads[i].waiteventresultptr != 0)
                                                       ? &psx->psx_ram[
                                                               psx->threads[i].waiteventresultptr /
                                                               4] : 0);
                        if (success) {
                            psx->threads[i].waitparm = 0;
                            psx->threads[i].waiteventresultptr = 0;

                            psx->threads[i].iState = TS_RUNNING;

                            psx->rescheduleNeeded = 1;
                        }
                    }
                }

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 7: // iSetEventFlag
                a0--;
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: iSetEventFlag(%08x %08x)\n", a0, a1);
#endif

                psx->evflags[a0].value |= a1;

                for (i = 0; i < psx->iNumThreads; i++) {
                    if (psx->threads[i].iState != TS_WAITEVFLAG) continue;
                    if (psx->threads[i].waitparm == a0) {
                        int success = ProcessEventFlag(psx->threads[i].waiteventmode,
                                                       &psx->evflags[a0].value,
                                                       psx->threads[i].waiteventmask,
                                                       (psx->threads[i].waiteventresultptr != 0)
                                                       ? &psx->psx_ram[
                                                               psx->threads[i].waiteventresultptr /
                                                               4] : 0);
                        if (success) {
                            psx->threads[i].waitparm = 0;
                            psx->threads[i].waiteventresultptr = 0;

                            psx->threads[i].iState = TS_RUNNING;

                            psx->rescheduleNeeded = 1;
                        }
                    }
                }

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 8:    // ClearEventFlag
                a0--;
                mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: ClearEventFlag(%d %08x) (PC=%x)\n", a0, a1, (uint32)mipsinfo.i);
#endif

                psx->evflags[a0].value &= a1;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 9: // iClearEventFlag
                a0--;
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: iClearEventFlag(%d %08x)\n", a0, a1);
#endif

                psx->evflags[a0].value &= a1;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 10:// WaitEventFlag
                a0--;
#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: WaitEventFlag(%d %08x %d %08x PC=%x)\n", a0, a1, a2, a3, (uint32)mipsinfo.i);
#endif

                a3 &= 0x1fffff;

                i = ProcessEventFlag(a2, &psx->evflags[a0].value, a1,
                                     (a3 != 0) ? &psx->psx_ram[a3 / 4] : 0);

                if (!i) {
                    psx->threads[psx->iCurThread].iState = TS_WAITEVFLAG;
                    psx->threads[psx->iCurThread].waitparm = a0;
                    psx->threads[psx->iCurThread].waiteventmode = a2;
                    psx->threads[psx->iCurThread].waiteventmask = a1;
                    psx->threads[psx->iCurThread].waiteventresultptr = a3;

                    psx->rescheduleNeeded = 1;
                }

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "thsemap")) {
        switch (callnum) {
            case 4:    // CreateSema
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: CreateSema(%08x)\n", a0);
#endif

                mipsinfo.i = -1;
                for (i = 0; i < SEMA_MAX; i++) {
                    if (!psx->semaphores[i].inuse) {
                        mipsinfo.i = i;
                        break;
                    }
                }

                if (mipsinfo.i == -1) {
                    psx->error_ptr += sprintf(psx->error_ptr, "IOP: out of semaphores!\n");
                }

                a0 &= 0x7fffffff;
                a0 /= 4;

//				printf("Sema %d Parms: %08x %08x %08x %08x\n", mipsinfo.i, psx_ram[a0], psx_ram[a0+1], psx_ram[a0+2], psx_ram[a0+3]);

                if (mipsinfo.i != -1) {
                    psx->semaphores[mipsinfo.i].attr = LE32(psx->psx_ram[a0]);
                    psx->semaphores[mipsinfo.i].option = LE32(psx->psx_ram[a0 + 1]);
                    psx->semaphores[mipsinfo.i].init = LE32(psx->psx_ram[a0 + 2]);
                    psx->semaphores[mipsinfo.i].max = LE32(psx->psx_ram[a0 + 3]);

                    psx->semaphores[mipsinfo.i].current = psx->semaphores[mipsinfo.i].init;

                    psx->semaphores[mipsinfo.i].inuse = 1;

                    mipsinfo.i++;
                }

                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 6: // SignalSema
                a0--;
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: SignalSema(%d) (current %d)\n", a0, psx->semaphores[a0].current);
#endif

                if (psx->semaphores[a0].threadsWaiting != 0) {
                    for (i = 0; i < psx->iNumThreads; i++) {
                        if ((psx->threads[i].iState == TS_WAITSEMA) &&
                            (psx->threads[i].waitparm == a0)) {
                            psx->threads[i].iState = TS_RUNNING;
                            psx->threads[i].waitparm = 0;
                            psx->rescheduleNeeded = 1;
                            psx->semaphores[a0].threadsWaiting--;
                            if (psx->semaphores[a0].threadsWaiting == 0)
                                break;
                        }
                    }

                    mipsinfo.i = 0;
                } else {
                    if (psx->semaphores[a0].current < psx->semaphores[a0].max) {
                        psx->semaphores[a0].current++;
                        mipsinfo.i = 0;
                    } else {
                        mipsinfo.i = -420;    // semaphore overflow
                    }
                }

                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 7: // iSignalSema
                a0--;
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: iSignalSema(%d)\n", a0);
#endif

                if (psx->semaphores[a0].threadsWaiting != 0) {
                    for (i = 0; i < psx->iNumThreads; i++) {
                        if ((psx->threads[i].iState == TS_WAITSEMA) &&
                            (psx->threads[i].waitparm == a0)) {
                            psx->threads[i].iState = TS_RUNNING;
                            psx->threads[i].waitparm = 0;
                            psx->rescheduleNeeded = 1;
                            psx->semaphores[a0].threadsWaiting--;
                            if (psx->semaphores[a0].threadsWaiting == 0)
                                break;
                        }
                    }

                    mipsinfo.i = 0;
                } else {
                    if (psx->semaphores[a0].current < psx->semaphores[a0].max) {
                        psx->semaphores[a0].current++;
                        mipsinfo.i = 0;
                    } else {
                        mipsinfo.i = -420;    // semaphore overflow
                    }
                }

                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 8: // WaitSema
                a0--;
#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: WaitSema(%d) (cnt %d) (th %d) (PC=%x)\n", a0, psx->iCurThread, psx->semaphores[a0].current, (uint32)mipsinfo.i);
#endif

                if (psx->semaphores[a0].current > 0) {
                    psx->semaphores[a0].current--;
                } else {
                    psx->threads[psx->iCurThread].iState = TS_WAITSEMA;
                    psx->threads[psx->iCurThread].waitparm = a0;
                    psx->semaphores[a0].threadsWaiting++;
                    psx->rescheduleNeeded = 1;
                }

                mipsinfo.i = psx->semaphores[a0].current;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "timrman")) {
        switch (callnum) {
            case 4:    // AllocHardTimer
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: AllocHardTimer(%d %d %d)\n", a0, a1, a2);
#endif
                // source, size, prescale

                if (a1 != 32) {
                    psx->error_ptr += sprintf(psx->error_ptr,
                                              "IOP: AllocHardTimer doesn't support 16-bit timers!\n");
                }

                psx->iop_timers[psx->iNumTimers].source = a0;
                psx->iop_timers[psx->iNumTimers].prescale = a2;

                mipsinfo.i = psx->iNumTimers + 1;
                psx->iNumTimers++;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 6: // FreeHardTimer
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: FreeHardTimer(%d)\n", a0);
#endif
                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 10:// GetTimerCounter
                mipsinfo.i = psx->iop_timers[a0 - 1].count;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 20: // SetTimerHandler
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: SetTimerHandler(%d %d %08x %08x)\n", a0, a1, a2, a3);
#endif
                // id, compare, handler, common (last is param for handler)

                psx->iop_timers[a0 - 1].target = a1;
                psx->iop_timers[a0 - 1].handler = a2;
                psx->iop_timers[a0 - 1].hparam = a3;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 22: // SetupHardTimer
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: SetupHardTimer(%d %d %d %d)\n", a0, a1, a2, a3);
#endif
                // id, source, mode, prescale

                psx->iop_timers[a0 - 1].source = a1;
                psx->iop_timers[a0 - 1].mode = a2;
                psx->iop_timers[a0 - 1].prescale = a3;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 23: // StartHardTimer
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: StartHardTimer(%d)\n", a0);
#endif

                psx->iop_timers[a0 - 1].iActive = 1;
                psx->iop_timers[a0 - 1].count = 0;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 24: // StopHardTimer
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: StopHardTimer(%d)\n", a0);
#endif

                psx->iop_timers[a0 - 1].iActive = 0;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "sysclib")) {
        switch (callnum) {
            case 12:    // memcpy
            {
                uint8 *dst, *src;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: memcpy(%08x, %08x, %d)\n", a0, a1, a2);
#endif

                dst = (uint8 *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                src = (uint8 *) &psx->psx_ram[(a1 & 0x1fffff) / 4];
                // get exact byte alignment
                dst += a0 % 4;
                src += a1 % 4;

                while (a2) {
                    *dst = *src;
                    dst++;
                    src++;
                    a2--;
                }

                // v0 = a0
                mipsinfo.i = a0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;

            case 13:    // memmove
            {
                uint8 *dst, *src;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: memmove(%08x, %08x, %d)\n", a0, a1, a2);
#endif

                dst = (uint8 *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                src = (uint8 *) &psx->psx_ram[(a1 & 0x1fffff) / 4];
                // get exact byte alignment
                dst += a0 % 4;
                src += a1 % 4;

                dst += a2 - 1;
                src += a2 - 1;

                while (a2) {
                    *dst = *src;
                    dst--;
                    src--;
                    a2--;
                }

                // v0 = a0
                mipsinfo.i = a0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;

            case 14:    // memset
            {
                uint8 *dst;

#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
					printlog(psx, "IOP: memset(%08x, %02x, %d) [PC=%x]\n", a0, a1, a2, (uint32)mipsinfo.i);
#endif

                dst = (uint8 *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                dst += (a0 & 3);

                memset(dst, a1, a2);
            }
                break;

            case 17:    // bzero
            {
                uint8 *dst;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: bzero(%08x, %08x)\n", a0, a1);
#endif

                dst = (uint8 *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                dst += (a0 & 3);
                memset(dst, 0, a1);
            }
                break;

            case 19:    // sprintf
                mname = (char *) psx->psx_ram;
                str1 = (char *) psx->psx_ram;
                mname += a0 & 0x1fffff;
                str1 += a1 & 0x1fffff;

#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: sprintf(%08x, %s, ...) [PC=%08x]\n", a0, str1, (uint32)mipsinfo.i);
				printlog(psx, "%x %x %x %x\n", a0, a1, a2, a3);
#endif

                iop_sprintf(psx, mname, str1, 2);    // a2 is first parameter

#if DEBUG_HLE_IOP
                printlog(psx, "     = [%s]\n", mname);
#endif
                break;

#if 0
                                                                                                                                        case 21:    // strchr ??
				{
					uint8 *src, chr;

					#if DEBUG_HLE_IOP
					printlog(psx, "IOP: strchr(%08x, %08x)\n", a0, a1);
					#endif

					src = (uint8 *)&psx->psx_ram[(a0 & 0x1fffff) / 4];
					src += a0 & 3;

					while (*src != a1 && *src != '\0')
					{
						src++;
						a0++;
					}

					if (*src == '\0')
					{
						a0 = 0;
					}

					// v0 = a0
					mipsinfo.i = a0;
					mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
				}
				break;
#endif

            case 22:    // strcmp
            {
                uint8 *src0, *src1;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: strcmp(%08x, %08x)\n", a0, a1);
#endif

                src0 = (uint8 *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                src1 = (uint8 *) &psx->psx_ram[(a1 & 0x1fffff) / 4];
                src0 += a0 & 3;
                src1 += a1 & 3;

                mipsinfo.i = strcmp((const char *) src0, (const char *) src1);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;

            case 23:    // strcpy
            {
                uint8 *dst, *src;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: strcpy(%08x, %08x)\n", a0, a1);
#endif

                dst = (uint8 *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                src = (uint8 *) &psx->psx_ram[(a1 & 0x1fffff) / 4];
                // get exact byte alignment
                dst += a0 % 4;
                src += a1 % 4;

                while (*src != '\0') {
                    *dst = *src;
                    dst++;
                    src++;
                }
                *dst = '\0';

                // v0 = a0
                mipsinfo.i = a0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;

            case 27:    // strlen
            {
                char *dst;

#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
					printlog(psx, "IOP: strlen(%08x) [PC=%x]\n", a0, (uint32)mipsinfo.i);
#endif

                dst = (char *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                dst += (a0 & 3);
                mipsinfo.i = strlen(dst);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;

            case 30:    // strncpy
            {
                char *dst, *src;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: strncpy(%08x, %08x, %d)\n", a0, a1, a2);
#endif

                dst = (char *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                src = (char *) &psx->psx_ram[(a1 & 0x1fffff) / 4];
                // get exact byte alignment
                dst += a0 % 4;
                src += a1 % 4;

                while ((*src != '\0') && (a2 > 0)) {
                    *dst = *src;
                    dst++;
                    src++;
                    a2--;
                }
                *dst = '\0';

                // v0 = a0
                mipsinfo.i = a0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
            }
                break;


            case 36:    // strtol
                mname = (char *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                mname += (a0 & 3);

                if (a1) {
                    psx->error_ptr += sprintf(psx->error_ptr,
                                              "IOP: Unhandled strtol with non-NULL second parm\n");
                }

                mipsinfo.i = strtol(mname, NULL, a2);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 38:    // strtoul
                mname = (char *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                mname += (a0 & 3);

                if (a1) {
                    psx->error_ptr += sprintf(psx->error_ptr,
                                              "IOP: Unhandled strtoul with non-NULL second parm\n");
                }

                mipsinfo.i = strtoul(mname, NULL, a2);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "intrman")) {
        switch (callnum) {
            case 4:    // RegisterIntrHandler
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: RegisterIntrHandler(%d %08x %08x %08x)\n", a0, a1, a2, a3);
#endif

                if (a0 == 9) {
                    psx->irq9_fval = a1;
                    psx->irq9_cb = a2;
                    psx->irq9_flag = a3;
                }

                // DMA4?
                if (a0 == 36) {
                    psx->dma4_fval = a1;
                    psx->dma4_cb = a2;
                    psx->dma4_flag = a3;
                }

                // DMA7?
                if (a0 == 40) {
                    psx->dma7_fval = a1;
                    psx->dma7_cb = a2;
                    psx->dma7_flag = a3;
                }
                break;

            case 5:    // ReleaseIntrHandler
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: ReleaseIntrHandler(%d)\n", a0);
#endif
                break;

            case 6:    // EnableIntr
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: EnableIntr(%d)\n", a0);
#endif
                break;

            case 7:    // DisableIntr
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: DisableIntr(%d)\n", a0);
#endif
                break;

            case 8: // CpuDisableIntr
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: CpuDisableIntr(%d)\n", a0);
#endif
                break;

            case 9: // CpuEnableIntr
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: CpuEnableIntr(%d)\n", a0);
#endif
                break;

            case 17:    // CpuSuspendIntr
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: CpuSuspendIntr\n");
#endif

                // if already suspended, return an error code
                if (psx->intr_susp) {
                    mipsinfo.i = -102;
                } else {
                    mipsinfo.i = 0;
                }
                psx->intr_susp = 1;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 18:    // CpuResumeIntr
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: CpuResumeIntr\n");
#endif
                psx->intr_susp = 0;
                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 23:    // QueryIntrContext
#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: QueryIntrContext(PC=%x)\n", (uint32)mipsinfo.i);
#endif
                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "loadcore")) {
        switch (callnum) {
            case 5: // FlushDcache
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: FlushDcache()\n");
#endif
                break;

            case 6:    // RegisterLibraryEntries
                a0 &= 0x1fffff;
#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: RegisterLibraryEntries(%08x) (PC=%x)\n", a0, (uint32)mipsinfo.i);
#endif

                if (psx->psx_ram[a0 / 4] == LE32(0x41c00000)) {
                    a0 += 3 * 4;
                    memcpy(&psx->reglibs[psx->iNumLibs].name, &psx->psx_ram[a0 / 4], 8);
                    psx->reglibs[psx->iNumLibs].name[8] = '\0';
#if DEBUG_HLE_IOP
                    printlog(psx, "Lib name [%s]\n", psx->reglibs[psx->iNumLibs].name);
#endif
                    a0 += 2 * 4;
                    psx->reglibs[psx->iNumLibs].dispatch = a0;
                    psx->iNumLibs++;
                } else {
                    psx->error_ptr += sprintf(psx->error_ptr,
                                              "ERROR: Entry table signature missing\n");
                }

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "sysmem")) {
        uint32 newAlloc;

        switch (callnum) {
            case 4:    // AllocMemory
                newAlloc = psf2_get_loadaddr(psx);
                // make sure we're 16-byte aligned
                if (newAlloc & 15) {
                    newAlloc &= ~15;
                    newAlloc += 16;
                }

                if (a1 & 15) {
                    a1 &= ~15;
                    a1 += 16;
                }

                if (a1 ==
                    1114112)    // HACK for crappy code in Shadow Hearts rip that assumes the buffer address
                {
                    psx->error_ptr += sprintf(psx->error_ptr, "SH Hack: was %x now %x\n", newAlloc,
                                              0x60000);
                    newAlloc = 0x60000;
                }

                psf2_set_loadaddr(psx, newAlloc + a1);

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: AllocMemory(%d, %d, %x) = %08x\n", a0, a1, a2, newAlloc|0x80000000);
#endif

                mipsinfo.i = newAlloc; // | 0x80000000;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 5:    // FreeMemory
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: FreeMemory(%x)\n", a0);
#endif
                break;

            case 7:    // QueryMaxFreeMemSize
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: QueryMaxFreeMemSize\n");
#endif

                mipsinfo.i = (2 * 1024 * 1024) - psf2_get_loadaddr(psx);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 8:    // QueryTotalFreeMemSize
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: QueryTotalFreeMemSize\n");
#endif

                mipsinfo.i = (2 * 1024 * 1024) - psf2_get_loadaddr(psx);
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 14: // Kprintf
                mname = (char *) psx->psx_ram;
                mname += a0 & 0x1fffff;
                mname += (a0 & 3);

                iop_sprintf(psx, out, mname, 1);    // a1 is first parm

                if (out[strlen(out) - 1] != '\n') {
                    strcat(out, "\n");
                }

                // filter out ESC characters
                {
                    int ch;

                    for (ch = 0; ch < strlen(out); ch++) {
                        if (out[ch] == 27) {
                            out[ch] = ']';
                        }
                    }
                }

#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "KTTY: %s [PC=%x]\n", out, (uint32)mipsinfo.i);
#endif

                if (psx->console_callback) {
                    psx->console_callback(psx->console_context, out);
                }

#if 0
                                                                                                                                        {
					FILE *f;
					f = fopen("psxram.bin", "wb");
					fwrite(psx_ram, 2*1024*1024, 1, f);
					fclose(f);
				}
#endif
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }
    } else if (!strcmp(name, "modload")) {
        uint8 *tempmem;
        uint32 newAlloc;

        switch (callnum) {
            case 7:    // LoadStartModule
                mname = (char *) &psx->psx_ram[(a0 & 0x1fffff) / 4];
                mname += 8;
                str1 = (char *) &psx->psx_ram[(a2 & 0x1fffff) / 4];
#if DEBUG_HLE_IOP
                printlog(psx, "LoadStartModule: %s\n", mname);
#endif

                // get 2k for our parameters
                newAlloc = psf2_get_loadaddr(psx);
                // force 16-byte alignment
                if (newAlloc & 0xf) {
                    newAlloc &= ~0xf;
                    newAlloc += 16;
                }
                psf2_set_loadaddr(psx, newAlloc + 2048);

                tempmem = (uint8 *) psx->elf_scratch;
                if (psf2_load_file(psx, mname, tempmem, 2 * 1024 * 1024) != 0xffffffff) {
                    uint32 start;
                    int i;

                    start = psf2_load_elf(psx, tempmem, 2 * 1024 * 1024);

                    if (start != 0xffffffff) {
                        uint32 args[20], numargs = 1, argofs;
                        uint8 *argwalk = (uint8 *) psx->psx_ram, *argbase;

                        argwalk += (a2 & 0x1fffff);
                        argbase = argwalk;

                        args[0] = a0;    // program name is argc[0]

                        argofs = 0;

                        if (a1 > 0) {
                            args[numargs] = a2;
                            numargs++;

                            while (a1) {
                                if ((*argwalk == 0) && (a1 > 1)) {
                                    args[numargs] = a2 + argofs + 1;
                                    numargs++;
                                }
                                argwalk++;
                                argofs++;
                                a1--;
                            }
                        }

                        for (i = 0; i < numargs; i++) {
#if DEBUG_HLE_IOP
                            //							printlog(psx, "Arg %d: %08x [%s]\n", i, args[i], &argbase[args[i]-a2]);
#endif
                            psx->psx_ram[(newAlloc / 4) + i] = LE32(args[i]);
                        }

                        // set argv and argc
                        mipsinfo.i = numargs;
                        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);
                        mipsinfo.i = 0x80000000 | newAlloc;
                        mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R5, &mipsinfo);

                        // leave RA alone, PC = module start
                        // (NOTE: we get called in the delay slot!)
                        mipsinfo.i = start - 4;
                        mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
                    }
                }
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
                break;
        }

    } else if (!strcmp(name, "ioman")) {
        switch (callnum) {
            case 4:    // open
            {
                int i, slot2use;

                slot2use = -1;
                for (i = 0; i < MAX_FILE_SLOTS; i++) {
                    if (psx->filestat[i] == 0) {
                        slot2use = i;
                        break;
                    }
                }

                if (slot2use == -1) {
                    psx->error_ptr += sprintf(psx->error_ptr, "IOP: out of file slots!\n");
                    mipsinfo.i = 0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    return;
                }

                mname = (char *) psx->psx_ram;
                mname += (a0 & 0x1fffff);

                if (!strncmp(mname, "aofile:", 7)) {
                    mname += 8;
                } else if (!strncmp(mname, "hefile:", 7)) {
                    mname += 8;
                } else if (!strncmp(mname, "host0:", 6)) {
                    mname += 7;
                }

                mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: open(\"%s\") (PC=%08x)\n", mname, (uint32)mipsinfo.i);
#endif

                psx->filename[slot2use] = malloc(strlen(mname) + 1);
                strcpy(psx->filename[slot2use], mname);

                psx->filepos[slot2use] = 0;
                psx->filestat[slot2use] = 1;
                {
                    uint8 tempbuf[4];
                    psx->filesize[slot2use] = psx->readfile(psx->readfile_context, mname, 0,
                                                            tempbuf, 0);
                }

                if (psx->filesize[slot2use] >= 0x80000000) {
                    mipsinfo.i = 0;
                } else {
                    mipsinfo.i = slot2use + 1;
                }
            }
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 5:    // close
                a0--;
#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: close(%d) (PC=%08x)\n", a0, (uint32)mipsinfo.i);
#endif
                free(psx->filename[a0]);
                psx->filename[a0] = (uint8 *) NULL;
                psx->filepos[a0] = 0;
                psx->filestat[a0] = 0;
                break;

            case 6: { // read
                uint8 *rp;

                a0--;

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: read(%x %x %d) [pos %d size %d]\n", a0, a1, a2, psx->filepos[a0], psx->filesize[a0]);
#endif

                if (psx->filepos[a0] + a2 > psx->filesize[a0])
                    a2 = psx->filesize[a0] - psx->filepos[a0];

                rp = (uint8 *) psx->psx_ram;
                rp += (a1 & 0x1fffff);

                a2 = psx->readfile(psx->readfile_context, psx->filename[a0], psx->filepos[a0], rp,
                                   a2);

                if (a2 > 0)
                    psx->filepos[a0] += a2;
                else
                    a2 = 0;

                mipsinfo.i = a2;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;
            }
            case 8:    // lseek
                a0--;
#if DEBUG_HLE_IOP
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: lseek(%d, %d, %s) (PC=%08x)\n", a0, a1, seek_types[a2], (uint32)mipsinfo.i);
#endif

                switch (a2) {
                    case 0:    // SEEK_SET
                        if (a1 <= psx->filesize[a0]) {
                            psx->filepos[a0] = a1;
                        }
                        break;
                    case 1:    // SEEK_CUR
                        if ((a1 + psx->filepos[a0]) < psx->filesize[a0]) {
                            psx->filepos[a0] += a1;
                        }
                        break;
                    case 2:    // SEEK_END
                        psx->filepos[a0] = psx->filesize[a0] - a1;
                        break;
                }

                mipsinfo.i = psx->filepos[a0];
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 20:    // AddDrv
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: AddDrv(%x)\n", a0);
#endif

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 21:    // DelDrv
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: DelDrv(%x)\n", a0);
#endif

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
        }
    } else if (!strcmp(name, "vblank")) {
        switch (callnum) {
            // XXX
            case 4:    // WaitVblankStart
            case 5:    // WaitVblankEnd
                if (psx->iCurThread >= 0) {
                    psx->threads[psx->iCurThread].iState = TS_WAITDELAY;
                    psx->threads[psx->iCurThread].waitparm = 768 * psx->vblank_samples_until_next;
                    psx->rescheduleNeeded = 1;
                }
                break;

            default:
                psx->error_ptr += sprintf(psx->error_ptr,
                                          "IOP: Unhandled service %d for module %s\n", callnum,
                                          name);
        }
    } else {
        int lib;

        if (psx->iNumLibs > 0) {
            for (lib = 0; lib < psx->iNumLibs; lib++) {
                if (!strcmp(name, psx->reglibs[lib].name)) {
#if DEBUG_HLE_IOP
                                                                                                                                            uint32 PC;

					mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
					PC = (uint32)mipsinfo.i;
#endif

                    // zap the delay slot handling
                    mipsinfo.i = 0;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYV, &mipsinfo);
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &mipsinfo);

                    mipsinfo.i = LE32(psx->psx_ram[(psx->reglibs[lib].dispatch / 4) + callnum]);

                    // (NOTE: we get called in the delay slot!)
#if DEBUG_HLE_IOP
                                                                                                                                            printlog(psx, "IOP: Calling %s (%d) service %d => %08x (parms %08x %08x %08x %08x) (PC=%x)\n",
							 psx->reglibs[lib].name,
							 lib,
							 callnum,
							 (uint32)mipsinfo.i,
							 a0, a1, a2, a3, PC);
#endif

#if 0
                                                                                                                                            if (!strcmp(psx->reglibs[lib].name, "ssd"))
					{
						if (callnum == 37)
						{
							psx->psxcpu_verbose = 4096;
						}
					}
#endif

                    mipsinfo.i -= 4;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

                    return;
                }
            }
        }

        psx->error_ptr += sprintf(psx->error_ptr, "IOP: Unhandled service %d for module %s\n",
                                  callnum, name);
    }
}

void psx_set_refresh(PSX_STATE *psx, uint32 refresh) {
    psx->psf_refresh = refresh;
}
