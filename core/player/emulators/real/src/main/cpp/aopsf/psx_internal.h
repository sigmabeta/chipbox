/*
	Audio Overload SDK - Sony Playstation audio emulator

	Copyright (c) 2007-2008 R. Belmont and Richard Bannister.
  Copyright (c) 2015 Christopher Snowhill

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

//
// Audio Overload
// Emulated music player
//
// (C) 2000-2008 Richard F. Bannister
//

//
// psx_internal.h
//
// References:
// psf_format.txt v1.6 by Neill Corlett (filesystem and decompression info)
// Intel ELF format specs ELF.PS (general ELF parsing info)
// http://ps2dev.org/kb.x?T=457 (IRX relocation and inter-module call info)
// http://ps2dev.org/ (the whole site - lots of IOP info)
// spu2regs.txt (comes with SexyPSF source: IOP hardware info)
// 64-bit ELF Object File Specification: http://techpubs.sgi.com/library/manuals/4000/007-4658-001/pdf/007-4658-001.pdf (MIPS ELF relocation types)

#ifndef _PSX_INTERNAL_H_
#define _PSX_INTERNAL_H_

#define DEBUG_LOADER    (0)
#define MAX_FS        (32)    // maximum # of filesystems (libs and subdirectories)

#define MAX_FILE_SLOTS  (32)

// ELF relocation helpers
#define ELF32_R_SYM(val)                ((val) >> 8)
#define ELF32_R_TYPE(val)               ((val) & 0xff)

#define DEBUG_HLE_BIOS    (0)        // debug PS1 HLE BIOS
#define DEBUG_HLE_IOP    (0)        // debug PS2 IOP OS calls
#define DEBUG_UNK_RW    (0)        // debug unknown reads/writes
#define DEBUG_THREADING (0)        // debug PS2 IOP threading

#define DEBUG_DISASM    (0)     // debug all CPU activity to a log file

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
    uint32 attr;
    uint32 option;
    int32 init;
    int32 current;
    int32 max;
    int32 threadsWaiting;
    int32 inuse;
} Semaphore;

#define SEMA_MAX    (64)

// thread states
enum {
    TS_RUNNING = 0,        // now running
    TS_DORMANT,        // ready to run
    TS_WAITEVFLAG,        // waiting on an event flag
    TS_WAITSEMA,        // waiting on a semaphore
    TS_WAITDELAY,        // waiting on a time delay
    TS_SLEEPING,        // sleeping

    TS_MAXSTATE
};

enum WEF_FLAGS {
    WEF_AND = 0x00,
    WEF_OR = 0x01,
    WEF_CLEAR = 0x10,
};

typedef struct {
    int32 iState;        // state of thread

    uint32 flags;        // flags
    uint32 routine;        // start of code for the thread
    uint32 stackloc;    // stack location in IOP RAM
    uint32 stacksize;    // stack size
    uint32 refCon;        // user value passed in at CreateThread time

    uint32 waitparm;    // what we're waiting on if in one the TS_WAIT* states

    uint32 wakeupcount;

    uint32 waiteventmode;
    uint32 waiteventmask;
    uint32 waiteventresultptr;

    uint32 save_regs[37];    // CPU registers belonging to this thread
} Thread;

typedef struct {
    int32 iActive;
    uint32 count;
    uint32 target;
    uint32 source;
    uint32 prescale;
    uint32 handler;
    uint32 hparam;
    uint32 mode;
} IOPTimer;

#define COUNTERS (6)

struct IOPTIMER_COUNTER {
    //
    // quick values used in advance loop, etc.
    //
    uint32 save;
    uint64 counter;
    uint32 delta;
    uint64 target;
    uint8 target_is_overflow;
    //
    // other values
    //
    uint16 mode;
    uint16 status;
    uint64 compare;
};

struct IOPTIMER_STATE {
    struct IOPTIMER_COUNTER counter[COUNTERS];
    uint8 gate;
    uint64 field_counter;
    uint64 field_vblank;
    uint64 field_total;
    uint32 hz_sysclock;
    uint32 hz_hline;
    uint32 hz_pixel;
};

#define CLOCK_DIV    (8)    // 33 MHz / this = what we run the R3000 at to keep the CPU usage not insane

// counter modes
#define RC_EN        (0x0001)    // halt
#define RC_RESET    (0x0008)    // automatically wrap
#define RC_IQ1        (0x0010)    // IRQ when target reached
#define RC_IQ2        (0x0040)    // IRQ when target reached (pSX treats same as IQ1?)
#define RC_CLC        (0x0100)    // counter uses direct system clock
#define RC_DIV8        (0x0200)    // (counter 2 only) system clock/8

enum {
    MAX_EVENT = 32,
};

typedef struct {
    uint32 isValid;
    uint32 enabled;
    uint32 classId;
    uint32 spec;
    uint32 mode;
    uint32 func;
    uint32 fired;
} EvtCtrlBlk;

// Sony event states
#define EvStUNUSED    0x0000
#define EvStWAIT    0x1000
#define EvStACTIVE    0x2000
#define EvStALREADY    0x4000

// Sony event modes
#define EvMdINTR    0x1000
#define EvMdNOINTR    0x2000

enum {
    BLK_STAT = 0,
    BLK_SIZE = 4,
    BLK_FD = 8,
    BLK_BK = 12
};

#if DEBUG_DISASM
#include <stdio.h>
#endif

struct mips_cpu_context {
    UINT32 op;
    UINT32 pc;
    UINT32 prevpc;
    UINT32 delayv;
    UINT32 delayr;
    UINT32 hi;
    UINT32 lo;
    UINT32 r[32];
    UINT32 cp0r[32];
    PAIR cp2cr[32];
    PAIR cp2dr[32];

    int (*irq_callback)(void *, int irqline);

    void *irq_callback_param;

    int mips_ICount;

    PSX_STATE *psx;

#if DEBUG_DISASM
    FILE *file;
#endif
};

struct psx_state {
    // main RAM
    uint32 psx_ram[(2 * 1024 * 1024) / 4];

    uint32 initial_ram[(2 * 1024 * 1024) / 4];

    // spare the PSF2 elf loader from using malloc
    uint32 elf_scratch[(2 * 1024 * 1024) / 4];

    uint32 scratch[0x400 / 4];

    uint32 initialPC, initialSP;
    uint32 initialGP; // PSF1 only
    uint32 loadAddr;

    uint32 stop; // stop running when this is set

    // SPU format
    uint8 *start_of_file, *song_ptr;
    uint32 cur_tick, cur_event, num_events, next_tick, end_tick;
    int old_fmt;

    uint32 psf_refresh;
    uint32 samples_into_frame;

    MIPS_CPU_CONTEXT mipscpu;

    uint32 spu_delay, dma_pcr, dma_icr, irq_data, irq_mask, dma_timer, WAI;
    uint32 dma4_madr, dma4_bcr, dma4_chcr, dma4_delay;
    uint32 dma7_madr, dma7_bcr, dma7_chcr, dma7_delay;
    uint32 dma4_cb, dma7_cb, dma4_fval, dma4_flag, dma7_fval, dma7_flag;
    uint32 irq9_cb, irq9_fval, irq9_flag;
    uint32 irq_masked;

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

    struct IOPTIMER_STATE root_cnts;

    uint32 eventsAllocated;

    EvtCtrlBlk *Event;

    uint32 gpu_stat;

    int fcnt;

    uint32 heap_addr;

    uint32 irq_regs[37];

    int irq_mutex;

    int vblank_samples_until_next;

    char *error_ptr;
    char error_buffer[32768];

    psx_console_callback_t console_callback;
    void *console_context;

    uint32 offset_to_spu;
};

#define SPUSTATE      ((void*)(((char*)(psx))+(psx->offset_to_spu)))

extern void mips_init(MIPS_CPU_CONTEXT *);

extern void mips_reset(MIPS_CPU_CONTEXT *, void *param);

extern int mips_execute(MIPS_CPU_CONTEXT *, int cycles);

extern void mips_set_info(MIPS_CPU_CONTEXT *, UINT32 state, union cpuinfo *info);

extern void psx_hw_init(PSX_STATE *, uint32 version);

extern void psx_hw_slice(PSX_STATE *);

extern void ps2_hw_slice(PSX_STATE *);

extern void psx_hw_frame(PSX_STATE *);

extern void ps2_hw_frame(PSX_STATE *);

extern void ps2_reschedule(PSX_STATE *);

extern uint32 mips_get_cause(MIPS_CPU_CONTEXT *);

extern uint32 mips_get_status(MIPS_CPU_CONTEXT *);

extern void mips_set_status(MIPS_CPU_CONTEXT *, uint32 status);

extern uint32 mips_get_ePC(MIPS_CPU_CONTEXT *);

uint32 psf2_get_loadaddr(PSX_STATE *);

void psf2_set_loadaddr(PSX_STATE *, uint32 new);

static void call_irq_routine(PSX_STATE *, uint32 routine, uint32 parameter);

extern void psx_bios_hle(PSX_STATE *, uint32 pc);

extern void psx_iop_call(PSX_STATE *, uint32 pc, uint32 callnum);

extern uint8 program_read_byte_32le(void *, offs_t address);

extern uint16 program_read_word_32le(void *, offs_t address);

extern uint32 program_read_dword_32le(void *, offs_t address);

extern void program_write_byte_32le(void *, offs_t address, uint8 data);

extern void program_write_word_32le(void *, offs_t address, uint16 data);

extern void program_write_dword_32le(void *, offs_t address, uint32 data);

#endif
