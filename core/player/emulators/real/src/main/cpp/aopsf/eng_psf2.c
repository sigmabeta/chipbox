/*
	Audio Overload SDK - PSF2 file format engine

	Copyright (c) 2007-2008 R. Belmont and Richard Bannister.

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
// eng_psf2.c
//
// References:
// psf_format.txt v1.6 by Neill Corlett (filesystem and decompression info)
// Intel ELF format specs ELF.PS (general ELF parsing info)
// http://ps2dev.org/kb.x?T=457 (IRX relocation and inter-module call info)
// http://ps2dev.org/ (the whole site - lots of IOP info)
// spu2regs.txt (comes with SexyPSF source: IOP hardware info)
// 64-bit ELF Object File Specification: http://techpubs.sgi.com/library/manuals/4000/007-4658-001/pdf/007-4658-001.pdf (MIPS ELF relocation types)

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "ao.h"
#include "cpuintrf.h"
#include "psx.h"
#include "psx_internal.h"

#include "spu/spu.h"

// ELF relocation helpers
#define ELF32_R_SYM(val)                ((val) >> 8)
#define ELF32_R_TYPE(val)               ((val) & 0xff)

// main RAM

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

#if 0
static dump_files(int fs, uint8 *buf, uint32 buflen)
{
    int32 numfiles, i, j;
    uint8 *cptr;
    uint32 offs, uncomp, bsize, cofs, uofs;
    uint32 X;
    uLongf dlength;
    int uerr;
    uint8 *start;
    uint32 len;
    FILE *f;
    char tfn[128];

    printf("Dumping FS %d\n", fs);

    start = filesys[fs];
    len = fssize[fs];

    cptr = start + 4;

    numfiles = start[0] | start[1]<<8 | start[2]<<16 | start[3]<<24;

    for (i = 0; i < numfiles; i++)
    {
        offs = cptr[36] | cptr[37]<<8 | cptr[38]<<16 | cptr[39]<<24;
        uncomp = cptr[40] | cptr[41]<<8 | cptr[42]<<16 | cptr[43]<<24;
        bsize = cptr[44] | cptr[45]<<8 | cptr[46]<<16 | cptr[47]<<24;

        if (bsize > 0)
        {
            X = (uncomp + bsize - 1) / bsize;

            printf("[dump %s]: ofs %08x uncomp %08x bsize %08x\n", cptr, offs, uncomp, bsize);

            cofs = offs + (X*4);
            uofs = 0;
            for (j = 0; j < X; j++)
            {
                uint32 usize;

                usize = start[offs+(j*4)] | start[offs+1+(j*4)]<<8 | start[offs+2+(j*4)]<<16 | start[offs+3+(j*4)]<<24;

                dlength = buflen - uofs;

                uerr = uncompress(&buf[uofs], &dlength, &start[cofs], usize);
                if (uerr != Z_OK)
                {
                    printf("Decompress fail: %x %d!\n", dlength, uerr);
                    return 0xffffffff;
                }

                cofs += usize;
                uofs += dlength;
            }

            sprintf(tfn, "iopfiles/%s", cptr);
            f = fopen(tfn, "wb");
            fwrite(buf, uncomp, 1, f);
            fclose(f);
        }
        else
        {
            printf("[subdir %s]: ofs %08x uncomp %08x bsize %08x\n", cptr, offs, uncomp, bsize);
        }

        cptr += 48;
    }

    return 0xffffffff;
}
#endif

// find a file on our filesystems
uint32 psf2_load_file(PSX_STATE *psx, const char *file, uint8 *buf, uint32 buflen) {
    int i = psx->readfile(psx->readfile_context, file, 0, (char *) buf, buflen);
    if (i < 0)
        return 0xffffffff;
    else
        return i;
}

int32 psf2_start(PSX_STATE *psx) {
    uint32 irx_len;
    uint8 *buf;
    union cpuinfo mipsinfo;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

#if DEBUG_DISASM
    psx->mipscpu.file = fopen("/tmp/moo.txt", "w");
#endif

    psx->loadAddr = 0x23f00;    // this value makes allocations work out similarly to how they would
    // in Highly Experimental (as per Shadow Hearts' hard-coded assumptions)

    // clear IOP work RAM before we start scribbling in it
    memset(psx->psx_ram, 0, 2 * 1024 * 1024);

    // load psf2.irx, which kicks everything off
    buf = (uint8 *) malloc(512 * 1024);
    irx_len = psf2_load_file(psx, "psf2.irx", buf, 512 * 1024);

    if (irx_len != 0xffffffff) {
        psx->initialPC = psf2_load_elf(psx, buf, irx_len);
        psx->initialSP = 0x801ffff0;
    }
    free(buf);

    if (psx->initialPC == 0xffffffff) {
        psx->error_ptr += sprintf(psx->error_ptr, "Invalid psf2.irx.\n");
        return AO_FAIL;
    }

    mips_init(&psx->mipscpu);
    mips_reset(&psx->mipscpu, NULL);

    mipsinfo.i = psx->initialPC;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

    mipsinfo.i = psx->initialSP;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);

    // set RA
    mipsinfo.i = 0x80000000;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);

    // set A0 & A1 to point to "aofile:/"
    mipsinfo.i = 2;    // argc
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);

    mipsinfo.i = 0x80000004;    // argv
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R5, &mipsinfo);
    psx->psx_ram[1] = LE32(0x80000008);

    buf = (uint8 *) &psx->psx_ram[2];
    strcpy((char *) buf, "aofile:/");

    psx->psx_ram[0] = LE32(FUNCT_HLECALL);

    // back up initial RAM image to quickly restart songs
    memcpy(psx->initial_ram, psx->psx_ram, 2 * 1024 * 1024);

    psx_hw_init(psx, 2);
    spu_clear_state(SPUSTATE, 2);

    return AO_SUCCESS;
}

int32 psf2_gen(PSX_STATE *psx, int16 *buffer, uint32 samples) {
    int i;

    const int samples_per_frame = psx->psf_refresh == 50 ? 960 : 800;

    int samples_into_frame = psx->samples_into_frame;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

    spu_set_buffer(SPUSTATE, buffer, samples);

    while (samples) {
        int samples_to_do = samples_per_frame - samples_into_frame;
        int samples_until_vblank = samples_to_do;
        if (samples_to_do > samples)
            samples_to_do = samples;

        psx->vblank_samples_until_next = samples_until_vblank;

        for (i = 0; i < samples_to_do; i++) {
            spu_advance(SPUSTATE, 1);
            ps2_hw_slice(psx);
            if (--psx->vblank_samples_until_next == 0)
                psx->vblank_samples_until_next = samples_per_frame;
        }

        samples_into_frame += samples_to_do;
        if (samples_into_frame >= samples_per_frame)
            ps2_hw_frame(psx), samples_into_frame = 0;

        samples -= samples_to_do;
        if (buffer) buffer += samples_to_do * 2;
    }

    spu_flush(SPUSTATE);

    psx->samples_into_frame = samples_into_frame;

    if (psx->stop)
        return AO_FAIL;

    return AO_SUCCESS;
}

int32 psf2_stop(PSX_STATE *psx) {
    int i;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

    for (i = 0; i < MAX_FILE_SLOTS; i++) {
        if (psx->filestat[i]) {
            free(psx->filename[i]);
            psx->filename[i] = 0;
            psx->filestat[i] = 0;
        }
    }

#if DEBUG_DISASM
    fclose(psx->mipscpu.file);
#endif

    return AO_SUCCESS;
}

int32 psf2_command(PSX_STATE *psx, int32 command, int32 parameter) {
    union cpuinfo mipsinfo;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

    switch (command) {
        case COMMAND_RESTART:

            memcpy(psx->psx_ram, psx->initial_ram, 2 * 1024 * 1024);

            mips_init(&psx->mipscpu);
            mips_reset(&psx->mipscpu, NULL);
            psx_hw_init(psx, 2);
            spu_clear_state(SPUSTATE, 2);

            mipsinfo.i = psx->initialPC;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

            mipsinfo.i = psx->initialSP;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);

            // set RA
            mipsinfo.i = 0x80000000;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R31, &mipsinfo);

            // set A0 & A1 to point to "aofile:/"
            mipsinfo.i = 2;    // argc
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R4, &mipsinfo);

            mipsinfo.i = 0x80000004;    // argv
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R5, &mipsinfo);

            psx_hw_init(psx, 2);

            return AO_SUCCESS;

    }
    return AO_FAIL;
}

uint32 psf2_get_loadaddr(PSX_STATE *psx) {
    return psx->loadAddr;
}

void psf2_set_loadaddr(PSX_STATE *psx, uint32 new) {
    psx->loadAddr = new;
}

void psf2_register_readfile(PSX_STATE *psx, virtual_readfile function, void *context) {
    psx->readfile = function;
    psx->readfile_context = context;
}
