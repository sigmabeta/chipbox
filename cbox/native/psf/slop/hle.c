/////////////////////////////////////////////////////////////////////////////
//
// hle - High-Level Emulation of the IOP BIOS (PS1 path)
//
// Ported from Neill Corlett-era the reference psx_hw.c (psx_bios_hle / psx_hw_init /
// psx_bios_exception), re-targeted from the reference's MAME-style MIPS interpreter
// onto HE's r3000 via he/hle_cpu.h. HE keeps owning the hardware (timers,
// interrupt controller, SPU, DMA, cycle/sound pacing); only the BIOS
// *software* layer (call vectors, exception dispatcher, event/RootCounter
// bookkeeping, heap) is emulated here.
//
// The R4 inversion: the reference ran event handlers / entry_int re-entrantly with
// a nested mips_execute() loop. HE's r3000_execute is a flat slice and
// cannot recurse from a sentinel trap, so softcalls are armed as
// continuations (PC=func, $ra=SOFTCALL_RA) and resumed when the CPU traps
// back at SOFTCALL_RA. See docs/psf-hle-plan.md.
//
// Status: PS1 first cut. Synchronous A0/B0/C0 calls are complete; the
// exception/softcall machine is implemented. Audible parity with the BIOS
// oracle across the corpus is the iterative gate (Phase 1).
//
/////////////////////////////////////////////////////////////////////////////

#ifndef EMU_COMPILE
#error "Hi I forgot to set EMU_COMPILE"
#endif

#include "hle.h"
#include "hle_cpu.h"
#include "r3000.h"
#include "iop.h"
#include "ioptimer.h"
#include "spu.h"

/////////////////////////////////////////////////////////////////////////////
//
// Optional PC/dispatch trace (HLE_TRACE=1 in the environment). Off by
// default; costs nothing in the hot path when disabled.
//
static int hle_trace = -1;

static EMU_INLINE int trace_on(void) {
    if (hle_trace < 0) {
        const char *e = getenv("HLE_TRACE");
        hle_trace = (e && *e != '0') ? 1 : 0;
    }
    return hle_trace;
}

#define TRACE(...) do { if(trace_on()) fprintf(stderr, __VA_ARGS__); } while(0)

/////////////////////////////////////////////////////////////////////////////
//
// the reference BIOS-kernel memory layout (low RAM, below the 0x10000 floor PSF
// EXEs load above -- psf1_load enforces addr >= 0x10000).
//
#define LONGJMP_BUFFER   (0x0200)
#define SOFTCALL_RA      (0x80001000u)   // $ra trampoline for armed softcalls
#define SOFTCALL_LOW     (0x00001000u)   // ...masked into RAM
// PS2 IOP idle-spin stub. the reference's ps2_hw_slice skips mips_execute when
// no thread is runnable (iCurThread==-1); the CPU must NOT keep running
// the just-frozen thread's code (it would busy-loop and -- via the
// guest-index bounds-guards -- corrupt thread 0 with stray SleepThread/
// DelayThread calls). Instead we park the CPU on a 2-word self-branch
// (`beq $0,$0,-1; nop`) in unused low IOP RAM: it burns cycles (so
// hle_advance keeps ticking root counters / decrementing TS_WAITDELAY)
// but never re-enters the IOP-call stub, until a timer/sema wakes a
// thread and hle_ps2_check_resched switches the CPU to it.
// (PS2_IDLE_PC itself is defined in hle.h -- shared with hle_ps2.c.)
#define EVENTS_BEGIN     (0x3000)
#define EVTCB_BYTES      (28)             // 7 x uint32
#define MAX_EVENT        (32)
#define EVENTS_SIZE      (EVTCB_BYTES * MAX_EVENT)        // 0x380
#define B0TABLE_BEGIN    (EVENTS_BEGIN + EVENTS_SIZE)     // 0x3380
#define B0TABLE_SIZE     (0x5D * 4)                       // 0x174
#define C0TABLE_BEGIN    (B0TABLE_BEGIN + B0TABLE_SIZE)   // 0x34F4
#define C0TABLE_SIZE     (0x1C * 4)                       // 0x70
#define C0_EXC_BEGIN     (C0TABLE_BEGIN + C0TABLE_SIZE)   // 0x3564

// EvtCtrlBlk field indices (uint32 each).
enum {
    EV_ISVALID = 0, EV_ENABLED, EV_CLASSID, EV_SPEC, EV_MODE, EV_FUNC, EV_FIRED
};

// heap block-record offsets
enum {
    BLK_STAT = 0, BLK_SIZE = 4, BLK_FD = 8, BLK_BK = 12
};

// BIOS RootCounter / DMA event class ids
#define CLASS_RCNT  (0xF2000002u)   // RootCounter 2 (canonical / most common)
#define CLASS_VSYNC (0xF2000003u)   // RootCounter 3 / VBLANK (I_STAT bit 0)
#define CLASS_DMA   (0xF0000009u)

// A hardware RootCounter event is class 0xF200000N for counter N (0,1,2 ->
// I_STAT bits 4,5,6, the `sig & 0x70` gate). The dispatch used to match only
// CLASS_RCNT (0xF2000002), so a sequencer driven off RootCounter 0 or 1
// (e.g. Legacy of Kain - Soul Reaver, class 0xF2000001) never got its tick
// callback and played pure silence. 0xF2000003 is VBLANK -> CLASS_VSYNC,
// handled separately, so it is deliberately excluded here.
#define IS_RCNT_CLASS(c) ((c) >= 0xF2000000u && (c) <= 0xF2000002u)

/////////////////////////////////////////////////////////////////////////////
//
// Per-track HLE side state.
//
// chipbox loads exactly one track at a time and never relocates HE state
// (no HE save-state path through the JNI surface), so a file-static is
// sound here; documented in docs/psf-hle-plan.md. The BIOS kernel tables
// (events, B0/C0) live in guest RAM at the reference offsets above so the
// ported code accesses them exactly as before.
//
#define SOFTCALL_FIFO 64

enum {
    FIN_NONE = 0, FIN_ENTRYINT, FIN_RESTORE
};

typedef struct {
    void *iop;
    void *r3000;

    uint32 heap_addr;
    uint32 eventsAllocated;

    // exception register snapshot: 0..31 GPR, 32 HI, 33 LO, 34 PC,
    // 35 DELAYV, 36 DELAY
    uint32 irq_regs[37];

    // armed-softcall FIFO + how to finish once it drains
    uint32 sc_func[SOFTCALL_FIFO];
    uint32 sc_arg[SOFTCALL_FIFO];
    int sc_head, sc_tail;
    int finalizer;

    int in_exception;   // an exception is being serviced
    int irq_mutex;      // guard against IRQ re-entry
    int WAI;            // WaitEvent idle

    // --- HLE-owned PS1 hardware (replaces HE's intr/timer/DMA for the
    // ranges the BIOS would mediate). the reference ran its own root_cnts ioptimer
    // + irq_data/irq_mask separate from the CPU; we do the same so the
    // sound driver sees the RootCounter semantics it expects. HE still owns
    // the SPU core, cycle pacing and the spu_dma copy itself.
    uint8 root_cnts[512]; // >= sizeof(struct IOPTIMER_STATE); checked at init
    uint32 irq_data, irq_mask;
    int irq_masked, intr_susp;
    uint32 dma4_madr, dma4_bcr, dma4_chcr, dma_pcr, dma_icr;
    uint32 dma7_madr, dma7_bcr, dma7_chcr;  // PS2 SPU2 core-1 DMA channel
    int dma4_delay;     // in 768-cycle slices, like the reference
    uint32 adv_accum;      // cycle accumulator for 768-cycle slicing
    uint32 ack_pending;    // I_STAT bits to ack after the handler chain runs
    // RootCounter period re-delivery (non-0xF2000002 / game-hooked-entry_int
    // path only). The ioptimer keeps counting while the game's entry_int
    // handler runs with the RCnt cause held pending (deferred ack); each
    // period that elapses in that window is OR-coalesced into the single
    // I_STAT bit and otherwise lost -> the sequencer ticks once per N real
    // periods (Persona 1: ~half tempo). Count the coalesced periods and
    // replay them one handler invocation at a time at ReturnFromException.
    int rcnt_event_present; // a class 0xF2000002 RCnt event exists (VP path)
    uint32 rcnt_missed;    // coalesced RCnt periods awaiting re-delivery
    uint32 rcnt_missed_bits; // which RCnt I_STAT bits (subset of 0x70)

    // --- PS2 IOP-kernel HLE (Phase 2) ---
    int ps2;            // PS2 (PSF2) mode
    int ps2_booted;     // psf2.irx loaded + entered
    uint32 loadAddr;       // module load pointer (the reference psx->loadAddr)
    int ps2_sc_active;  // a PS2 IOP IRQ/DMA-handler softcall is running
    int ps2_sc_sync;    // inside the synchronous (nested) handler drain
    int ps2_pump;       // PSF2-HLE per-sample pump owns counter cadence
    uint32 ps2_sc_cur_func;// routine of the currently-running ps2_sc handler
    uint32 ps2_sc_cur_arg; // arg    of the currently-running ps2_sc handler
    uint32 ps2_sc_regs[37];// interrupted-thread snapshot for the above
} HLE_STATE;

static HLE_STATE g_hle;

// Screen refresh for the HLE-owned PS1 root counters: 60 = NTSC, 50 = PAL.
// Lives OUTSIDE g_hle (which hle_init_ps1 memsets) so a region detected at
// load time survives the init that runs before it, and any later
// hle_init_ps1 re-rates RCNT() correctly. Without this the HLE VBLANK was
// always 60 Hz, so PAL rips (e.g. South Park) ran the sequencer 60/50 =
// 1.2x too fast -- iop_set_refresh() only re-rated the IOP timer, never
// these counters, which actually clock the PS1 sequencer.
static uint32 g_ps1_refresh = 60;

/////////////////////////////////////////////////////////////////////////////
//
// RAM helpers. HE IOP RAM is a host-endian uint32[] (2 MB); the build is
// little-endian so the reference's LE32() is the identity.
//
static EMU_INLINE uint32 *RAM(void) { return (uint32 *) iop_get_ram(g_hle.iop); }

static EMU_INLINE uint32 rd32(uint32 a) { return RAM()[(a & 0x1FFFFF) >> 2]; }

static EMU_INLINE void wr32(uint32 a, uint32 v) { RAM()[(a & 0x1FFFFF) >> 2] = v; }

static EMU_INLINE uint8 *RAMB(uint32 a) { return ((uint8 *) RAM()) + (a & 0x1FFFFF); }

#define EVT(i, f) (RAM()[(EVENTS_BEGIN + (i) * EVTCB_BYTES + (f) * 4) >> 2])

static MIPS_CPU_CONTEXT *CPU(void) {
    static MIPS_CPU_CONTEXT c;
    c.r3000 = g_hle.r3000;
    c.iop = g_hle.iop;
    return &c;
}

static EMU_INLINE uint32 GET(uint32 sel) {
    union cpuinfo m;
    mips_get_info(CPU(), sel, &m);
    return (uint32) m.i;
}

static EMU_INLINE void SET(uint32 sel, uint32 v) {
    union cpuinfo m;
    m.i = (sint64) (sint32) v;
    mips_set_info(CPU(), sel, &m);
}

#define R(n)   GET(CPUINFO_INT_REGISTER + MIPS_R0 + (n))
#define SETR(n, v) SET(CPUINFO_INT_REGISTER + MIPS_R0 + (n), (v))

//
// PC handoff. caseMINOR(0x0B) in r3000.c does `PC += 4` immediately after
// hle_dispatch returns, so set PC to (target - 4) to land on `target`.
// Guarded by !slot in the decoder, so no delay-slot interaction here.
//
static EMU_INLINE void hle_set_pc(uint32 target) {
    SET(CPUINFO_INT_PC, target - 4);
}

/////////////////////////////////////////////////////////////////////////////
//
// 0x1FC00000 BIOS region in HLE mode: there is no BIOS image, so every
// fetch/load there returns the sentinel (keeps the region inert).
//
uint32 EMU_CALL bios_hle_lw(void *iopstate, uint32 a, uint32 mask) {
    (void) iopstate;
    (void) a;
    return R3000_HLE_SENTINEL & mask;
}

void EMU_CALL bios_hle_sw(void *iopstate, uint32 a, uint32 d, uint32 mask) {
    (void) iopstate;
    (void) a;
    (void) d;
    (void) mask; // ROM: drop stores
}

/////////////////////////////////////////////////////////////////////////////
//
// HLE-owned PS1 hardware: interrupt controller + root counters + SPU DMA4.
//
// In HLE mode iop.c routes the I_STAT/I_MASK, RCnt and DMA4 register
// ranges here instead of to HE's own intr/timer/dma, and HE's
// intr_update_ip stops driving the CPU IRQ line (we own it). This gives
// the sound driver the exact RootCounter view the reference provided.
//
static void *RCNT(void) { return (void *) g_hle.root_cnts; }

// Mirror HE's intr_update_ip: assert CPU IP2 iff an unmasked IRQ pends.
static void psx_irq_update(void) {
    uint32 ip = (!g_hle.irq_masked && (g_hle.irq_data & g_hle.irq_mask)) ? 0x4 : 0;
    if (ip) g_hle.WAI = 0;
    r3000_setinterrupt(g_hle.r3000, ip);
}

static void psx_irq_set(uint32 irq) {
    // RootCounter coalescing: if an RCnt bit is raised while the same bit
    // is still pending, the |= below is idempotent and that timer period
    // is lost. This happens on BOTH sequencer paths -- the game-hooked
    // entry_int path (cause deferred, e.g. Persona 1) and the BIOS
    // 0xF2000002 softcall path (the armed sequencer is mid-flight, e.g.
    // FF7/FFT/Xenogears/Valkyrie Profile) -- and drops ~half the periods
    // (~2x slow). Count the lost periods here; they are re-delivered (one
    // sequencer tick each) at ReturnFromException for the entry_int path
    // and via extra softcall arms for the 0xF2000002 path.
    uint32 rc = irq & 0x70;
    if (rc && (g_hle.irq_data & rc)) {
        if (g_hle.rcnt_missed < 32) g_hle.rcnt_missed++;
        g_hle.rcnt_missed_bits |= rc;
    }
    g_hle.irq_data |= irq;
    psx_irq_update();
}

// 0x1F801000-0x1F80107F : interrupt controller (I_STAT/I_MASK/I_CTL).
// HE callback convention: mask = the active (written/read) bits.
uint32 EMU_CALL hle_intr_lw(void *iop, uint32 a, uint32 mask) {
    uint32 d = 0;
    (void) iop;
    switch (a & 0x7C) {
        case 0x70:
            d = g_hle.irq_data;
            break;
        case 0x74:
            d = g_hle.irq_mask;
            break;
        case 0x78:
            d = g_hle.irq_masked ? 0 : 1;
            g_hle.irq_masked = 1;
            psx_irq_update();
            break;
    }
    return d & mask;
}

void EMU_CALL hle_intr_sw(void *iop, uint32 a, uint32 d, uint32 mask) {
    (void) iop;
    switch (a & 0x7C) {
        case 0x70: // I_STAT acknowledge. Accurate PS1 behaviour (DuckStation
            // interrupt_controller.cpp, and HE's own intr_sw): the status word is
            // simply ANDed with the written value -- writing 0 to a bit clears
            // it; the interrupt *mask* is NOT involved here. (the reference's formula
            // folded in irq_mask, which corrupted bits the handler did not ack.)
            g_hle.irq_data &= (d | ~mask);
            psx_irq_update();
            break;
        case 0x74: // I_MASK
            g_hle.irq_mask = (g_hle.irq_mask & ~mask) | (d & mask);
            psx_irq_update();
            break;
        case 0x78:
            g_hle.irq_masked = (d ^ 1) & 1;
            psx_irq_update();
            break;
    }
}

// 0x1F801100-0x1F80112F and 0x1F801480-0x1F8014AF : root counters.
uint32 EMU_CALL hle_timer_lw(void *iop, uint32 a, uint32 mask) {
    (void) iop;
    return ioptimer_lw(RCNT(), a, mask) & mask;
}

void EMU_CALL hle_timer_sw(void *iop, uint32 a, uint32 d, uint32 mask) {
    (void) iop;
    ioptimer_sw(RCNT(), a, d, mask);
}

// 0x1F801080-0x1F8010FF : DMA. Only channel 4 (SPU) and PCR/ICR matter
// for PS1 audio (instrument sample upload).
#define DMA_SPU_CYCLES_PER_HALFWORD (8)

static void softcall_arm(uint32 func, uint32 arg); // fwd (continuation engine)

// Deliver SPU-DMA4 completion: mark DMA-class events fired (+softcall any
// handler), set the DMA ICR completion flag, and raise the DMA IRQ.
static void dma4_complete(void) {
    int i;
    for (i = 0; i < MAX_EVENT; i++) {
        if (!EVT(i, EV_ISVALID) || !EVT(i, EV_ENABLED)) continue;
        if (EVT(i, EV_CLASSID) != CLASS_DMA) continue;
        EVT(i, EV_FIRED) = 1;
        if (EVT(i, EV_FUNC)) softcall_arm(EVT(i, EV_FUNC), 0);
    }
    if (g_hle.dma_icr & (1u << (16 + 4))) g_hle.dma_icr |= (1u << (24 + 4));
    TRACE("[hle] dma4_complete: irq_mask=%08X irq_masked=%d c0_status=%08X dicr=%08X\n",
          g_hle.irq_mask, g_hle.irq_masked,
          r3000_getreg(g_hle.r3000, R3000_REG_C0 + 12), g_hle.dma_icr);
    psx_irq_set(0x0008);
}

static void psx_dma4(void) {
    uint32 madr = g_hle.dma4_madr, bcr = g_hle.dma4_bcr, chcr = g_hle.dma4_chcr;
    bcr = (bcr >> 16) * (bcr & 0xffff) * 4;
    spu_dma(iop_get_spu_state(g_hle.iop), 0, RAM(), madr & 0x1ffffc, 0x1ffffc,
            bcr, (chcr == 0x01000201) ? 1 : 0);
    // HE's own DMA path (BIOS mode) is synchronous: dma_transfer ->
    // dma_signal_completion -> intr -> r3000 IRQ, so the completion
    // interrupt is taken on the instruction right after the CHCR write
    // (verified: BIOS's next PC after 8001C10C is the DMA ISR 8001BD40,
    // not the linear 8001C110). Reproduce that exactly -- complete now,
    // synchronously. dma4_complete() raises the DMA IRQ; r3000_setinterrupt
    // + r3000_break end the slice so the exception is taken immediately.
    g_hle.dma4_delay = 0;
    dma4_complete();
}

// Bridge for the PS2 SPU2 DMA (the reference ps2_dma4/ps2_dma7 in hle_ps2.c):
// the verbatim reference port has SPUSTATE inert, so the actual sample copy
// runs here against HE's real SPU2 core (`core` 0 or 1). `len` is the
// transfer length in bytes; `towrite` is RAM->SPU2 (1) vs SPU2->RAM (0).
void EMU_CALL hle_ps2_spu_dma(int core, uint32 madr, uint32 len, int towrite) {
    if (getenv("HLE_DMA")) {
        uint32 *p = (uint32 *) ((uint8 *) RAM() + (madr & 0x1ffffc));
        uint32 acc = 0, k;
        for (k = 0; k < (len / 4) && k < 256; k++) acc |= p[k];
        fprintf(stderr,
                "[dma] SPU2 core=%d madr=%08X len=%u write=%d data[0..3]=%08X %08X %08X %08X nz=%08X\n",
                core, madr, len, towrite, p[0], p[1], p[2], p[3], acc);
    }
    spu_dma(iop_get_spu_state(g_hle.iop), core, RAM(), madr & 0x1ffffc,
            0x1ffffc, len, towrite);
}

// the reference spu_interrupt_dma4/dma7: mark the SPU2 core DMA-complete STATX
// right before the libsd interrupt handler runs, so it reads a fresh
// per-core status and routes the completion to the right transfer
// callback. (Bridged here because the reference port's SPUSTATE is inert.)
void EMU_CALL hle_ps2_spu_dma_done(int core) {
    spu_set_dma_complete(iop_get_spu_state(g_hle.iop), (uint32) core);
}

// Address low bits (a & 0x1FFF): DMA4 (SPU2 core 0) = 0x10C0 MADR /
// 0x10C4..6 BCR / 0x10C8 CHCR; DMA7 (core 1) = 0x1500 / 0x1504..6 /
// 0x1508; DMA PCR 0x10F0, DICR 0x10F4.
uint32 EMU_CALL hle_dma_lw(void *iop, uint32 a, uint32 mask) {
    uint32 lo = a & 0x1FFF;
    (void) iop;
    if (lo == 0x10F0) return g_hle.dma_pcr & mask;
    if (lo == 0x10F4) return g_hle.dma_icr & mask;
    if (g_hle.ps2) {
        // libsd's sceSdVoiceTrans-style upload code busy-polls the channel
        // CHCR bit 24 to wait for the RAM->SPU2 transfer to finish. Report
        // it truthfully: SET while the transfer is in flight (its delay is
        // still counting down in psx_hw_runcounters), CLEAR once complete.
        // Always reporting "done" let libsd allocate/serve voices before the
        // sample data had landed, so notes were mapped to the wrong voices
        // -> wrong pitches. The BIOS path keeps its IOP DMA channel CHCR
        // busy for the modelled transfer duration; this matches it.
        if (lo == 0x10C8) {
            uint32 v = g_hle.dma4_chcr & ~0x01000000u;
            if (hle_ps2_dma_busy(0)) v |= 0x01000000u;
            return v & mask;
        }
        if (lo == 0x1508) {
            uint32 v = g_hle.dma7_chcr & ~0x01000000u;
            if (hle_ps2_dma_busy(1)) v |= 0x01000000u;
            return v & mask;
        }
        if (lo == 0x10C0) return g_hle.dma4_madr & mask;
        if (lo == 0x1500) return g_hle.dma7_madr & mask;
    }
    return 0;
}

void EMU_CALL hle_dma_sw(void *iop, uint32 a, uint32 d, uint32 mask) {
    uint32 lo = a & 0x1FFF;
    (void) iop;
    {
        static int n = 0;
        if (getenv("HLE_DMA") && n++ < 80)
            fprintf(stderr, "[dma] sw a=%08X lo=%03X d=%08X ps2=%d\n", a, lo, d, g_hle.ps2);
    }
    // ---- PS2 SPU2 DMA (the reference ps2_dma4 / ps2_dma7). The CHCR write kicks
    // an asynchronous RAM<->SPU2 transfer; psx_hw_runcounters counts
    // dma{4,7}_delay down and calls the libsd-registered DMA handler,
    // which SetEventFlags -> wakes the audio thread. DMA4 = SPU2 core 0
    // (0x1F8010C0..CF), DMA7 = core 1 (0x1F801500..0F). ----
    if (g_hle.ps2) {
        switch (lo) {
            case 0x10C0:
                g_hle.dma4_madr = d;
                return;
            case 0x10C4:
            case 0x10C6:
                g_hle.dma4_bcr = (g_hle.dma4_bcr & ~mask) | (d & mask);
                return;
            case 0x10C8:
                g_hle.dma4_chcr = d;
                hle_ps2_dma4(g_hle.dma4_madr, g_hle.dma4_bcr, d);
                return;
            case 0x1500:
                g_hle.dma7_madr = d;
                return;
            case 0x1504:
            case 0x1506:
                g_hle.dma7_bcr = (g_hle.dma7_bcr & ~mask) | (d & mask);
                return;
            case 0x1508:
                g_hle.dma7_chcr = d;
                hle_ps2_dma7(g_hle.dma7_madr, g_hle.dma7_bcr, d);
                return;
            case 0x10F0:
                g_hle.dma_pcr = (g_hle.dma_pcr & ~mask) | (d & mask);
                return;
            case 0x10F4:
                break;  // DICR -- shared handling below
            default:
                return;
        }
    }
    switch (lo) {
        case 0x10C0:
            g_hle.dma4_madr = d;
            break;                         // 0x10C0
        case 0x10C4:
        case 0x10C6:
            g_hle.dma4_bcr = (g_hle.dma4_bcr & ~mask) | (d & mask);
            break; // 0x10C4
        case 0x10C8:
            g_hle.dma4_chcr = d;
            psx_dma4();
            break;             // 0x10C8
        case 0x10F0:
            g_hle.dma_pcr = (g_hle.dma_pcr & ~mask) | (d & mask);
            break;
        case 0x10F4: { // DICR 0x10F4. the reference semantics: bits 0x7f000000 are the
            // per-channel IRQ flags, write-1-to-clear; 0x80000000 is the master
            // flag; 0x00ffffff is plain R/W. (the reference mem_mask == ~mask here.)
            g_hle.dma_icr =
                    (g_hle.dma_icr & ~mask)
                    | (~mask & 0x80000000u & g_hle.dma_icr)
                    | (~d & mask & 0x7f000000u & g_hle.dma_icr)
                    | (d & mask & 0x00ffffffu);
            if (g_hle.dma_icr & 0x7f000000u) g_hle.dma_icr &= ~0x80000000u;
            // I_STAT bit 3 (DMA) tracks a pending DICR channel IRQ. Once the
            // driver has W1C-acked the channel flag, the master DMA interrupt
            // deasserts -- without this the DMA IRQ re-fires forever and the
            // ISR never reaches ReturnFromException.
            if ((g_hle.dma_icr & 0x7f000000u) == 0) {
                g_hle.irq_data &= ~0x08u;
                g_hle.ack_pending &= ~0x08u;
                psx_irq_update();
            }
        }
            break;
    }
}

/////////////////////////////////////////////////////////////////////////////
//
// Continuation engine.
//
static void exc_finish(void); // fwd

// Restore the exception-entry register snapshot (everything except PC,
// which the pump/finalizer sets next). the reference's call_irq_routine is atomic:
// each softcall saves regs, runs, and rewinds. Our continuation must do
// the same so the next softcall / the entry_int finalizer starts from the
// clean interrupted state, not the handler's clobbered registers.
static void restore_snapshot(void) {
    int i;
    for (i = 0; i < 32; i++) SETR(i, g_hle.irq_regs[i]);
    SET(CPUINFO_INT_REGISTER + MIPS_HI, g_hle.irq_regs[32]);
    SET(CPUINFO_INT_REGISTER + MIPS_LO, g_hle.irq_regs[33]);
    SET(CPUINFO_INT_REGISTER + MIPS_DELAYV, g_hle.irq_regs[35]);
    SET(CPUINFO_INT_REGISTER + MIPS_DELAYR, g_hle.irq_regs[36]);
}

static void softcall_arm(uint32 func, uint32 arg) {
    int n = (g_hle.sc_tail + 1) % SOFTCALL_FIFO;
    if (n == g_hle.sc_head) {
        TRACE("[hle] softcall FIFO overflow\n");
        return;
    }
    g_hle.sc_func[g_hle.sc_tail] = func;
    g_hle.sc_arg[g_hle.sc_tail] = arg;
    g_hle.sc_tail = n;
}

// Either dispatch the next armed softcall (return to the outer loop running
// it, $ra=SOFTCALL_RA) or, when the FIFO drains, run the finalizer.
static void softcall_pump(void) {
    if (g_hle.sc_head != g_hle.sc_tail) {
        uint32 func = g_hle.sc_func[g_hle.sc_head];
        uint32 arg = g_hle.sc_arg[g_hle.sc_head];
        g_hle.sc_head = (g_hle.sc_head + 1) % SOFTCALL_FIFO;
        SETR(4, arg);                                   // $a0 = parameter
        SET(CPUINFO_INT_REGISTER + MIPS_R31, SOFTCALL_RA); // $ra = trampoline
        hle_set_pc(func);
        TRACE("[hle]   softcall -> %08X(%08X)\n", func, arg);
        return;
    }
    exc_finish();
}

/////////////////////////////////////////////////////////////////////////////
//
// Per-slice hardware advance. Called from HE's iop_advance (HLE mode) so
// the root counters and the SPU-DMA completion run on HE's cycle clock --
// HE keeps owning pacing; we just drive the BIOS-mediated parts. Mirrors
// the reference psx_hw_runcounters (PS1 path only).
//
// PSF2-HLE per-sample pump owns the PS2 counter cadence (the reference
// ps2_hw_slice: psx_hw_runcounters once per output sample). While it is
// active, hle_advance must NOT also tick the counters off the cycle
// accumulator (that would double-count / desync the sequencer).
int EMU_CALL hle_ps2_pump_active(void) { return g_hle.ps2_pump; }

void EMU_CALL hle_ps2_set_pump(int v) { g_hle.ps2_pump = v; }

void EMU_CALL hle_advance(void *iop, uint32 elapse) {
    g_hle.iop = iop;
    g_hle.r3000 = iop_get_r3000_state(iop);
    if (g_hle.ps2 && g_hle.ps2_pump) return;   // pump drives PS2 counters

    {
        static int en = -1;
        if (en < 0) {
            const char *e = getenv("HLE_ADV");
            en = (e && *e != '0');
        }
        if (en) {
            static unsigned long c = 0;
            if ((c++ % 100000) == 0)
                fprintf(stderr, "[adv] #%lu elapse=%u accum=%u ps2=%d\n", c, elapse,
                        g_hle.adv_accum, g_hle.ps2);
        }
    }
    g_hle.adv_accum += elapse;
    while (g_hle.adv_accum >= 768) {
        uint32 intr;
        g_hle.adv_accum -= 768;

        if (!g_hle.intr_susp) {
            if (g_hle.dma4_delay && --g_hle.dma4_delay == 0) dma4_complete();
        }

        intr = ioptimer_advance(RCNT(), 768);
        if (intr && !g_hle.intr_susp) psx_irq_set(intr);

        // PS2: drive the IOP-software timers, thread wait-delays and
        // sys_time, and reschedule (the reference psx_hw_runcounters PS2 path).
        if (g_hle.ps2 && g_hle.ps2_booted) hle_ps2_runcounters(iop);
    }
}

// One output sample (768 IOP cycles) of counter advance for the
// PSF2-HLE per-sample pump. hle_advance early-returns under the pump
// (see "pump drives PS2 counters" above), so the pump must run
// hle_advance's per-768 block itself -- otherwise the HE root counters
// (RCNT, which the game's sequencer busy-polls via hle_timer_lw at
// 0x1F801100/0x1F801480) FREEZE for the whole render. A frozen counter
// makes the sequencer perceive zero elapsed time, so it never waits
// between events and walks the entire song at IOP speed (~7x ahead of
// SPU playback: identical SPU2 writes, ~7x time-compressed -- verified
// vs the BIOS/the reference oracle, 30k vs ~230k samples of spread). This is
// byte-identical to hle_advance's loop body so the pump and non-pump
// counter cadence match exactly. The pump calls this once per sample
// in place of its bare hle_ps2_runcounters call.
void EMU_CALL hle_ps2_pump_tick(void *iop) {
    uint32 intr;
    g_hle.iop = iop;
    g_hle.r3000 = iop_get_r3000_state(iop);
    if (!g_hle.intr_susp) {
        if (g_hle.dma4_delay && --g_hle.dma4_delay == 0) dma4_complete();
    }
    intr = ioptimer_advance(RCNT(), 768);
    if (intr && !g_hle.intr_susp) psx_irq_set(intr);
    if (g_hle.ps2 && g_hle.ps2_booted) hle_ps2_runcounters(iop);
}

/////////////////////////////////////////////////////////////////////////////
//
// PS1 boot. psf1_load() then overrides PC/$sp from the PS-X EXE header.
//
void EMU_CALL hle_init_ps1(void *iop) {
    uint32 *ram;
    int i;

    memset(&g_hle, 0, sizeof(g_hle));
    g_hle.iop = iop;
    g_hle.r3000 = iop_get_r3000_state(iop);
    g_hle.finalizer = FIN_NONE;

    ram = RAM();

    // Sentinel at the call vectors + the softcall trampoline.
    ram[0x0080 / 4] = R3000_HLE_SENTINEL;        // general exception vector
    ram[0x00A0 / 4] = R3000_HLE_SENTINEL;        // A0 table
    ram[0x00B0 / 4] = R3000_HLE_SENTINEL;        // B0 table
    ram[0x00C0 / 4] = R3000_HLE_SENTINEL;        // C0 table
    ram[SOFTCALL_LOW / 4] = R3000_HLE_SENTINEL;  // softcall return
    // A `jal`/`jalr` through a NULL function pointer lands at address 0.
    // On a real PS1 address 0 holds the kernel exception trampoline
    // (lui k0,0 / addiu k0,0xC80 / jr k0) which traps into the BIOS
    // handler and unwinds back to $ra (verified against the BIOS oracle:
    // it returns to the caller with v0=0 and playback continues). Several
    // PSF sound engines do this deliberately and the BIOS tolerates it
    // (e.g. libsnd's SsSeqCalledTbyT path in Crash Team Racing / Metamor
    // Panic). slopsf left address 0 zeroed, so the NULL call nop-slid into
    // the exception sentinel and wedged irq_mutex. Plant `jr ra; nop` so
    // the NULL call returns harmlessly -- the net effect of the real
    // kernel trampoline for this case.
    ram[0x0000 / 4] = 0x03E00008u;               // jr ra
    ram[0x0004 / 4] = 0x00000000u;               // nop (delay slot)

    // Clear the event-control block table.
    for (i = 0; i < (int) (EVENTS_SIZE / 4); i++) ram[(EVENTS_BEGIN / 4) + i] = 0;

    // B0[0x5B] / C0[0x06] point at the HLE exception handler stub region
    // (games fetch these via GetB0Table/GetC0Table; the body is never run --
    // the real exception entry is HE's 0x80000080 -> our sentinel).
    ram[(B0TABLE_BEGIN / 4) + 0x5B] = C0_EXC_BEGIN;
    ram[(C0TABLE_BEGIN / 4) + 0x06] = C0_EXC_BEGIN;

    wr32(LONGJMP_BUFFER, 0);

    // HLE-owned PS1 root counters (the reference psx_hw_init rates, NTSC default).
    if (ioptimer_get_state_size() > sizeof(g_hle.root_cnts))
        fprintf(stderr, "[hle] FATAL: root_cnts buffer too small\n");
    ioptimer_clear_state(RCNT());
    ioptimer_set_rates(RCNT(), 33868800, 429,
                       (g_ps1_refresh == 50) ? 312 : 262,
                       (g_ps1_refresh == 50) ? 240 : 224,
                       g_ps1_refresh);
    g_hle.irq_data = g_hle.irq_mask = 0;
    g_hle.irq_masked = 0;

    r3000_setreg(g_hle.r3000, R3000_REG_PC, 0x80010000);
    r3000_setreg(g_hle.r3000, R3000_REG_GEN + 29, 0x801FFFF0); // $sp
}

/////////////////////////////////////////////////////////////////////////////
//
// PS2 (PSF2) IOP kernel -- ported verbatim in he/hle_ps2.c. These are
// the hardware/host bridges it calls back into.
//
extern sint32 EMU_CALL psx_hle_readfile(void *psx_state, const char *path,
                                        sint32 offset, char *buffer,
                                        sint32 length);

/* hle_ps2_boot / hle_ps2_iop_call / hle_ps2_runcounters: declared in hle.h */

void EMU_CALL hle_ps2_irq_set(uint32 irq) {
    g_hle.irq_data |= irq;
    psx_irq_update();
}

void EMU_CALL hle_ps2_set_refresh(uint32 refresh) { (void) refresh; }

// Re-rate the HLE-owned PS1 root counters for NTSC (60) / PAL (50). Called
// from iop_set_refresh() once the region is known (PSF _refresh tag or the
// PS-X EXE region string), so the PS1 sequencer's VBLANK cadence matches
// the rip's region instead of always running NTSC.
void EMU_CALL hle_ps1_set_refresh(uint32 refresh) {
    if (refresh != 50 && refresh != 60) return;
    g_ps1_refresh = refresh;
    ioptimer_set_rates(RCNT(), 33868800, 429,
                       (refresh == 50) ? 312 : 262,
                       (refresh == 50) ? 240 : 224,
                       refresh);
}

sint32 EMU_CALL hle_ps2_readfile_bridge(const char *path, sint32 ofs,
                                        char *buf, sint32 len) {
    // ofs MUST be honoured: libsd streams the wave bank as sequential
    // 32 KB chunks (ioman read advances filepos and passes it here). The
    // previous hardcoded 0 made every streamed read return the START of
    // the file, so SPU2 sample RAM was filled with the first chunk over
    // and over -> voices played the correct pitch on the wrong instrument
    // (sample data). vfs_readfile supports the offset directly.
    return psx_hle_readfile(iop_get_psx_state(g_hle.iop), path, ofs, buf, len);
}

// PS2 IOP timer/DMA handlers reuse the PS1 continuation softcall queue.
// the reference runs call_irq_routine synchronously (nested mips_execute) so the
// handler completes before the interrupted code resumes. Our equivalent
// runs it at the next r3000 instruction boundary (hle_slice_end) -- so
// break the current slice now: without this the handler waits until the
// natural end of the sound-buffer slice (thousands of cycles later),
// which coalesces/loses the FFXI sequencer's timer ticks and the song
// never advances.
void   EMU_CALL hle_ps1_softcall_arm(uint32 func, uint32 arg) {
    softcall_arm(func, arg);
    if (g_hle.ps2 && g_hle.r3000) r3000_break(g_hle.r3000);
}

sint32 EMU_CALL hle_ps1_softcall_pending(void) { return g_hle.sc_head != g_hle.sc_tail; }
// Is this exact (handler,arg) event already queued or currently running?
// Dedupe by the (func,arg) PAIR, not func alone: DMA4 and DMA7 completion
// share one handler routine but are distinct events (different channel
// arg). Dropping by routine alone loses one of them -> the WD-loader's
// awaited completion never arrives and the wave bank loads incompletely
// (-> "Wave is not Entry" -> silence). A genuine re-trigger of the same
// event (same func+arg, e.g. the sequencer timer) is still coalesced.
sint32 EMU_CALL hle_ps1_softcall_has(uint32 func, uint32 arg) {
    int i;
    if (g_hle.ps2_sc_active && g_hle.ps2_sc_cur_func == func
        && g_hle.ps2_sc_cur_arg == arg)
        return 1;
    for (i = g_hle.sc_head; i != g_hle.sc_tail; i = (i + 1) % SOFTCALL_FIFO)
        if (g_hle.sc_func[i] == func && g_hle.sc_arg[i] == arg) return 1;
    return 0;
}

void EMU_CALL hle_init_ps2(void *iop) {
    memset(&g_hle, 0, sizeof(g_hle));
    g_hle.iop = iop;
    g_hle.r3000 = iop_get_r3000_state(iop);
    g_hle.ps2 = 1;
    g_hle.ps2_booted = 0;
    // IOP work RAM is cleared by iop_clear_state. Park the CPU on the
    // sentinel at RAM 0; the first dispatch performs the deferred boot
    // (readfile is only registered after psx_clear_state).
    RAM()[0] = R3000_HLE_SENTINEL;
    RAM()[SOFTCALL_LOW / 4] = R3000_HLE_SENTINEL; // IOP IRQ/DMA-handler $ra
    // Root counters MUST be initialised: ioptimer_advance() infinite-loops
    // on a zeroed state (cycles_until_gate()==0 -> cycles_left never
    // decrements). the reference psx_hw_init v2 rates.
    ioptimer_clear_state(RCNT());
    ioptimer_set_rates(RCNT(), 36864000, 858, 262, 224, 60);
    g_hle.irq_data = g_hle.irq_mask = 0;
    g_hle.irq_masked = 0;
    r3000_setreg(g_hle.r3000, R3000_REG_PC, 0x80000000);
    r3000_setreg(g_hle.r3000, R3000_REG_GEN + 29, 0x801FFFF0);
}

/////////////////////////////////////////////////////////////////////////////
//
// Synchronous BIOS calls (A0/B0/C0). Ported verbatim in behaviour from
// the reference psx_bios_hle; psx->psx_ram[x] -> rd32/wr32, mips_*_info -> GET/SET.
//
static void bios_call(uint32 vec) {
    uint32 subcall = R(9) & 0xff;       // $t1
    uint32 a0 = R(4), a1 = R(5), a2 = R(6), a3 = R(7);
    int i;

    switch (vec) {
        case 0xA0:
            switch (subcall) {
                case 0x13: // setjmp
                    wr32(a0 + 0, R(31));
                    wr32(a0 + 4, R(29));
                    wr32(a0 + 8, R(30));
                    for (i = 0; i < 8; i++) wr32(a0 + 12 + i * 4, R(16 + i));
                    wr32(a0 + 44, R(28));
                    SETR(2, 0);
                    break;
                case 0x14: // longjmp
                    SETR(31, rd32(a0 + 0));
                    SETR(29, rd32(a0 + 4));
                    SETR(30, rd32(a0 + 8));
                    for (i = 0; i < 8; i++) SETR(16 + i, rd32(a0 + 12 + i * 4));
                    SETR(28, rd32(a0 + 44));
                    SETR(2, a1);
                    break;
                case 0x18: // strncmp
                    SETR(2, (uint32) strncmp((char *) RAMB(a0), (char *) RAMB(a1), a2));
                    break;
                case 0x19: { // strcpy
                    uint8 *d = RAMB(a0), *s = RAMB(a1);
                    while (*s) { *d++ = *s++; }
                    *d = 0;
                    SETR(2, a0);
                }
                    break;
                case 0x28: // bzero
                    memset(RAMB(a0), 0, a1);
                    break;
                case 0x2a: { // memcpy
                    uint8 *d = RAMB(a0), *s = RAMB(a1);
                    uint32 n = a2;
                    while (n--) *d++ = *s++;
                    SETR(2, a0);
                }
                    break;
                case 0x2b: { // memset
                    memset(RAMB(a0), (int) a1, a2);
                    SETR(2, a0);
                }
                    break;
                case 0x2f: // rand
                    SETR(2, 1 + (uint32)(32767.0 * rand() / (RAND_MAX + 1.0)));
                    break;
                case 0x30: // srand
                    srand(a0);
                    break;
                case 0x33: { // malloc
                    uint32 chunk, fd, size = a0;
                    if (size & 15) {
                        size &= ~15u;
                        size += 16;
                    }
                    chunk = g_hle.heap_addr;
                    while ((size > rd32(chunk + BLK_SIZE)) || (rd32(chunk + BLK_STAT) == 1))
                        chunk = rd32(chunk + BLK_FD);
                    fd = chunk + 16 + size;
                    wr32(fd + BLK_STAT, rd32(chunk + BLK_STAT));
                    wr32(fd + BLK_SIZE, rd32(chunk + BLK_SIZE) - size - 16);
                    wr32(fd + BLK_FD, rd32(chunk + BLK_FD));
                    wr32(fd + BLK_BK, chunk);
                    wr32(chunk + BLK_STAT, 1);
                    wr32(chunk + BLK_SIZE, size);
                    wr32(chunk + BLK_FD, fd);
                    SETR(2, (chunk + 16) | 0x80000000);
                }
                    break;
                case 0x34: { // free
                    uint32 chunk = (a0 & 0x1fffff) - 16, size, fd, lastfd = 0;
                    if (rd32(chunk + BLK_STAT) != 1) {
                        SETR(2, 0xffffffff);
                        break;
                    }
                    size = rd32(chunk + BLK_SIZE);
                    fd = rd32(chunk + BLK_FD);
                    while (fd && rd32(fd + BLK_STAT) != 1) {
                        size += rd32(fd + BLK_SIZE) + 16;
                        lastfd = fd;
                        fd = rd32(fd + BLK_FD);
                    }
                    wr32(chunk + BLK_SIZE, size);
                    wr32(chunk + BLK_FD, lastfd);
                    wr32(chunk + BLK_STAT, 0);
                    if (lastfd) wr32(lastfd + BLK_BK, chunk);
                    SETR(2, 0);
                }
                    break;
                case 0x39: // InitHeap
                    if (a0 & 15) {
                        a1 -= 16 - (a0 & 15);
                        a0 &= ~15u;
                        a0 += 16;
                    }
                    if (a1 >= 16) a1 -= 16;
                    if (a1 & 15) a1 &= ~15u;
                    g_hle.heap_addr = a0 & 0x3fffffff;
                    /* Real BIOS makes ZERO writes here: empirically
                    ** verified by widening a CPU store-watchpoint over
                    ** 0x80020000..0x8002001F on the BIOS oracle (hepsf),
                    ** which shows no writes during InitHeap. slopsf
                    ** inherited aopsf's eager pre-write of the chunk
                    ** metadata, which OVERWRITES whatever the PSF loader
                    ** has already placed at heap_addr. The libsnd software
                    ** streamer family (Metamor Panic + 6 silent
                    ** regressions: Riot Stars, Heroine Dream 2, Nekketsu
                    ** Oyako, Sengoku Mugen, Bomberman Party Edition,
                    ** Cotton 100%) calls InitHeap(0x8001FFFC, ...) which
                    ** aligns up to 0x80020000 -- exactly on top of the
                    ** loaded SEQ header ("pQES" magic + PPQN + tempo).
                    ** Clobbering the magic took the driver's track-init
                    ** down the wrong branch (seq_ptr off by 8 bytes), and
                    ** the per-tick scheduler then never dispatched any
                    ** notes -- pure silence on 6 games, 15dB-quieter
                    ** init-notes-only on Metamor.
                    **
                    ** Fix: only pre-write the chunk header if the region
                    ** is virgin (all zero) -- means no PSF/loader data
                    ** lives there. Games whose heap lands on already-
                    ** loaded RAM keep the data; games whose heap lands
                    ** on virgin RAM still get a usable chunk header for
                    ** the existing HLE malloc. Verified clean against 30
                    ** randomly-sampled previously-OK games (|delta| <
                    ** 0.07 dB vs BIOS, no regressions). */
                    {
                        uint32 b0 = rd32(g_hle.heap_addr + BLK_STAT);
                        uint32 b1 = rd32(g_hle.heap_addr + BLK_SIZE);
                        uint32 b2 = rd32(g_hle.heap_addr + BLK_FD);
                        uint32 b3 = rd32(g_hle.heap_addr + BLK_BK);
                        if ((b0 | b1 | b2 | b3) == 0) {
                            wr32(g_hle.heap_addr + BLK_STAT, 0);
                            wr32(g_hle.heap_addr + BLK_FD, 0);
                            wr32(g_hle.heap_addr + BLK_BK, 0);
                            if (((a0 & 0x1fffff) + a1) >= 2 * 1024 * 1024)
                                wr32(g_hle.heap_addr + BLK_SIZE,
                                     0x1ffffc - (a0 & 0x1fffff));
                            else
                                wr32(g_hle.heap_addr + BLK_SIZE, a1);
                        }
                    }
                    break;
                case 0x3f: // printf
                case 0x44: // FlushCache
                case 0x70: // bu_init
                case 0x72: // __96_remove
                    break;
                default:
                    TRACE("[hle] unknown A0 call %02X\n", subcall);
                    break;
            }
            break;

        case 0xB0:
            switch (subcall) {
                case 0x07: { // DeliverEvent
                    uint32 dv = 0; // BIOS leaves the matched event's mode in $v0; the
                    // VP DMA-wait loop spins until this is non-zero.
                    if (g_hle.eventsAllocated) {
                        for (i = 0; i < MAX_EVENT; i++) {
                            if (!EVT(i, EV_ISVALID)) continue;
                            if (EVT(i, EV_CLASSID) != a0) continue;
                            dv = EVT(i, EV_MODE);
                            if (!EVT(i, EV_ENABLED)) continue;
                            EVT(i, EV_FIRED) = 1;
                            if (EVT(i, EV_FUNC)) softcall_arm(EVT(i, EV_FUNC), 0);
                        }
                        SETR(2, dv);
                        // Run any armed handlers, then return to $ra.
                        if (g_hle.sc_head != g_hle.sc_tail) {
                            g_hle.finalizer = FIN_RESTORE;
                            // snapshot caller so FIN_RESTORE returns cleanly to $ra
                            for (i = 0; i < 32; i++) g_hle.irq_regs[i] = R(i);
                            g_hle.irq_regs[32] = GET(CPUINFO_INT_REGISTER + MIPS_HI);
                            g_hle.irq_regs[33] = GET(CPUINFO_INT_REGISTER + MIPS_LO);
                            g_hle.irq_regs[34] = R(31); // resume at caller's $ra
                            g_hle.irq_regs[35] = GET(CPUINFO_INT_REGISTER + MIPS_DELAYV);
                            g_hle.irq_regs[36] = GET(CPUINFO_INT_REGISTER + MIPS_DELAYR);
                            g_hle.in_exception = 1;
                            softcall_pump();
                            return;
                        }
                    }
                }
                    break;
                case 0x08: { // OpenEvent
                    uint32 eventId = 0xffffffff;
                    if (g_hle.eventsAllocated == MAX_EVENT) {
                        TRACE("[hle] too many events\n");
                        break;
                    }
                    g_hle.eventsAllocated++;
                    for (i = 0; i < MAX_EVENT; i++)
                        if (!EVT(i, EV_ISVALID)) {
                            eventId = i;
                            break;
                        }
                    EVT(eventId, EV_ISVALID) = 1;
                    EVT(eventId, EV_CLASSID) = a0;
                    EVT(eventId, EV_SPEC) = a1;
                    EVT(eventId, EV_MODE) = a2;
                    EVT(eventId, EV_FUNC) = a3;
                    EVT(eventId, EV_FIRED) = 0;
                    TRACE("[hle] OpenEvent id=%u class=%08X spec=%08X mode=%08X func=%08X\n",
                          eventId, a0, a1, a2, a3);
                    SETR(2, eventId + 1);
                }
                    break;
                case 0x0a: { // WaitEvent
                    uint32 ev = a0 - 1;
                    if (ev < MAX_EVENT && EVT(ev, EV_ISVALID) && EVT(ev, EV_ENABLED) &&
                        !EVT(ev, EV_FIRED)) {
                        g_hle.WAI = 1;
                        r3000_break(g_hle.r3000);   // cooperative yield
                    }
                    SETR(2, 1);
                }
                    break;
                case 0x0b: { // TestEvent
                    uint32 ev = a0 - 1, v = (ev < MAX_EVENT && EVT(ev, EV_ISVALID)) ? EVT(ev,
                                                                                          EV_FIRED)
                                                                                    : 0;
                    if (v && ev < MAX_EVENT) EVT(ev, EV_FIRED) = 0;
                    SETR(2, v);
                    SETR(3, v); // Crash 2/3 rely on v1 too
                }
                    break;
                case 0x0c: { // EnableEvent
                    uint32 ev = a0 - 1;
                    if (ev < MAX_EVENT && EVT(ev, EV_ISVALID)) {
                        EVT(ev, EV_ENABLED) = 1;
                        EVT(ev, EV_FIRED) = 0;
                    }
                    SETR(2, 1);
                }
                    break;
                case 0x0d: { // DisableEvent
                    uint32 ev = a0 - 1;
                    if (ev < MAX_EVENT && EVT(ev, EV_ISVALID)) EVT(ev, EV_ENABLED) = 0;
                    SETR(2, 1);
                }
                    break;
                case 0x17: { // ReturnFromException
                    uint32 status;
                    // The BIOS dispatcher acks the IRQ lines it serviced now, after
                    // the (custom) handler chain has run and seen the cause.
                    if (g_hle.ack_pending) {
                        r3000_sw(g_hle.r3000, 0x1f801070, ~g_hle.ack_pending);
                        g_hle.ack_pending = 0;
                    }
                    // Re-deliver one RootCounter period coalesced away while
                    // this handler ran (game-hooked path only -- the VP
                    // 0xF2000002 path acks immediately and never gets here
                    // with rcnt_missed set). Re-asserting after the context
                    // restore makes the resumed code take the exception
                    // again at once, ticking the game's sequencer once per
                    // missed period -- restoring real tempo (Persona 1).
                    if (!g_hle.rcnt_event_present && g_hle.rcnt_missed) {
                        uint32 rb = g_hle.rcnt_missed_bits ? g_hle.rcnt_missed_bits
                                                           : 0x40;
                        g_hle.rcnt_missed--;
                        if (!g_hle.rcnt_missed) g_hle.rcnt_missed_bits = 0;
                        psx_irq_set(rb);
                    }
                    for (i = 0; i < 32; i++) SETR(i, g_hle.irq_regs[i]);
                    SET(CPUINFO_INT_REGISTER + MIPS_HI, g_hle.irq_regs[32]);
                    SET(CPUINFO_INT_REGISTER + MIPS_LO, g_hle.irq_regs[33]);
                    SET(CPUINFO_INT_REGISTER + MIPS_DELAYV, g_hle.irq_regs[35]);
                    SET(CPUINFO_INT_REGISTER + MIPS_DELAYR, g_hle.irq_regs[36]);
                    status = mips_get_status(CPU());
                    status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
                    mips_set_status(CPU(), status);
                    g_hle.in_exception = 0;
                    g_hle.irq_mutex = 0;
                    hle_set_pc(g_hle.irq_regs[34]); // ePC was stashed here on entry
                    return; // do not fall through to PC=$ra
                }
                case 0x19: // HookEntryInt
                    wr32(LONGJMP_BUFFER, a0);
                    break;
                case 0x3f: // puts
                case 0x5b: // ChangeClearPAD
                case 0x4a: // InitCard
                case 0x4b: // StartCard
                    break;
                case 0x56: // GetC0Table
                    SETR(2, C0TABLE_BEGIN);
                    break;
                case 0x57: // GetB0Table
                    SETR(2, B0TABLE_BEGIN);
                    break;
                default:
                    TRACE("[hle] unknown B0 call %02X\n", subcall);
                    break;
            }
            break;

        case 0xC0:
            switch (subcall) {
                case 0x0a: // ChangeClearRCnt
                    SETR(2, rd32((a0 << 2) + 0x8600));
                    wr32((a0 << 2) + 0x8600, a1);
                    break;
                default:
                    TRACE("[hle] unknown C0 call %02X\n", subcall);
                    break;
            }
            break;
    }

    // Default return: PC = $ra.
    hle_set_pc(R(31));
}

/////////////////////////////////////////////////////////////////////////////
//
// Exception path. HE delivers a hardware interrupt by vectoring the CPU to
// 0x80000080 (our sentinel). cause&0x3c: 0 = IRQ, 0x20 = syscall.
//
static void exc_begin(void) {
    MIPS_CPU_CONTEXT *c = CPU();
    uint32 cause = mips_get_cause(c) & 0x3c;
    int i;

    if (cause == 0x20) { // syscall: EnterCritical / ExitCritical
        uint32 status = mips_get_status(c);
        uint32 a0 = R(4);
        if (a0 == 1) {                          // EnterCritical
            status &= ~0x0404u;
            SETR(2, 1);                          // $v0 = "interrupts were enabled"
        } else if (a0 == 2) {                   // ExitCritical
            status |= 0x0404u;
            SETR(2, 0);
        }
        status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
        mips_set_status(c, status);
        hle_set_pc(mips_get_ePC(c) + 4);
        return;
    }
    if (cause == 0x24) { // break (ExcCode 9): the BIOS swallows the trap
        // and returns past it. Restore the status stack (RFE-equivalent,
        // exactly as the syscall path) and resume after the break, so
        // SDK assert/trap `break`s don't wedge the driver.
        uint32 status = mips_get_status(c);
        status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
        mips_set_status(c, status);
        hle_set_pc(mips_get_ePC(c) + 4);
        return;
    }
    if (cause != 0) {                          // unknown: just resume
        hle_set_pc(mips_get_ePC(c));
        return;
    }

    // ---- IRQ ----
    g_hle.WAI = 0;
    if (g_hle.irq_mutex) {
        TRACE("[hle] IRQ reentry\n");
        hle_set_pc(mips_get_ePC(c));
        return;
    }
    g_hle.irq_mutex = 1;
    g_hle.in_exception = 1;

    // snapshot pre-exception registers
    for (i = 0; i < 32; i++) g_hle.irq_regs[i] = R(i);
    g_hle.irq_regs[32] = GET(CPUINFO_INT_REGISTER + MIPS_HI);
    g_hle.irq_regs[33] = GET(CPUINFO_INT_REGISTER + MIPS_LO);
    g_hle.irq_regs[34] = mips_get_ePC(c);
    g_hle.irq_regs[35] = GET(CPUINFO_INT_REGISTER + MIPS_DELAYV);
    g_hle.irq_regs[36] = GET(CPUINFO_INT_REGISTER + MIPS_DELAYR);

    // Which HE hardware IRQs are pending (read through HE's interrupt
    // controller at 0x1f801070)? Fire the matching BIOS RootCounter/DMA
    // events, then ack. Bit mapping vs the BIOS event model is the
    // documented iterative tuning point.
    {
        // PS1 I_STAT (HE is PS1-faithful -- the real BIOS depends on it):
        // bits 4..6 (0x70) are the root counters, bit 3 (0x08) is DMA. the reference
        // fires RootCounter-class events on 0x70 and DMA-class on the DMA line;
        // it does NOT fire them on VBLANK/GPU/etc.
        uint32 sig = r3000_lw(g_hle.r3000, 0x1f801070);
        uint32 entryint = rd32(LONGJMP_BUFFER);
        uint32 handled = 0;
        int fired = 0;
        // the reference psx_bios_exception, faithfully: on a RootCounter IRQ
        // (sig & 0x70) deliver the 0xF2000002 events -- this is the music
        // sequencer (8001463C) -- then clear the RCnt I_STAT bits
        // (the reference: irq_data &= ~0x70). This happens whether or not a custom
        // handler is hooked (HookEntryInt chains; it does not replace BIOS
        // event delivery).
        if ((sig & 0x70) && g_hle.eventsAllocated) {
            // aopsf psx_bios_exception: needClearInt is set iff a BIOS
            // RootCounter event (class 0xF2000002) is present; only then
            // does it consume the RCnt cause (irq_data &= ~0x70). Games
            // like Valkyrie Profile / FF7 / FFT / Xenogears run their
            // sequencer off this 0xF2000002 (EvMdINTR) softcall.
            int needClear = 0;
            for (i = 0; i < MAX_EVENT; i++) {
                if (!EVT(i, EV_ISVALID) || !IS_RCNT_CLASS(EVT(i, EV_CLASSID))) continue;
                needClear = 1;
                if (!EVT(i, EV_ENABLED)) continue;
                EVT(i, EV_FIRED) = 1;
                if (EVT(i, EV_FUNC)) {
                    // One softcall for this period, plus one for each
                    // period coalesced away while the previous softcall
                    // sequencer was mid-flight (psx_irq_set's idempotent
                    // |= dropped them). Without this the 0xF2000002
                    // sequencer ticks once per ~2 real timer periods --
                    // FF7/FFT/Xenogears/Valkyrie Profile ran ~half tempo.
                    uint32 extra = g_hle.rcnt_missed, e;
                    softcall_arm(EVT(i, EV_FUNC), 0);
                    fired++;
                    for (e = 0; e < extra; e++)
                        softcall_arm(EVT(i, EV_FUNC), 0);
                }
            }
            // Remember which path serviced the RCnt cause: the 0xF2000002
            // path re-delivers via the extra softcall arms above (and
            // resets the counter here); the entry_int path re-delivers at
            // ReturnFromException, gated on !rcnt_event_present so the two
            // mechanisms never double-fire.
            g_hle.rcnt_event_present = needClear;
            if (needClear) {
                g_hle.rcnt_missed = 0;
                g_hle.rcnt_missed_bits = 0;
                handled |= (sig & 0x70);
                r3000_sw(g_hle.r3000, 0x1f801070, ~(sig & 0x70)); // the reference: irq_data &= ~0x70
            } else if (entryint) {
                // No BIOS RootCounter event: the game drives its sequencer
                // from its OWN hooked entry_int handler + EvMdNOINTR flag
                // events (e.g. Persona 1, classes 0xF4000001/0xF0000011).
                // Faithful to aopsf (needClearInt==0): do NOT consume the
                // cause here -- leave it pending so the handler sees the
                // RootCounter, and defer the ack to ReturnFromException
                // (slopsf's ack_pending), exactly as the BIOS dispatcher
                // does after the custom handler chain has run.
                g_hle.ack_pending |= (sig & 0x70);
                handled |= (sig & 0x70);
            } else {
                // No event and no entry_int handler to see/ack it: clear
                // now to avoid a RootCounter IRQ storm (slightly safer
                // than aopsf, which would never clear here).
                handled |= (sig & 0x70);
                r3000_sw(g_hle.r3000, 0x1f801070, ~(sig & 0x70));
            }
        }
        // VBLANK (I_STAT bit 0) drives RootCounter-3 / VSync events:
        // class 0xF2000003, EvMdINTR (callback). Gran Turismo
        // (arcade.psf) runs its sequencer SOLELY off a 0xF2000003 VBLANK
        // callback (func 8001019C) and was pure silence without this.
        //
        // This is tightly scoped: deliver VSync ONLY when there is no
        // 0xF2000002 RootCounter event AND no hooked entry_int handler --
        // i.e. VBLANK is the game's sole timing source. Most games open a
        // 0xF2000003 VSync event for housekeeping while running the real
        // sequencer off 0xF2000002 RootCounters or their own entry_int;
        // touching VBLANK for those wedged ~85 games (Megaman8 /
        // MetalSlugX / CrashBandicoot / Persona went silent). Outside this
        // narrow case nothing about VBLANK/other I_STAT bits changes, so
        // every previously-working title is byte-for-byte unaffected.
        if ((sig & 0x01) && g_hle.eventsAllocated && !entryint) {
            int has_rcnt = 0, vsynced = 0;
            for (i = 0; i < MAX_EVENT; i++)
                if (EVT(i, EV_ISVALID) && IS_RCNT_CLASS(EVT(i, EV_CLASSID))) {
                    has_rcnt = 1;
                    break;
                }
            if (!has_rcnt) {
                for (i = 0; i < MAX_EVENT; i++) {
                    if (!EVT(i, EV_ISVALID) ||
                        EVT(i, EV_CLASSID) != CLASS_VSYNC)
                        continue;
                    vsynced = 1;
                    if (!EVT(i, EV_ENABLED)) continue;
                    EVT(i, EV_FIRED) = 1;
                    if (EVT(i, EV_FUNC)) {
                        softcall_arm(EVT(i, EV_FUNC), 0);
                        fired++;
                    }
                }
                // Ack VBLANK only here (the GT-style sole-VSync case), so
                // the callback isn't re-stormed. Games that don't take
                // this branch keep VBLANK exactly as before (untouched).
                if (vsynced) {
                    handled |= (sig & 0x01);
                    r3000_sw(g_hle.r3000, 0x1f801070, ~(sig & 0x01));
                }
            }
        }
        // DMA (0x08): the entry_int handler runs its own SPU-RAM ISR and
        // W1C-acks DICR (which deasserts I_STAT bit 3 -- see hle_dma_sw). Do
        // NOT pre-ack it here, or the handler can't see the cause.
        (void) entryint;
        TRACE("[hle] IRQ I_STAT=%08X handled=%08X events_fired=%d entryint=%08X\n",
              sig, handled, fired, entryint);
    }

    g_hle.finalizer = rd32(LONGJMP_BUFFER) ? FIN_ENTRYINT : FIN_RESTORE;
    softcall_pump();
}

// Run once the armed-softcall FIFO drains.
static void exc_finish(void) {
    MIPS_CPU_CONTEXT *c = CPU();
    int i;

    if (g_hle.finalizer == FIN_ENTRYINT) {
        uint32 buf = rd32(LONGJMP_BUFFER);
        // entry_int: restore context block, v0=1, resume at its RA/PC. The
        // driver handler runs and eventually calls ReturnFromException (B0
        // 0x17), which restores irq_regs.
        uint32 ra = rd32(buf + 0);
        SETR(31, ra);
        SETR(29, rd32(buf + 4));
        SETR(30, rd32(buf + 8));
        for (i = 0; i < 8; i++) SETR(16 + i, rd32(buf + 12 + i * 4));
        SETR(28, rd32(buf + 44));
        SETR(2, 1);
        g_hle.finalizer = FIN_NONE;
        TRACE("[hle] entry_int buf=%08X ra=%08X sp=%08X gp=%08X\n",
              buf, ra, rd32(buf + 4), rd32(buf + 44));
        hle_set_pc(ra);
        return;
    }

    // FIN_RESTORE: no entry_int -- restore the saved snapshot ourselves
    // (the reference's no-entry_int tail). This is the ONLY place a restore
    // happens; the softcall path deliberately does not (see hle_dispatch).
    restore_snapshot();
    if (g_hle.in_exception) {
        uint32 status = mips_get_status(c);
        status = (status & 0xfffffff0) | ((status & 0x3c) >> 2);
        mips_set_status(c, status);
    }
    g_hle.finalizer = FIN_NONE;
    g_hle.in_exception = 0;
    g_hle.irq_mutex = 0;
    hle_set_pc(g_hle.irq_regs[34]);
}

/////////////////////////////////////////////////////////////////////////////
//
// PS2 IOP-kernel call (Phase 2). the reference psx_iop_call: resolve the calling
// library by scanning IOP RAM backwards from pc for the import-table
// signature 0x41E00000, read the 8-char lib name, dispatch by
// name+callnum. This foundation does the resolution + tracing; the
// per-module handlers (sysmem/loadcore/intrman/threadman/sysclib/ioman/
// modload/sifcmd/...) are the remaining Phase 2 bulk.
//
sint32 EMU_CALL hle_iop_call(void *r3000, void *iop, uint32 callnum) {
    uint32 pc, resume;
    g_hle.iop = iop;
    g_hle.r3000 = r3000;
    if (!g_hle.ps2 || !g_hle.ps2_booted) return 0; // PS1 / pre-boot: not a stub
    pc = r3000_getreg(r3000, R3000_REG_PC);
    resume = hle_ps2_iop_call(iop, pc, callnum);
    // r3000.c caseMAJOR(0x09) does PC+=4 next; hle_ps2_iop_call returns the
    // value to set so that lands on the intended instruction. resume==pc
    // means a plain kernel call / non-stub (no PC change -> PC+=4 advances
    // past the stub, normal delay-slot behaviour preserved). resume!=pc
    // means a redirect (module entry / thread switch) -> r3000.c cancels
    // the pending delay-slot branch.
    r3000_setreg(r3000, R3000_REG_PC, resume);
    return (resume != pc) ? 1 : 0;
}

// Snapshot/restore the full CPU register file (incl. PC + delay slot)
// for a PS2 IOP IRQ/DMA-handler softcall (run between threads).
static void ps2_sc_snapshot(void) {
    int i;
    for (i = 0; i < 32; i++) g_hle.ps2_sc_regs[i] = r3000_getreg(g_hle.r3000, R3000_REG_GEN + i);
    g_hle.ps2_sc_regs[32] = r3000_getreg(g_hle.r3000, R3000_REG_HI);
    g_hle.ps2_sc_regs[33] = r3000_getreg(g_hle.r3000, R3000_REG_LO);
    g_hle.ps2_sc_regs[34] = r3000_getreg(g_hle.r3000, R3000_REG_PC);
    g_hle.ps2_sc_regs[35] = r3000_getreg(g_hle.r3000, R3000_REG_DELAYV);
    g_hle.ps2_sc_regs[36] = r3000_getreg(g_hle.r3000, R3000_REG_DELAY);
}

static void ps2_sc_restore(void) {
    int i;
    for (i = 0; i < 32; i++) r3000_setreg(g_hle.r3000, R3000_REG_GEN + i, g_hle.ps2_sc_regs[i]);
    r3000_setreg(g_hle.r3000, R3000_REG_HI, g_hle.ps2_sc_regs[32]);
    r3000_setreg(g_hle.r3000, R3000_REG_LO, g_hle.ps2_sc_regs[33]);
    r3000_setreg(g_hle.r3000, R3000_REG_DELAYV, g_hle.ps2_sc_regs[35]);
    r3000_setreg(g_hle.r3000, R3000_REG_DELAY, g_hle.ps2_sc_regs[36]);
    r3000_setreg(g_hle.r3000, R3000_REG_PC, g_hle.ps2_sc_regs[34]);
}

// Start the next queued PS2 IOP IRQ/DMA handler on the CPU. Returns 1 if
// one was launched (PC now = handler, $ra = SOFTCALL_RA trampoline).
static int ps2_sc_launch(void) {
    uint32 func, arg;
    if (g_hle.sc_head == g_hle.sc_tail) return 0;
    func = g_hle.sc_func[g_hle.sc_head];
    arg = g_hle.sc_arg[g_hle.sc_head];
    g_hle.sc_head = (g_hle.sc_head + 1) % SOFTCALL_FIFO;
    g_hle.ps2_sc_cur_func = func;
    g_hle.ps2_sc_cur_arg = arg;
    r3000_setreg(g_hle.r3000, R3000_REG_GEN + 4, arg);          // $a0
    r3000_setreg(g_hle.r3000, R3000_REG_GEN + 31, SOFTCALL_RA);  // $ra
    r3000_setreg(g_hle.r3000, R3000_REG_DELAY, 0);               // no pending branch
    r3000_setreg(g_hle.r3000, R3000_REG_PC, func);
    TRACE("[hle] ps2 IOP-handler -> %08X(%08X)\n", func, arg);
    return 1;
}

// True while hle_slice_end is synchronously draining handlers (nested
// r3000_execute). iop.c freezes the sound/time clock during this so the
// handler burst is time-neutral (the reference saved/restored-icount equivalent).
int EMU_CALL hle_ps2_in_sync_drain(void) { return g_hle.ps2_sc_sync; }

// Safe instruction-boundary hook (end of an r3000 execute slice). Run
// any queued IOP IRQ/DMA handlers (SPU2-DMA/timer -> SignalSema/Wakeup),
// then apply a deferred cooperative reschedule. Never mid lw/sw.
void EMU_CALL hle_slice_end(void *r3000, void *iop) {
    uint32 t;
    if (!g_hle.ps2 || !g_hle.ps2_booted) return;
    g_hle.iop = iop;
    g_hle.r3000 = r3000;
    {
        static int n = 0;
        if (getenv("HLE_DMA") && g_hle.sc_head != g_hle.sc_tail && n++ < 12)
            fprintf(stderr, "[sc] slice_end entry: head=%d tail=%d sc_active=%d\n",
                    g_hle.sc_head, g_hle.sc_tail, g_hle.ps2_sc_active);
    }

    // Re-entrant guard: while synchronously draining handlers, the nested
    // r3000_execute reaches its own finishing_sync and re-enters here --
    // do nothing (the outer drain loop owns the queue).
    if (g_hle.ps2_sc_sync) return;
    if (g_hle.ps2_sc_active) return;   // shouldn't happen with the sync drain

    // Pending IOP IRQ/DMA/timer handler(s)? Run them SYNCHRONOUSLY and
    // ATOMICALLY here, exactly like the reference's nested mips_execute: snapshot
    // the interrupted context, then for each queued handler launch it and
    // spin a NESTED r3000_execute until it returns to SOFTCALL_RA. This is
    // required because libsd's WD-stream ISR re-kicks the next 4 KB
    // sub-block (libsd fno17) from *inside* the handler and depends on
    // that running atomically -- deferred execution leaves the
    // [0x2D77C+core]/busy-flag handshake inconsistent and the streaming
    // chain deadlocks after one sub-block (wave bank never finishes
    // loading -> silence). hle_slice_end runs at finishing_sync (the
    // outer r3000_execute's tail, just before it returns 0), so a nested
    // r3000_execute here is safe.
    // Run exactly ONE queued handler per slice_end, synchronously. the reference
    // services one call_irq_routine per psx_hw_runcounters (sample) tick,
    // naturally spreading IRQ/DMA/timer handlers across time. Draining the
    // whole FIFO here instead bunched all handlers into one instant
    // followed by a time gap -> the SPU2 ADMA stream underran between
    // bursts (audibly glitchy). r3000_break-on-arm keeps slice_ends
    // frequent so the remaining queued handlers are serviced promptly on
    // the following ticks (and the WD-stream chain still advances: each
    // sub-block ISR kicks the next, whose completion is queued for a
    // later tick exactly as on hardware).
    if (g_hle.sc_head != g_hle.sc_tail) {
        uint32 guard = 0;
        {
            static unsigned long long drains = 0, instr = 0;
            if (getenv("SCLOG")) {
                unsigned long long o0 = 0;
                (void) o0;
                drains++;
                if ((drains % 500ULL) == 0)
                    fprintf(stderr, "[scdrain] count=%llu (handlers synchronously drained)\n",
                            drains);
            }
        }
        ps2_sc_snapshot();
        hle_ps2_set_irq_mutex(1);   // no cooperative reschedule under the handler
        g_hle.ps2_sc_sync = 1;
        g_hle.ps2_sc_active = 1;
        ps2_sc_launch();                         // PC=func, $ra=SOFTCALL_RA, $a0=arg
        while (g_hle.ps2_sc_active && guard++ < 4000000u)
            r3000_execute(r3000, 64);              // hle_dispatch clears active + breaks
        g_hle.ps2_sc_active = 0;                 // safety on guard timeout
        g_hle.ps2_sc_sync = 0;
        ps2_sc_restore();
        hle_ps2_set_irq_mutex(0);
        /* fall through to the deferred cooperative reschedule */
    }

    t = hle_ps2_check_resched(iop);
    if (t != 0xffffffffu) r3000_setreg(r3000, R3000_REG_PC, t);
}

/////////////////////////////////////////////////////////////////////////////
//
// Sentinel dispatch. Entered from r3000.c caseMINOR(R3000_HLE_FUNCT).
//
sint32 EMU_CALL hle_dispatch(void *r3000, void *iop) {
    uint32 pc, low;

    g_hle.iop = iop;
    g_hle.r3000 = r3000;

    pc = r3000_getreg(r3000, R3000_REG_PC);
    low = pc & 0x1FFFFF;

    if (pc == 0 || pc == 0x80000000) {
        if (g_hle.ps2 && !g_hle.ps2_booted) {  // deferred PSF2 boot
            uint32 e = hle_ps2_boot(iop);
            if (e == 0xffffffffu) {
                iop_request_quit(iop);
                return 0;
            }
            g_hle.ps2_booted = 1;
            // Plant the idle-spin stub (beq $0,$0,-1 / nop) now that IOP RAM
            // exists and iop_clear_state has run.
            RAM()[PS2_IDLE_PC / 4] = 0x1000FFFFu;
            RAM()[(PS2_IDLE_PC + 4) / 4] = 0x00000000u;
            hle_set_pc(e);                      // entry; -4 for the post-case +4
            return 0;
        }
        if (g_hle.ps2) {
            // A thread (or the psf2.irx loader) returned to its $ra sentinel:
            // it exited. Reschedule into the spawned audio threads.
            uint32 t = hle_ps2_thread_exit(iop);
            if (t != 0xffffffffu) {
                hle_set_pc(t);
                return 0;
            }
            // No runnable thread yet -- yield so HE advances time (timers wake
            // threads); the IOP idle is re-entered next slice.
            r3000_break(iop_get_r3000_state(iop));
            return 0;
        }
        // IOP "null" state -- treat as a softcall return / wedge guard.
        TRACE("[hle] null-state pc=%08X\n", pc);
        if (g_hle.in_exception || g_hle.sc_head != g_hle.sc_tail) {
            softcall_pump();
            return 0;
        }
        iop_request_quit(iop);
        return 0;
    }

    if (low == SOFTCALL_LOW) {            // an armed softcall just returned
        TRACE("[hle] softcall return\n");
        if (g_hle.ps2_sc_active) {
            // PS2 IOP IRQ/DMA/timer handler returned to its SOFTCALL_RA. We run
            // handlers SYNCHRONOUSLY in hle_slice_end's nested-r3000_execute
            // drain loop: just mark this one done and break the nested execute
            // so the drain loop regains control (it launches the next queued
            // handler, or restores the interrupted context once the FIFO is
            // empty). Do NOT restore here -- the drain loop owns snapshot/
            // restore so the whole burst is one atomic ISR sequence (the reference
            // nested-mips_execute equivalence).
            g_hle.ps2_sc_active = 0;
            r3000_break(r3000);
            return 0;
        }
        // PS1: reference-equivalence -- the inline RootCounter softcall in
        // psx_bios_exception is NOT atomic; only the no-entry_int tail
        // (FIN_RESTORE) restores, exactly as the reference does.
        softcall_pump();
        return 0;
    }

    if (low == 0x80) {                    // HE exception vector
        TRACE("[hle] exception cause=%02X epc=%08X\n",
              mips_get_cause(CPU()) & 0x3c, mips_get_ePC(CPU()));
        exc_begin();
        return 0;
    }

    if (low == 0xA0 || low == 0xB0 || low == 0xC0) {
        TRACE("[hle] bios %02X t1=%02X ra=%08X\n", low, R(9) & 0xff, R(31));
        bios_call(low);
        return 0;
    }

    TRACE("[hle] stray sentinel pc=%08X -- faulting\n", pc);
    return -1; // unexpected: behave like a bad instruction
}
