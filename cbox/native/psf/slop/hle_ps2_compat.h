/////////////////////////////////////////////////////////////////////////////
//
// hle_ps2_compat - thin compatibility shim so the reference PS2 IOP-kernel
// (eng_psf2.c psf2_load_elf + psx_hw.c FreezeThread/ThawThread/
// ps2_reschedule/psx_iop_call/iop_sprintf/psx_hw_runcounters) compiles
// almost verbatim against the HE core. Minimising hand-translation keeps
// the 2000-line port faithful (this is exactly how Phase 1's the reference-
// equivalence audit concluded the port should be done).
//
/////////////////////////////////////////////////////////////////////////////

#ifndef __PSX_HLE_PS2_COMPAT_H__
#define __PSX_HLE_PS2_COMPAT_H__

#include "emuconfig.h"
#include "hle_cpu.h"   // union cpuinfo, MIPS_*, mips_get/set_info, mips_get_status, mips_shorten_frame

typedef uint8 u8;
typedef uint16 u16;
typedef uint32 u32;
typedef sint8 int8;
typedef sint16 int16;
typedef sint32 int32;
typedef sint64 int64;
typedef uint32 UINT32;
typedef sint32 INT32;
typedef uint32 offs_t;

#define SPUSTATE ((void*)0)   // SPU2 owned by HE; the reference SPU hooks are inert here

#define LE32(x) (x)                 // little-endian build: identity
#define AO_SUCCESS (0)
#define AO_FAIL    (-1)
#define MAX_FILE_SLOTS (32)
#define SEMA_MAX  (64)
#define MAX_EVENT (32)
#define CLOCK_DIV (8)

#define ELF32_R_SYM(i)  ((i) >> 8)
#define ELF32_R_TYPE(i) ((i) & 0xff)

// debug switches the reference code tests -- all off
#define DEBUG_HLE_IOP    0
#define DEBUG_THREADING  0
#define DEBUG_LOADER     0
#define DEBUG_DISASM     0

typedef sint32 (*virtual_readfile)(void *context, const char *path,
                                   sint32 offset, char *buffer, sint32 length);

// thread states (psx_internal.h)
enum {
    TS_RUNNING = 0, TS_DORMANT, TS_WAITEVFLAG, TS_WAITSEMA,
    TS_WAITDELAY, TS_SLEEPING, TS_MAXSTATE
};
enum WEF_FLAGS {
    WEF_AND = 0x00, WEF_OR = 0x01, WEF_CLEAR = 0x10
};

#define EvStUNUSED 0x0000
#define EvStWAIT   0x1000
#define EvStACTIVE 0x2000
#define EvStALREADY 0x4000
#define EvMdINTR   0x1000
#define EvMdNOINTR 0x2000

#define RC_EN   0x0001
#define RC_RESET 0x0008
#define RC_IQ1  0x0010
#define RC_IQ2  0x0040
#define RC_CLC  0x0100
#define RC_DIV8 0x0200

typedef struct {
    char name[10];
    uint32 dispatch;
} ExternLibEntries;
typedef struct {
    uint32 type;
    uint32 value;
    uint32 param;
    int inUse;
} EventFlag;
typedef struct {
    uint32 attr, option;
    int32 init, current, max, threadsWaiting, inuse;
} Semaphore;
typedef struct {
    int32 iState;
    uint32 flags, routine, stackloc, stacksize, refCon, waitparm, wakeupcount;
    uint32 waiteventmode, waiteventmask, waiteventresultptr;
    uint32 save_regs[37];
} Thread;
typedef struct {
    int32 iActive;
    uint32 count, target, source, prescale, handler, hparam, mode;
} IOPTimer;
typedef struct {
    uint32 isValid, enabled, classId, spec, mode, func, fired;
} EvtCtrlBlk;

typedef struct psx_state {
    uint32 *psx_ram;              // aliased to HE IOP RAM (2 MB)
    uint32 *elf_scratch;          // scratch for the ELF loader
    MIPS_CPU_CONTEXT mipscpu;

    uint32 initialPC, initialSP, initialGP, loadAddr;
    uint32 stop;

    uint32 psf_refresh;

    uint32 spu_delay, dma_pcr, dma_icr, irq_data, irq_mask, dma_timer, WAI;
    uint32 dma4_madr, dma4_bcr, dma4_chcr, dma4_delay;
    uint32 dma7_madr, dma7_bcr, dma7_chcr, dma7_delay;
    uint32 dma4_cb, dma7_cb, dma4_fval, dma4_flag, dma7_fval, dma7_flag;
    uint32 irq9_cb, irq9_fval, irq9_flag, irq_masked;

    virtual_readfile readfile;
    void *readfile_context;

    volatile int softcall_target;
    int filestat[MAX_FILE_SLOTS];
    char *filename[MAX_FILE_SLOTS];
    uint32 filesize[MAX_FILE_SLOTS], filepos[MAX_FILE_SLOTS];
    int intr_susp;

    uint64 sys_time;
    int timerexp;

    int32 iNumLibs;
    ExternLibEntries reglibs[32];
    int32 iNumFlags;
    EventFlag evflags[32];
    int32 iNumSema;
    Semaphore semaphores[SEMA_MAX];
    int32 iNumThreads, iCurThread, rescheduleNeeded;
    Thread threads[32];
    IOPTimer iop_timers[8];
    int32 iNumTimers;

    void *root_cnts;      // -> g_hle root-counter ioptimer (bridge)

    uint32 eventsAllocated;
    EvtCtrlBlk *Event;
    uint32 gpu_stat;
    int fcnt;
    uint32 heap_addr;
    uint32 irq_regs[37];
    int irq_mutex;
    int vblank_samples_until_next;

    // console_* BEFORE error_buffer so a runaway error_ptr append cannot
    // corrupt the callback pointer (defence in depth; error_ptr is also
    // reset every kernel call -- see hle_ps2_iop_call).
    void (*console_callback)(void *, const char *);

    void *console_context;

    char *error_ptr;
    char error_buffer[1024];
} PSX_STATE;

static int psxcpu_verbose = 0;

// printlog / android log: no-ops (console disabled in the harness/app)
static EMU_INLINE void printlog(PSX_STATE *psx, const char *fmt, ...) {
    (void) psx;
    (void) fmt;
}

#ifndef __android_log_print
#define __android_log_print(...) ((void)0)
// `ANDROID_LOG_WARN` / `_INFO` etc. come from `<android/log.h>` on Android; on hosts
// (WASM, x86 Linux) we don't include that header, so define harmless ints for the
// log-priority arguments referenced inside the (now no-op) `__android_log_print` calls.
#ifndef ANDROID_LOG_VERBOSE
#define ANDROID_LOG_VERBOSE 0
#define ANDROID_LOG_DEBUG   0
#define ANDROID_LOG_INFO    0
#define ANDROID_LOG_WARN    0
#define ANDROID_LOG_ERROR   0
#define ANDROID_LOG_FATAL   0
#endif
#endif

// Bridges implemented in hle.c (HE-owned hardware).
void   EMU_CALL hle_ps2_irq_set(uint32 irq);

sint32 EMU_CALL hle_ps2_readfile_bridge(const char *path, sint32 ofs,
                                        char *buf, sint32 len);

void   EMU_CALL hle_ps2_set_refresh(uint32 refresh);

// Actual SPU2 sample DMA copy on HE's real SPU2 core (the reference port's
// SPUSTATE is inert). core 0/1, len bytes, towrite = RAM->SPU2.
void   EMU_CALL hle_ps2_spu_dma(int core, uint32 madr, uint32 len, int towrite);

void   EMU_CALL hle_ps2_spu_dma_done(int core);

#endif
