/////////////////////////////////////////////////////////////////////////////
//
// hle_ps2 - PS2 (PSF2) IOP-kernel HLE.
//
// The reference eng_psf2.c / psx_hw.c PS2 engine (ELF loader, cooperative
// thread scheduler, and the ~1570-line psx_iop_call covering sysmem/
// loadcore/intrman/threadman/sysclib/ioman/modload/sifman/timrman/
// vblank) ported VERBATIM behind he/hle_ps2_compat.h. Keeping the port
// literal is deliberate -- the Phase 1 audit proved hand-translation of
// this engine is the main source of bugs.
//
// HE owns the real hardware (SPU2, interrupt controller, root counters,
// cycle pacing); this file is purely the IOP *software* (kernel + module
// loader). Bridges to hle.c handle the few hardware touch-points.
//
/////////////////////////////////////////////////////////////////////////////

#ifndef EMU_COMPILE
#error "Hi I forgot to set EMU_COMPILE"
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "hle.h"
#include "hle_ps2_compat.h"
#include "iop.h"

static PSX_STATE g_ps2;

// Forward decls (port cross-references)
static void ps2_reschedule(PSX_STATE *psx);

uint32 psf2_load_elf(PSX_STATE *psx, const uint8 *start, uint32 len);

uint32 psf2_load_file(PSX_STATE *psx, const char *file, uint8 *buf, uint32 buflen);

uint32 psf2_get_loadaddr(PSX_STATE *psx);

void psf2_set_loadaddr(PSX_STATE *psx, uint32 newv);

// hardware bridges (hle.c). ps2_set_refresh is provided verbatim by the
// pasted the reference code (just stores psf_refresh).
static void psx_irq_set(PSX_STATE *psx, uint32 irq) {
    (void) psx;
    hle_ps2_irq_set(irq);
}

// IOP register window access used by psx_iop_call (a few intrman/sif
// services). Route through HE's r3000 memory map (HLE-owned in HLE mode).
extern uint32 EMU_CALL r3000_lw(void *state, uint32 a);

extern void   EMU_CALL r3000_sw(void *state, uint32 a, uint32 d);

static uint32 psx_hw_read(PSX_STATE *psx, uint32 a, uint32 mask) {
    return r3000_lw(psx->mipscpu.r3000, a) & ~mask;
}

static void psx_hw_write(PSX_STATE *psx, uint32 a, uint32 d, uint32 mask) {
    uint32 cur = r3000_lw(psx->mipscpu.r3000, a);
    r3000_sw(psx->mipscpu.r3000, a, (cur & mask) | (d & ~mask));
}

// Defined in the glue below; used by the pasted runcounters.
static void call_irq_routine(PSX_STATE *psx, uint32 routine, uint32 parameter,
                             const char *source);

static void spu_interrupt_dma4(void *s);

static void spu_interrupt_dma7(void *s);

static void ps2_set_refresh(PSX_STATE *psx, uint32 refresh);

/////////////////////////////////////////////////////////////////////////////
// --- eng_psf2.c: get_le16/get_le32/secname ---
static uint16 get_le16(const uint8 *start) {
    return start[0] | start[1] << 8;
}

static uint32 get_le32(const uint8 *start) {
    return start[0] | start[1] << 8 | start[2] << 16 | start[3] << 24;
}

static uint32 secname(const uint8 *start, uint32 strndx, uint32 shoff, uint32 shentsize,
                      uint32 name) {
    uint32 offset, shent;

    // get string table section
    shent = shoff + (shentsize * strndx);

    // find the offset to the section
    offset = get_le32(start + shent + 16);

    offset += name;

    return offset;
}

// --- eng_psf2.c: do_iopmod ---
static void do_iopmod(const uint8 *start, uint32 offset) {
    uint32 nameoffs, saddr, heap, tsize, dsize, bsize, vers2;

    nameoffs = get_le32(start + offset);

    saddr = get_le32(start + offset + 4);
    heap = get_le32(start + offset + 8);
    tsize = get_le32(start + offset + 12);
    dsize = get_le32(start + offset + 16);
    bsize = get_le32(start + offset + 20);
    vers2 = get_le16(start + offset + 24);

//	printf("nameoffs %08x saddr %08x heap %08x tsize %08x dsize %08x bsize %08x\n", nameoffs, saddr, heap, tsize, dsize, bsize);
#if DEBUG_LOADER
    printf("vers: %04x name [%s]\n", vers2, &start[offset+26]);
#endif
}
// --- eng_psf2.c: psf2_load_elf ---
uint32 psf2_load_elf(PSX_STATE *psx, const uint8 *start, uint32 len) {
    uint32 entry, phoff, shoff, phentsize, shentsize, phnum, shnum, shstrndx;
    uint32 name, type, flags, addr, offset, size, shent;
    uint32 totallen;
    int i, rec;
//	FILE *f;

    if (psx->loadAddr & 3) {
        psx->loadAddr &= ~3;
        psx->loadAddr += 4;
    }

#if DEBUG_LOADER
    printf("psf2_load_elf: starting at %08x\n", loadAddr | 0x80000000);
#endif

    if ((start[0] != 0x7f) || (start[1] != 'E') || (start[2] != 'L') || (start[3] != 'F')) {
        printf("Not an ELF file\n");
        return 0xffffffff;
    }

    entry = get_le32(start + 24);    // 0x18
    phoff = get_le32(start + 28);    // 0x1c
    shoff = get_le32(start + 32);    // 0x20

//	printf("Entry: %08x phoff %08x shoff %08x\n", entry, phoff, shoff);

    phentsize = get_le16(start + 42);            // 0x2a
    phnum = get_le16(start + 44);            // 0x2c
    shentsize = get_le16(start + 46);            // 0x2e
    shnum = get_le16(start + 48);            // 0x30
    shstrndx = get_le16(start + 50);            // 0x32

//	printf("phentsize %08x phnum %d shentsize %08x shnum %d shstrndx %d\n", phentsize, phnum, shentsize, shnum, shstrndx);

    // process ELF sections
    shent = shoff;
    totallen = 0;
    for (i = 0; i < shnum; i++) {
        name = get_le32(start + shent);
        type = get_le32(start + shent + 4);
        flags = get_le32(start + shent + 8);
        addr = get_le32(start + shent + 12);
        offset = get_le32(start + shent + 16);
        size = get_le32(start + shent + 20);

//		printf("Section %02d: name %08x [%s] type %08x flags %08x addr %08x offset %08x size %08x\n", i, name, &start[secname(start, shstrndx, shoff, shentsize, name)], type, flags, addr, offset, size);

        switch (type) {
            case 0:            // section table header - do nothing
                break;

            case 1:            // PROGBITS: copy data to destination
                memcpy(&psx->psx_ram[(psx->loadAddr + addr) / 4], &start[offset], size);
                totallen += size;
                break;

            case 2:            // SYMTAB: ignore
                break;

            case 3:            // STRTAB: ignore
                break;

            case 8:            // NOBITS: BSS region, zero out destination
                memset(&psx->psx_ram[(psx->loadAddr + addr) / 4], 0, size);
                totallen += size;
                break;

            case 9:            // REL: short relocation data
                for (rec = 0; rec < (size / 8); rec++) {
                    uint32 offs, info, target, temp, val, vallo;
                    static uint32 hi16offs = 0, hi16target = 0;

                    offs = get_le32(start + offset + (rec * 8));
                    info = get_le32(start + offset + 4 + (rec * 8));
                    target = LE32(psx->psx_ram[(psx->loadAddr + offs) / 4]);

//					printf("[%04d] offs %08x type %02x info %08x => %08x\n", rec, offs, ELF32_R_TYPE(info), ELF32_R_SYM(info), target);

                    switch (ELF32_R_TYPE(info)) {
                        case 2:            // R_MIPS_32
                            target += psx->loadAddr;
//							target |= 0x80000000;
                            break;

                        case 4:        // R_MIPS_26
                            temp = (target & 0x03ffffff);
                            target &= 0xfc000000;
                            temp += (psx->loadAddr >> 2);
                            target |= temp;
                            break;

                        case 5:        // R_MIPS_HI16
                            hi16offs = offs;
                            hi16target = target;
                            break;

                        case 6:        // R_MIPS_LO16
                            vallo = ((target & 0xffff) ^ 0x8000) - 0x8000;

                            val = ((hi16target & 0xffff) << 16) + vallo;
                            val += psx->loadAddr;
//							val |= 0x80000000;

                            /* Account for the sign extension that will happen in the low bits.  */
                            val = ((val >> 16) + ((val & 0x8000) != 0)) & 0xffff;

                            hi16target = (hi16target & ~0xffff) | val;

                            /* Ok, we're done with the HI16 relocs.  Now deal with the LO16.  */
                            val = psx->loadAddr + vallo;
                            target = (target & ~0xffff) | (val & 0xffff);

                            psx->psx_ram[(psx->loadAddr + hi16offs) / 4] = LE32(hi16target);
                            break;

                        default:
                            printf("FATAL: Unknown MIPS ELF relocation!\n");
                            return 0xffffffff;
                            break;
                    }

                    psx->psx_ram[(psx->loadAddr + offs) / 4] = LE32(target);
                }
                break;

            case 0x70000080:    // .iopmod
                do_iopmod(start, offset);
                break;

            default:
#if DEBUG_LOADER
                printf("Unhandled ELF section type %d\n", type);
#endif
                break;
        }

        shent += shentsize;
    }

    entry += psx->loadAddr;
    entry |= 0x80000000;
    psx->loadAddr += totallen;

#if DEBUG_LOADER
    printf("psf2_load_elf: entry PC %08x\n", entry);
#endif
    return entry;
}
// --- eng_psf2.c: psf2_load_file ---
uint32 psf2_load_file(PSX_STATE *psx, const char *file, uint8 *buf, uint32 buflen) {
    int i = psx->readfile(psx->readfile_context, file, 0, (char *) buf, buflen);
    if (i < 0)
        return 0xffffffff;
    else
        return i;
}

// --- eng_psf2.c: psf2_get/set_loadaddr ---
uint32 psf2_get_loadaddr(PSX_STATE *psx) {
    return psx->loadAddr;
}

void psf2_set_loadaddr(PSX_STATE *psx, uint32 new) {
    psx->loadAddr = new;
}

/////////////////////////////////////////////////////////////////////////////
// --- psx_hw.c: FreezeThread/ThawThread/ps2_reschedule ---

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
        psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                    : 0].iState = TS_RUNNING;
    } else {
        if (psx->iCurThread != -1 &&
            psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                        : 0].iState != TS_RUNNING) {
            FreezeThread(psx, psx->iCurThread, 0);
            mips_shorten_frame(&psx->mipscpu);    // kill the CPU
            psx->iCurThread = -1;    // no threads are active
        }
    }
}
/////////////////////////////////////////////////////////////////////////////
// --- psx_hw.c: program_read_byte_32le ---
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
// --- psx_hw.c: iop_sprintf ---
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
/////////////////////////////////////////////////////////////////////////////
// --- psx_hw.c: psx_iop_call ---
extern unsigned long long g_hle_pump_sample;

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
    {
        static int n = 0;
        if (getenv("HLE_IOP") && n++ < 100000)
            fprintf(stderr, "[iop] %-9s svc=%-3u a0=%08X a1=%08X a2=%08X\n",
                    name, callnum, a0, a1, a2);
    }
    {
        if (getenv("IOPHIST")) {
            static char hk[64][16];
            static unsigned hc[64];
            static int hn = 0;
            static unsigned long long tot = 0;
            char key[16];
            int k;
            snprintf(key, sizeof key, "%.6s:%u", name, callnum);
            for (k = 0; k < hn; k++) if (!strcmp(hk[k], key)) break;
            if (k == hn && hn < 64) {
                strncpy(hk[hn], key, 15);
                hk[hn][15] = 0;
                hc[hn] = 0;
                hn++;
            }
            if (k < 64) hc[k]++;
            if ((++tot % 20000ULL) == 0) {
                fprintf(stderr, "[iophist] tot=%llu :", tot);
                for (k = 0; k < hn; k++)
                    if (hc[k] >= tot / 40ULL)
                        fprintf(stderr, " %s=%u", hk[k], hc[k]);
                fprintf(stderr, "\n");
            }
        }
    }


#if 0
                                                                                                                            if (psx->console_callback)
	{
		sprintf(out, "IOP: call module [%s] service %d (PC=%08x)\n", name, callnum, pc);
		psx->console_callback(psx->console_context, out);
	}
#endif

    if (!strcmp(name, "stdio")) {
        switch (callnum) {
            case 4:    // printf -- console output only; no audio effect.
                // (iop_sprintf into the fixed out[512] can overflow on
                // a malformed %s arg; the harness/app has no console
                // callback anyway, so skip the formatting entirely.)
                if (psx->console_callback) {
                    mname = (char *) psx->psx_ram;
                    mname += a0 & 0x1fffff;
                    mname += (a0 & 3);
                    iop_sprintf(psx, out, mname, 1);    // a1 is first parm
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

                if (psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState != TS_DORMANT) {
#if DEBUG_THREADING
                    printlog(psx, "IOP: Thread not ready!\n");
#endif
                    return;
                }

                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState = TS_RUNNING;
                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[4] = a1;
                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[34] = psx->threads[
                        (unsigned) (a0) < 32u ? (a0) : 0].routine;
                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[29] =
                        psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].stackloc +
                        psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].stacksize - 0x10;
                psx->rescheduleNeeded = 1;

                break;

            case 7: // StartThreadArgs
#if DEBUG_THREADING
                printlog(psx, "IOP: StartThreadArgs(%d %d %08X", a0, a1, a2);
#endif

                if (psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState != TS_DORMANT) {
#if DEBUG_THREADING
                    printlog(psx, "IOP: Thread not ready!\n");
#endif
                    return;
                }

                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState = TS_RUNNING;
                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[4] = a1;

                {
                    uint32 stackAddress = psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].stackloc +
                                          psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].stacksize;
                    uint32 fixedSize = ((a1 + 0x3) & ~0x3);
                    uint32 copyAddress = stackAddress - a1;
                    stackAddress -= fixedSize;
                    memcpy(((uint8 *) psx->psx_ram) + copyAddress, ((uint8 *) psx->psx_ram) + a2,
                           a1);
                    psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[29] =
                            stackAddress - 0x10;
                    psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[5] = copyAddress;
                }

                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].save_regs[34] = psx->threads[
                        (unsigned) (a0) < 32u ? (a0) : 0].routine;

                psx->rescheduleNeeded = 1;

                break;

            case 20:// GetThreadID
#if DEBUG_THREADING
                printlog(psx, "IOP: GetThreadId()\n");
#endif
                if (getenv("HLE_RPC"))
                    fprintf(stderr, "[thr] GetThreadId -> %d\n", psx->iCurThread);

                mipsinfo.i = psx->iCurThread;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 24:// SleepThread
#if DEBUG_THREADING
                                                                                                                                        mips_get_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);
				printlog(psx, "IOP: SleepThread() [curThread %d, PC=%x]\n", psx->iCurThread, mipsinfo.i);
#endif

                if (psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].iState !=
                    TS_RUNNING) {
#if DEBUG_THREADING
                    printlog(psx, "IOP: Thread not running!\n");
#endif
                    psx->stop = 1;
                    return;
                }

                if (psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].wakeupcount ==
                    0) {
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].iState = TS_SLEEPING;
                    psx->rescheduleNeeded = 1;
                    if (getenv("HLE_RPC"))
                        fprintf(stderr, "[thr] @%llu SleepThread cur=%d -> SLEEP\n",
                                g_hle_pump_sample, psx->iCurThread);
                } else {
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].wakeupcount--;
                    if (getenv("HLE_RPC"))
                        fprintf(stderr,
                                "[thr] @%llu SleepThread cur=%d -> consume wakeup (wc now %u)\n",
                                g_hle_pump_sample,
                                psx->iCurThread,
                                psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32)
                                             ? psx->iCurThread : 0].wakeupcount);
                }
                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 25:// WakeupThread
#if DEBUG_THREADING
                printlog(psx, "IOP: WakeupThread(%d)\n", a0);
#endif

                // set thread to "ready to go"
                if (psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState == TS_SLEEPING) {
                    psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState = TS_RUNNING;
                    psx->rescheduleNeeded = 1;
                    if (getenv("HLE_RPC"))
                        fprintf(stderr, "[thr] @%llu WakeupThread(%d) cur=%d -> WAKE\n",
                                g_hle_pump_sample, a0, psx->iCurThread);
                } else {
                    psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].wakeupcount++;
                    if (getenv("HLE_RPC"))
                        fprintf(stderr,
                                "[thr] @%llu WakeupThread(%d) cur=%d state=%d -> wc++ (%u)\n",
                                g_hle_pump_sample,
                                a0, psx->iCurThread,
                                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState,
                                psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].wakeupcount);
                }
                mipsinfo.i = psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].wakeupcount;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 26:// iWakeupThread
#if DEBUG_THREADING
                printlog(psx, "IOP: iWakeupThread(%d)\n", a0);
#endif

                // set thread to "ready to go" if it's not running
                if (psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState == TS_SLEEPING) {
                    psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].iState = TS_RUNNING;
                    psx->rescheduleNeeded = 1;
                } else {
                    psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].wakeupcount++;
                }
                mipsinfo.i = psx->threads[(unsigned) (a0) < 32u ? (a0) : 0].wakeupcount;
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

                psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                            : 0].iState = TS_WAITDELAY;
                dTicks /= (double) 1000000.0;
                dTicks *= (double) 36864000.0;    // 768*48000 = IOP native-mode clock rate
                psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                            : 0].waitparm = (uint32) dTicks;
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

                psx->evflags[(unsigned) (a0) < 32u ? (a0) : 0].value |= a1;

                for (i = 0; i < psx->iNumThreads; i++) {
                    if (psx->threads[i].iState != TS_WAITEVFLAG) continue;
                    if (psx->threads[i].waitparm == a0) {
                        int success = ProcessEventFlag(psx->threads[i].waiteventmode,
                                                       &psx->evflags[(unsigned) (a0) < 32u ? (a0)
                                                                                           : 0].value,
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

                psx->evflags[(unsigned) (a0) < 32u ? (a0) : 0].value |= a1;

                for (i = 0; i < psx->iNumThreads; i++) {
                    if (psx->threads[i].iState != TS_WAITEVFLAG) continue;
                    if (psx->threads[i].waitparm == a0) {
                        int success = ProcessEventFlag(psx->threads[i].waiteventmode,
                                                       &psx->evflags[(unsigned) (a0) < 32u ? (a0)
                                                                                           : 0].value,
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

                psx->evflags[(unsigned) (a0) < 32u ? (a0) : 0].value &= a1;

                mipsinfo.i = 0;
                mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                break;

            case 9: // iClearEventFlag
                a0--;
#if DEBUG_HLE_IOP
                printlog(psx, "IOP: iClearEventFlag(%d %08x)\n", a0, a1);
#endif

                psx->evflags[(unsigned) (a0) < 32u ? (a0) : 0].value &= a1;

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

                i = ProcessEventFlag(a2, &psx->evflags[(unsigned) (a0) < 32u ? (a0) : 0].value, a1,
                                     (a3 != 0) ? &psx->psx_ram[a3 / 4] : 0);

                if (!i) {
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].iState = TS_WAITEVFLAG;
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].waitparm = a0;
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].waiteventmode = a2;
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].waiteventmask = a1;
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].waiteventresultptr = a3;

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
                printlog(psx, "IOP: SignalSema(%d) (current %d)\n", a0, psx->semaphores[(unsigned)(a0)<64u?(a0):0].current);
#endif

                if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting != 0) {
                    for (i = 0; i < psx->iNumThreads; i++) {
                        if ((psx->threads[i].iState == TS_WAITSEMA) &&
                            (psx->threads[i].waitparm == a0)) {
                            psx->threads[i].iState = TS_RUNNING;
                            psx->threads[i].waitparm = 0;
                            psx->rescheduleNeeded = 1;
                            psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting--;
                            if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting ==
                                0)
                                break;
                        }
                    }

                    mipsinfo.i = 0;
                } else {
                    if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current <
                        psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].max) {
                        psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current++;
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

                if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting != 0) {
                    for (i = 0; i < psx->iNumThreads; i++) {
                        if ((psx->threads[i].iState == TS_WAITSEMA) &&
                            (psx->threads[i].waitparm == a0)) {
                            psx->threads[i].iState = TS_RUNNING;
                            psx->threads[i].waitparm = 0;
                            psx->rescheduleNeeded = 1;
                            psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting--;
                            if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting ==
                                0)
                                break;
                        }
                    }

                    mipsinfo.i = 0;
                } else {
                    if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current <
                        psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].max) {
                        psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current++;
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
				printlog(psx, "IOP: WaitSema(%d) (cnt %d) (th %d) (PC=%x)\n", a0, psx->iCurThread, psx->semaphores[(unsigned)(a0)<64u?(a0):0].current, (uint32)mipsinfo.i);
#endif

                if (psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current > 0) {
                    psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current--;
                } else {
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].iState = TS_WAITSEMA;
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].waitparm = a0;
                    psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].threadsWaiting++;
                    psx->rescheduleNeeded = 1;
                }

                mipsinfo.i = psx->semaphores[(unsigned) (a0) < 64u ? (a0) : 0].current;
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
        uint32 load_result;

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
                load_result = psf2_load_file(psx, mname, tempmem, 2 * 1024 * 1024);
                if (getenv("HLE_IOP"))
                    fprintf(stderr, "[iop] LoadStartModule '%s' -> %u\n",
                            mname, load_result);
                if (load_result == 0xffffffff) {
                    __android_log_print(ANDROID_LOG_WARN, "PsfProbe",
                                        "IOP LoadStartModule FAILED to read: %s", mname);
                }
                if (load_result != 0xffffffff) {
                    uint32 start;
                    int i;

                    start = psf2_load_elf(psx, tempmem, 2 * 1024 * 1024);
                    if (getenv("HLE_IOP"))
                        fprintf(stderr, "[iop]   '%s' entry=%08X loadAddr=%08X\n",
                                mname, start, psx->loadAddr);

                    if (start == 0xffffffff) {
                        __android_log_print(ANDROID_LOG_WARN, "PsfProbe",
                                            "IOP LoadStartModule FAILED to ELF-load: %s", mname);
                    }

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
                    if (getenv("HLE_DMA"))
                        fprintf(stderr,
                                "[io] open \"%s\" -> size=%d slot=%d\n",
                                mname, (int) psx->filesize[slot2use], slot2use);
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
                // Bad fd (e.g. game closes/seeks an fd open() never
                // handed out -- Front Mission 4 lseeks fd 0). a0-- wraps
                // 0 -> 0xFFFFFFFF; indexing filename/filepos[] there
                // segfaults. Real ioman returns -1 for an invalid fd.
                if ((uint32) a0 >= (uint32) MAX_FILE_SLOTS) {
                    mipsinfo.i = -1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;
                }
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
                if ((uint32) a0 >= (uint32) MAX_FILE_SLOTS) {  // invalid fd -> -1
                    mipsinfo.i = -1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;
                }

#if DEBUG_HLE_IOP
                printlog(psx, "IOP: read(%x %x %d) [pos %d size %d]\n", a0, a1, a2, psx->filepos[a0], psx->filesize[a0]);
#endif

                if (psx->filepos[a0] + a2 > psx->filesize[a0])
                    a2 = psx->filesize[a0] - psx->filepos[a0];

                rp = (uint8 *) psx->psx_ram;
                rp += (a1 & 0x1fffff);

                a2 = psx->readfile(psx->readfile_context, psx->filename[a0], psx->filepos[a0], rp,
                                   a2);
                if (getenv("HLE_IOP"))
                    fprintf(stderr,
                            "[io] read \"%s\" pos=%d -> %d bytes\n",
                            psx->filename[a0], psx->filepos[a0], (int) a2);

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
                if ((uint32) a0 >= (uint32) MAX_FILE_SLOTS) {  // invalid fd -> -1
                    mipsinfo.i = -1;
                    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R2, &mipsinfo);
                    break;
                }
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
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].iState = TS_WAITDELAY;
                    psx->threads[(psx->iCurThread >= 0 && psx->iCurThread < 32) ? psx->iCurThread
                                                                                : 0].waitparm =
                            768 * psx->vblank_samples_until_next;
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

static void ps2_set_refresh(PSX_STATE *psx, uint32 refresh) {
    psx->psf_refresh = refresh;
}

/////////////////////////////////////////////////////////////////////////////
// --- psx_hw.c: psx_hw_runcounters (PS2; root_cnts handled by hle.c) ---
void psx_hw_runcounters(PSX_STATE *psx) {
    int i, j, intr;
    union cpuinfo mipsinfo;

    // don't process any IRQ sources when interrupts are suspended
    if (!psx->intr_susp) {
        if (psx->dma4_delay) {
            psx->dma4_delay--;

            if (psx->dma4_delay == 0) {
                spu_interrupt_dma4(SPUSTATE);
                if (getenv("HLE_DMA"))
                    fprintf(stderr,
                            "[dma] DMA4 complete -> cb=%08X flag=%08X\n",
                            psx->dma4_cb, psx->dma4_flag);

                if (psx->dma4_cb) {
                    call_irq_routine(psx, psx->dma4_cb, psx->dma4_flag, "DMA4");
                }
            }
        }

        if (psx->dma7_delay) {
            psx->dma7_delay--;

            if (psx->dma7_delay == 0) {
                spu_interrupt_dma7(SPUSTATE);

                if (psx->dma7_cb) {
                    call_irq_routine(psx, psx->dma7_cb, psx->dma7_flag, "DMA7");
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
        {
            static unsigned long long rc = 0;
            if (getenv("RCLOG")) {
                rc++;
                if ((rc % 44100ULL) == 0)
                    fprintf(stderr, "[rc] runcounters-calls=%llu sys_time=%llu\n",
                            rc, (unsigned long long) psx->sys_time);
            }
        }

        if (psx->iNumTimers > 0) {
            for (i = 0; i < psx->iNumTimers; i++) {
                if (psx->iop_timers[i].iActive > 0) {
                    psx->iop_timers[i].count += 768;
                    if (psx->iop_timers[i].count >= psx->iop_timers[i].target) {
                        psx->iop_timers[i].count -= psx->iop_timers[i].target;

                        //					printlog(psx, "Timer %d: handler = %08x, param = %08x\n", i, iop_timers[i].handler, iop_timers[i].hparam);
                        call_irq_routine(psx, psx->iop_timers[i].handler,
                                         psx->iop_timers[i].hparam, "IOPTimer");

                        psx->timerexp = 1;
                    }
                }
            }
        }
    }

}

/////////////////////////////////////////////////////////////////////////////
//
// Glue: continuation-based call_irq_routine, SPU-DMA stubs, boot, and the
// entry points hle.c calls.
//
void   EMU_CALL hle_ps1_softcall_arm(uint32 func, uint32 arg); // hle.c
sint32 EMU_CALL hle_ps1_softcall_pending(void);                // hle.c
sint32 EMU_CALL hle_ps1_softcall_has(uint32 func, uint32 arg);  // hle.c

// the reference call_irq_routine sets psx->irq_mutex around the handler so no
// cooperative reschedule fires under it (the handler must run to its
// SOFTCALL_RA return before the woken thread is switched in). hle.c's
// ps2_sc launch/restore drives this.
void EMU_CALL hle_ps2_set_irq_mutex(int v) { g_ps2.irq_mutex = v; }

int  EMU_CALL hle_ps2_irq_mutex(void) { return g_ps2.irq_mutex; }

// PS2 IOP timer/DMA handler invocation. the reference ran these nested; we arm a
// continuation softcall (same mechanism as the PS1 path in hle.c).
static void call_irq_routine(PSX_STATE *psx, uint32 routine, uint32 parameter,
                             const char *source) {
    (void) psx;
    if (!routine) return;
    {
        static unsigned long long d4 = 0, d7 = 0, sp = 0, tm = 0, ot = 0;
        if (getenv("CIRLOG")) {
            if (source && source[0] == 'D' && source[3] == '4') d4++;
            else if (source && source[0] == 'D' && source[3] == '7') d7++;
            else if (source && source[0] == 'S') sp++;
            else if (source && source[0] == 'T') tm++;
            else ot++;
            static unsigned long long n = 0;
            if ((++n % 2000ULL) == 0)
                fprintf(stderr, "[cir] DMA4=%llu DMA7=%llu SPU2=%llu TIMER=%llu other=%llu\n",
                        d4, d7, sp, tm, ot);
        }
    }
    // the reference call_irq_routine atomicity: only ONE IRQ/DMA/timer handler
    // runs at a time. the reference refuses re-entry ("if(!irq_mutex) irq_mutex=1;
    // else return;"). Our handler runs as a deferred softcall, so drop the
    // new request if one is already queued or running -- the source (timer
    // tick / DMA completion) simply re-triggers next time, exactly as on
    // hardware. Without this the timer/DMA sources flood the FIFO and the
    // chained-softcall mechanism corrupts the CPU ("PSX execution error").
    if (hle_ps1_softcall_has(routine, parameter)) {
        if (getenv("HLE_DMA"))
            fprintf(stderr,
                    "[sc] call_irq_routine(%s) DROPPED (same event %08X/%08X in flight)\n",
                    source ? source : "?", routine, parameter);
        return;
    }
    if (getenv("HLE_DMA"))
        fprintf(stderr,
                "[sc] call_irq_routine(%s) routine=%08X arg=%08X\n",
                source ? source : "?", routine, parameter);
    hle_ps1_softcall_arm(routine, parameter);
}

// HE owns SPU2; its DMA completion is handled in hle.c. These the reference hooks
// only need to be inert here.
static void spu_interrupt_dma4(void *s) {
    (void) s;
    hle_ps2_spu_dma_done(0);
}

static void spu_interrupt_dma7(void *s) {
    (void) s;
    hle_ps2_spu_dma_done(1);
}

// the reference ps2_dma4 / ps2_dma7. Called from hle.c hle_dma_sw on the PS2
// SPU2 DMA-channel CHCR write: do the RAM<->SPU2 sample copy (delegated
// to HE via hle_ps2_spu_dma -- SPUSTATE is inert in this port) and arm
// dma{4,7}_delay. psx_hw_runcounters counts it down and call_irq_routine
// invokes the libsd-registered DMA handler, which SetEventFlags the
// audio thread (the steady-state PSF2 playback heartbeat).
void EMU_CALL hle_ps2_dma4(uint32 madr, uint32 bcr, uint32 chcr) {
    int delay;
    uint32 len;
    g_ps2.dma4_madr = madr;
    g_ps2.dma4_bcr = bcr;
    g_ps2.dma4_chcr = chcr;
    len = (bcr >> 16) * (bcr & 0xffff) * 4;
    hle_ps2_spu_dma(0, madr, len, (chcr == 0x01000201) ? 1 : 0);
    delay = (int) (((len / 2) * 8 /*cycles/halfword*/) / 768);
    if (!delay) delay = 1;
    g_ps2.dma4_delay = delay;
    if (getenv("HLE_DMA"))
        fprintf(stderr,
                "[dma] dma4 kick: delay=%d cb=%08X intr_susp=%d curThr=%d\n",
                delay, g_ps2.dma4_cb, g_ps2.intr_susp, g_ps2.iCurThread);
}

// SPU2 IRQ (IOP_INT_SPU). The FFXI sound driver registers a handler via
// intrman RegisterIntrHandler(9,...) -- this is the sequencer's periodic
// tick (advance the BGM, key/release SPU2 voices). the reference delivered it
// through psx_bios_exception; here we arm the registered handler as a
// softcall (same path as the DMA-completion handlers). Rate-limited to
// one in-flight so a fast SPU2 IRQA cadence cannot flood the FIFO.
void EMU_CALL hle_ps2_spu_irq(void) {
    if (!g_ps2.irq9_cb) return;   // unset until libsd RegisterIntrHandler(9)
    if (hle_ps1_softcall_pending()) return;
    {
        static unsigned long long n = 0;
        if (getenv("IRQLOG")) {
            n++;
            if ((n % 500ULL) == 0) fprintf(stderr, "[spuirq] deliveries=%llu\n", n);
        }
    }
    call_irq_routine(&g_ps2, g_ps2.irq9_cb, g_ps2.irq9_flag, "SPU2");
}

void EMU_CALL hle_ps2_dma7(uint32 madr, uint32 bcr, uint32 chcr) {
    int delay, w;
    uint32 len;
    g_ps2.dma7_madr = madr;
    g_ps2.dma7_bcr = bcr;
    g_ps2.dma7_chcr = chcr;
    w = (chcr == 0x01000201) || (chcr == 0x00100010) ||
        (chcr == 0x000f0010) || (chcr == 0x00010010);
    len = (bcr >> 16) * (bcr & 0xffff) * 4;
    hle_ps2_spu_dma(1, madr, len, w ? 1 : 0);
    delay = (int) (((len / 2) * 8 /*cycles/halfword*/) / 768);
    if (!delay) delay = 1;
    g_ps2.dma7_delay = delay;
}

// True while an SPU2 DMA (core 0 = DMA4, core 1 = DMA7) is still in
// flight -- i.e. its completion delay has not yet counted down to zero
// in psx_hw_runcounters. libsd's sceSdVoiceTrans-style transfer code
// busy-polls the channel CHCR bit 24 to wait for the upload to finish;
// hle_dma_lw uses this to report that bit truthfully instead of always
// "done", so libsd actually waits for the sample upload (matching the
// BIOS path, whose IOP DMA channel keeps CHCR busy during the modelled
// transfer). Always-done made libsd allocate/serve voices before the
// sample data landed -> notes mapped to the wrong voices -> wrong
// pitches (song still recognisable).
int EMU_CALL hle_ps2_dma_busy(int core) {
    return core ? (g_ps2.dma7_delay != 0) : (g_ps2.dma4_delay != 0);
}

/////////////////////////////////////////////////////////////////////////////

static int g_ps2_inited;

void EMU_CALL hle_ps2_reset(void *iop) {
    memset(&g_ps2, 0, sizeof(g_ps2));
    g_ps2.psx_ram = (uint32 *) iop_get_ram(iop);
    g_ps2.mipscpu.r3000 = iop_get_r3000_state(iop);
    g_ps2.mipscpu.iop = iop;
    g_ps2.error_ptr = g_ps2.error_buffer;
    g_ps2.psf_refresh = 60;
    g_ps2.iCurThread = -1;
    g_ps2.iNumThreads = 1;            // thread 0 = the boot/main thread
    g_ps2.threads[0].iState = TS_RUNNING;
    g_ps2.iCurThread = 0;
    if (!g_ps2.elf_scratch)
        g_ps2.elf_scratch = (uint32 *) malloc(2 * 1024 * 1024);
    g_ps2_inited = 1;
}

// Read a module from the PSF2 filesystem (bridge set up at boot).
static sint32 ps2_readfile(void *ctx, const char *path, sint32 ofs,
                           char *buf, sint32 len) {
    (void) ctx;
    return hle_ps2_readfile_bridge(path, ofs, buf, len);
}

// Deferred boot: load psf2.irx, ELF-relocate, set the reference entry
// register contract. Returns the IRX entry PC, or 0xffffffff on error.
uint32 EMU_CALL hle_ps2_boot(void *iop) {
    uint8 *buf;
    uint32 pc;
    MIPS_CPU_CONTEXT *c;

    hle_ps2_reset(iop);
    c = &g_ps2.mipscpu;
    g_ps2.readfile = ps2_readfile;
    g_ps2.loadAddr = 0x23f00;

    buf = (uint8 *) malloc(512 * 1024);
    if (!buf) return 0xffffffff;
    {
        sint32 n = hle_ps2_readfile_bridge("psf2.irx", 0, (char *) buf, 512 * 1024);
        if (n <= 0) {
            free(buf);
            return 0xffffffff;
        }
        pc = psf2_load_elf(&g_ps2, buf, (uint32) n);
    }
    free(buf);
    if (getenv("HLE_IOP"))
        fprintf(stderr, "[ps2] psf2_load_elf -> pc=%08X loadAddr=%08X\n", pc, g_ps2.loadAddr);
    if (pc == 0xffffffff) return 0xffffffff;

    {
        union cpuinfo m;
        m.i = pc;
        mips_set_info(c, CPUINFO_INT_PC, &m);
        m.i = 0x801ffff0;
        mips_set_info(c, CPUINFO_INT_REGISTER + MIPS_R29, &m);
        mips_set_info(c, CPUINFO_INT_REGISTER + MIPS_R30, &m);
        m.i = 0x80000000;
        mips_set_info(c, CPUINFO_INT_REGISTER + MIPS_R31, &m);
        m.i = 2;
        mips_set_info(c, CPUINFO_INT_REGISTER + MIPS_R4, &m);
        m.i = 0x80000004;
        mips_set_info(c, CPUINFO_INT_REGISTER + MIPS_R5, &m);
    }
    g_ps2.psx_ram[1] = 0x80000008;
    strcpy((char *) &g_ps2.psx_ram[2], "aofile:/");
    return pc;
}

// Entry from hle.c on the addiu-$0 IOP-kernel stub.
// Returns the PC the CPU should resume at (the caller -- r3000.c
// caseMAJOR(0x09) -- then does its normal PC+=4, so for an unchanged PC
// we hand back the stub PC and the +4 advances past it; for a redirected
// PC (LoadStartModule entry, or a thread switch via ps2_reschedule) we
// hand back target-4 so the +4 lands exactly on the target).
uint32 EMU_CALL hle_ps2_iop_call(void *iop, uint32 pc, uint32 callnum) {
    union cpuinfo m;
    (void) pc;
    if (!g_ps2_inited) return pc;
    g_ps2.mipscpu.r3000 = iop_get_r3000_state(iop);
    g_ps2.mipscpu.iop = iop;
    g_ps2.error_ptr = g_ps2.error_buffer; // never accumulate (debug only)
    // Mirror the reference psx.c exactly: psx_iop_call (which already sets PC in
    // the "minus 4" convention for redirects, e.g. LoadStartModule does
    // PC = start-4), THEN advance_pc (+4), THEN reschedule (ThawThread
    // sets the next thread's exact PC). We then hand back finalPC-4 so
    // r3000.c caseMAJOR(0x09)'s unconditional PC+=4 lands on finalPC.
    {
        union cpuinfo pcb;
        mips_get_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &pcb);
        psx_iop_call(&g_ps2, pc, callnum);
        mips_get_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &m);
        if ((uint32) m.i != (uint32) pcb.i) {
            // psx_iop_call redirected PC itself (clean entry, e.g.
            // LoadStartModule PC=start-4) -- this is NOT a delay-slot branch
            // target, so cancel any pending delay-slot branch (the reference "we get
            // called in the delay slot"). Thread switches are handled below by
            // ps2_reschedule/ThawThread, which restore the resumed thread's
            // own DELAYV/DELAYR; we must not clear those.
            union cpuinfo z;
            z.i = 0;
            mips_set_info(&g_ps2.mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &z);
        }
    }
    m.i += 4;                                         // the reference mips_advance_pc
    mips_set_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &m);
    if (!g_ps2.irq_mutex && g_ps2.rescheduleNeeded) {
        g_ps2.rescheduleNeeded = 0;
        ps2_reschedule(&g_ps2);
    }
    // No runnable thread (e.g. the caller just SleepThread/DelayThread'd
    // and nothing else is ready): park the CPU on the idle-spin stub
    // instead of resuming the just-frozen thread's code. hle_advance keeps
    // ticking; the next hle_ps2_check_resched switches to the woken thread.
    // NOT while a ps2_sc IRQ/DMA handler is running (irq_mutex) -- it was
    // launched from the idle snapshot and must run to its SOFTCALL_RA
    // return, not be abandoned to the idle stub.
    if (g_ps2.iCurThread == -1 && !g_ps2.irq_mutex) {
        union cpuinfo z;
        z.i = 0;
        mips_set_info(&g_ps2.mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYR, &z);
        mips_set_info(&g_ps2.mipscpu, CPUINFO_INT_REGISTER + MIPS_DELAYV, &z);
        z.i = PS2_IDLE_PC;
        mips_set_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &z);
        return PS2_IDLE_PC - 4;            // hle_iop_call/+4 -> PS2_IDLE_PC
    }
    mips_get_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &m);
    return (uint32) m.i - 4;
}

// pc == 0x80000000: the running thread (or the psf2.irx loader) returned
// to its $ra sentinel -> it has exited. Mark it dormant and reschedule
// into the spawned audio thread(s). Returns the next thread PC, or
// 0xffffffff if the IOP is now idle (no runnable thread).
uint32 EMU_CALL hle_ps2_thread_exit(void *iop) {
    union cpuinfo m;
    if (!g_ps2_inited) return 0xffffffff;
    g_ps2.mipscpu.r3000 = iop_get_r3000_state(iop);
    g_ps2.mipscpu.iop = iop;
    if (g_ps2.iCurThread != -1)
        g_ps2.threads[g_ps2.iCurThread].iState = TS_DORMANT;
    g_ps2.rescheduleNeeded = 0;
    ps2_reschedule(&g_ps2);
    {
        static int n = 0;
        if (getenv("HLE_IOP") && n++ < 20)
            fprintf(stderr,
                    "[ps2] thread_exit -> nThreads=%d cur=%d\n", g_ps2.iNumThreads,
                    g_ps2.iCurThread);
    }
    if (g_ps2.iCurThread == -1) return PS2_IDLE_PC;   // park; timers wake a thread
    mips_get_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &m);
    return (uint32) m.i;
}

void EMU_CALL hle_ps2_runcounters(void *iop) {
    if (!g_ps2_inited) return;
    g_ps2.mipscpu.r3000 = iop_get_r3000_state(iop);
    g_ps2.mipscpu.iop = iop;
    // hle_advance runs this from r3000 hw_sync -- i.e. MID-INSTRUCTION.
    // psx_hw_runcounters only does timer/sys_time bookkeeping and sets
    // rescheduleNeeded/timerexp (matches the reference -- it never reschedules
    // here). ps2_reschedule must NOT run mid-instruction (it
    // Freeze/ThawThread-swaps the CPU PC+regs while r3000 is mid lw/sw,
    // corrupting execution -- the libsd-init hang). The deferred
    // reschedule is taken at the safe instruction boundary in
    // hle_ps2_check_resched(), called from r3000.c between instructions.
    psx_hw_runcounters(&g_ps2);
}

// the reference eng_psf2.c psf2_gen frame model. psf2_gen maintained
// psx->vblank_samples_until_next (the count of output samples until the
// next emulated VBlank) per sample and called ps2_hw_frame at each frame
// boundary. Our dedicated per-sample pump (iop.c) replaced psf2_gen
// wholesale and dropped this bookkeeping, so vblank_samples_until_next
// stayed 0 forever -- WaitVblankStart then computes
// waitparm = 768 * 0 = 0 and returns immediately, so the IOP sound
// thread never actually sleeps a video frame. It races ~7x ahead of SPU
// playback: the SPU2 register writes are correct but ~7x time-compressed
// (verified vs the BIOS oracle: identical write stream, 34547 vs 238196
// samples of spread). Restoring the per-sample frame model makes the
// vblank wait a real multi-sample sleep, matching the BIOS/the reference tempo.
// Called once per output sample from the pump, right after
// hle_ps2_runcounters (== the reference ps2_hw_slice's psx_hw_runcounters).
void EMU_CALL hle_ps2_frame_tick(void) {
    if (!g_ps2_inited) return;
    PSX_STATE *psx = &g_ps2;
    int spf = (psx->psf_refresh == 50) ? 960 : 800;   // the reference samples_per_frame
    if (psx->vblank_samples_until_next <= 0)
        psx->vblank_samples_until_next = spf;
    if (--psx->vblank_samples_until_next == 0) {
        psx->vblank_samples_until_next = spf;
        // the reference ps2_hw_frame: ps2_reschedule(psx) if !irq_mutex. Our arch
        // defers the actual Freeze/ThawThread swap to the safe instruction
        // boundary (hle_ps2_check_resched) to avoid the documented
        // mid-instruction corruption, so just arm the deferred reschedule.
        if (!psx->irq_mutex)
            psx->rescheduleNeeded = 1;
    }
}

// Called from r3000.c at a safe instruction boundary (not mid lw/sw).
// Performs a pending cooperative reschedule. Returns the thread PC to
// resume at (so r3000 can apply it cleanly), or 0xffffffff for none.
uint32 EMU_CALL hle_ps2_check_resched(void *iop) {
    union cpuinfo m;
    if (!g_ps2_inited || g_ps2.irq_mutex || !g_ps2.rescheduleNeeded)
        return 0xffffffff;
    g_ps2.mipscpu.r3000 = iop_get_r3000_state(iop);
    g_ps2.mipscpu.iop = iop;
    g_ps2.rescheduleNeeded = 0;
    ps2_reschedule(&g_ps2);
    if (g_ps2.iCurThread == -1) return PS2_IDLE_PC;   // park; timers wake a thread
    mips_get_info(&g_ps2.mipscpu, CPUINFO_INT_PC, &m);
    return (uint32) m.i;
}

int EMU_CALL hle_ps2_rescheduling(void) { return g_ps2.rescheduleNeeded; }
