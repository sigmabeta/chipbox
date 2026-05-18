/////////////////////////////////////////////////////////////////////////////
//
// hle_cpu - the reference CPU/register shim over the HE r3000
//
// The HLE engine being ported (the reference psx_hw.c) talks to its CPU through
// MAME-style accessors: mips_get_info/mips_set_info with a `union cpuinfo`
// and MIPS_* register selectors, plus mips_get_cause/status/ePC. This header
// reimplements exactly that surface on top of HE's r3000_getreg/setreg so
// the dispatcher body ports with minimal edits.
//
// NOT shimmed here (deliberately): mips_execute / mips_get_icount /
// mips_set_icount. the reference calls those *re-entrantly* from inside the HLE
// (event softcalls, entry_int, the IOP scheduler). HE's r3000_execute is a
// flat slice and cannot recurse into itself from a sentinel dispatch, so
// those call sites are rewritten as continuations (see he/hle.c) rather
// than shimmed. mips_shorten_frame maps to r3000_break (cooperative yield).
//
/////////////////////////////////////////////////////////////////////////////

#ifndef __PSX_HLE_CPU_H__
#define __PSX_HLE_CPU_H__

#include "emuconfig.h"
#include "r3000.h"

#ifdef __cplusplus
extern "C" {
#endif

//
// the reference register selectors (psx.h enum order). Only the value matters; we
// translate to R3000_REG_* in cpu_reg_to_r3000().
//
enum {
    MIPS_PC = 1,
    MIPS_DELAYV, MIPS_DELAYR,
    MIPS_HI, MIPS_LO,
    MIPS_R0, MIPS_R1, MIPS_R2, MIPS_R3, MIPS_R4, MIPS_R5, MIPS_R6,
    MIPS_R7, MIPS_R8, MIPS_R9, MIPS_R10, MIPS_R11, MIPS_R12, MIPS_R13,
    MIPS_R14, MIPS_R15, MIPS_R16, MIPS_R17, MIPS_R18, MIPS_R19, MIPS_R20,
    MIPS_R21, MIPS_R22, MIPS_R23, MIPS_R24, MIPS_R25, MIPS_R26, MIPS_R27,
    MIPS_R28, MIPS_R29, MIPS_R30, MIPS_R31,
    MIPS_CP0R0
    // MIPS_CP0Rn = MIPS_CP0R0 + n  (n = 0..31)
};

#define CPUINFO_INT_PC          (0x10000)
#define CPUINFO_INT_REGISTER    (0x11000) // + MIPS_* selector

union cpuinfo {
    sint64 i;
    void *p;
    char *s;
};

//
// the reference's CPU context is just a pointer back to its PSX_STATE; in the HE
// port the only thing the shim needs is the r3000 state, which we resolve
// from the IOP state. Keep the field name `psx` for source compatibility
// but make it carry the HE r3000 pointer.
//
typedef struct {
    void *r3000; // HE r3000 state
    void *iop;   // HE IOP state (RAM owner)
} MIPS_CPU_CONTEXT;

//
// Map a CPUINFO_INT_* selector to an R3000_REG_* index.
//
static EMU_INLINE sint32 EMU_CALL cpu_reg_to_r3000(uint32 sel) {
    if (sel == CPUINFO_INT_PC) return R3000_REG_PC;
    if (sel >= CPUINFO_INT_REGISTER) {
        uint32 m = sel - CPUINFO_INT_REGISTER;
        if (m == MIPS_PC) return R3000_REG_PC;
        if (m == MIPS_HI) return R3000_REG_HI;
        if (m == MIPS_LO) return R3000_REG_LO;
        if (m == MIPS_DELAYV) return R3000_REG_DELAYV;
        if (m == MIPS_DELAYR) return R3000_REG_DELAY;
        if (m >= MIPS_R0 && m <= MIPS_R31) return R3000_REG_GEN + (sint32) (m - MIPS_R0);
        if (m >= MIPS_CP0R0) return R3000_REG_C0 + (sint32) (m - MIPS_CP0R0);
    }
    return -1;
}

static EMU_INLINE void EMU_CALL mips_get_info(MIPS_CPU_CONTEXT *c, uint32 sel,
                                              union cpuinfo *info) {
    sint32 r = cpu_reg_to_r3000(sel);
    info->i = (r >= 0) ? (sint64) (sint32) r3000_getreg(c->r3000, r) : 0;
}

static EMU_INLINE void EMU_CALL mips_set_info(MIPS_CPU_CONTEXT *c, uint32 sel,
                                              union cpuinfo *info) {
    sint32 r = cpu_reg_to_r3000(sel);
    if (r >= 0) r3000_setreg(c->r3000, r, (uint32) info->i);
}

// COP0: status=12, cause=13, epc=14 (verified in r3000.c getc0/setc0).
static EMU_INLINE uint32 EMU_CALL mips_get_cause(MIPS_CPU_CONTEXT *c) {
    return r3000_getreg(c->r3000, R3000_REG_C0 + 13);
}

static EMU_INLINE uint32 EMU_CALL mips_get_status(MIPS_CPU_CONTEXT *c) {
    return r3000_getreg(c->r3000, R3000_REG_C0 + 12);
}

static EMU_INLINE void   EMU_CALL mips_set_status(MIPS_CPU_CONTEXT *c, uint32 s) {
    r3000_setreg(c->r3000, R3000_REG_C0 + 12, s);
}

static EMU_INLINE uint32 EMU_CALL mips_get_ePC(MIPS_CPU_CONTEXT *c) {
    return r3000_getreg(c->r3000, R3000_REG_C0 + 14);
}

// Cooperative yield: make the current r3000_execute slice return promptly.
static EMU_INLINE void EMU_CALL mips_shorten_frame(MIPS_CPU_CONTEXT *c) {
    r3000_break(c->r3000);
}

#ifdef __cplusplus
}
#endif

#endif
