/*
	Audio Overload SDK - PSF file format engine

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

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "ao.h"
#include "cpuintrf.h"
#include "psx.h"
#include "psx_internal.h"

#include "spu/spu.h"

#ifdef _MSC_VER
#define strncasecmp _strnicmp
#endif

static uint32 get_le32(const uint8 *start) {
    return start[0] | start[1] << 8 | start[2] << 16 | start[3] << 24;
}

uint32 psf_load_section(PSX_STATE *psx, const uint8 *buffer, uint32 length, uint32 first) {
    //uint32 plength_truncat = 0;
    uint32 offset, plength, PC, SP, GP;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

    if (strncmp((char *) buffer, "PS-X EXE", 8) || length < 2048) {
        psx->error_ptr += sprintf(psx->error_ptr, "Invalid PSX EXE signature.\n");
        return 0xffffffff;
    }

    PC = get_le32(buffer + 0x10);
    GP = get_le32(buffer + 0x14);
    SP = get_le32(buffer + 0x30);

    offset = get_le32(buffer + 0x18);
    offset &= 0x3fffffff;    // kill any MIPS cache segment indicators
    plength = get_le32(buffer + 0x1c);

    if (length < 2048 + plength) {
        //return 0xffffffff;
        //plength_truncat = (2048 + plength) - length;
        plength = length - 2048;
    }

    if (first) {
        memset(psx->psx_ram, 0, 2 * 1024 * 1024);
    }

    if (!psx->psf_refresh) {
        if (!strncasecmp((const char *) buffer + 113, "Japan", 5)) psx->psf_refresh = 60;
        else if (!strncasecmp((const char *) buffer + 113, "Europe", 6)) psx->psf_refresh = 50;
        else if (!strncasecmp((const char *) buffer + 113, "North America", 13))
            psx->psf_refresh = 60;
    }

    memcpy(&psx->psx_ram[offset / 4], buffer + 2048, plength);
    /*if (plength_truncat)
    {
        memset(&psx->psx_ram[plength/4], 0, plength_truncat);
    }*/

    if (first) {
        psx->initialPC = PC;
        psx->initialSP = SP;
        psx->initialGP = GP;
    }

    return 0;
}

int32 psf_start(PSX_STATE *psx) {
    int i;
    union cpuinfo mipsinfo;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

#if DEBUG_DISASM
    psx->mipscpu.file = fopen("/tmp/moo.txt", "w");
#endif

    // clear PSX work RAM before we start scribbling in it
    // cleared by "first" call to psf_load_section
    //memset(psx_ram, 0, 2*1024*1024);

//	printf("Length = %d\n", length);

    mips_init(&psx->mipscpu);
    mips_reset(&psx->mipscpu, NULL);

    // set the initial PC, SP, GP
#if DEBUG_LOADER
    printf("Initial PC %x, GP %x, SP %x\n", psx->initialPC, psx->initialGP, psx->initialSP);
    printf("Refresh = %d\n", psx->psf_refresh);
#endif
    mipsinfo.i = psx->initialPC;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);

    // set some reasonable default for the stack
    if (psx->initialSP == 0) {
        psx->initialSP = 0x801fff00;
    }

    mipsinfo.i = psx->initialSP;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);

    mipsinfo.i = psx->initialGP;
    mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R28, &mipsinfo);

#if DEBUG_LOADER && 1
    {
        FILE *f;

        f = fopen("psxram.bin", "wb");
        fwrite(psx->psx_ram, 2*1024*1024, 1, f);
        fclose(f);
    }
#endif

    psx_hw_init(psx, 1);
    spu_clear_state(SPUSTATE, 1);

#if 0
    // patch illegal Chocobo Dungeon 2 code - CaitSith2 put a jump in the delay slot from a BNE
    // and rely on Highly Experimental's buggy-ass CPU to rescue them.  Verified on real hardware
    // that the initial code is wrong.
    if (c->inf_game)
    {
        if (!strcmp(c->inf_game, "Chocobo Dungeon 2"))
        {
            if (psx_ram[0xbc090/4] == LE32(0x0802f040))
            {
                 psx_ram[0xbc090/4] = LE32(0);
                psx_ram[0xbc094/4] = LE32(0x0802f040);
                psx_ram[0xbc098/4] = LE32(0);
            }
        }
    }
#endif

//	psx_ram[0x118b8/4] = LE32(0);	// crash 2 hack

    // backup the initial state for restart
    memcpy(psx->initial_ram, psx->psx_ram, 2 * 1024 * 1024);

    mips_execute(&psx->mipscpu, 5000);

    return AO_SUCCESS;
}

int32 psf_gen(PSX_STATE *psx, int16 *buffer, uint32 samples) {
    int i;

    const int samples_per_frame = psx->psf_refresh == 50 ? 882 : 735;

    int samples_into_frame = psx->samples_into_frame;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

    int framesWritten = 0;

    while (samples) {
        int samples_to_do = samples_per_frame - samples_into_frame;
        if (samples_to_do > samples)
            samples_to_do = samples;

        spu_set_buffer(SPUSTATE, buffer, samples_to_do);

        for (i = 0; i < samples_to_do; i++) {
            psx_hw_slice(psx);
            spu_advance(SPUSTATE, 1);
            framesWritten++;
        }

        samples_into_frame += samples_to_do;
        if (samples_into_frame >= samples_per_frame)
            psx_hw_frame(psx), samples_into_frame = 0;

        spu_flush(SPUSTATE);

        samples -= samples_to_do;
        if (buffer) buffer += samples_to_do * 2;
    }

    psx->samples_into_frame = samples_into_frame;

    if (psx->stop)
        return AO_FAIL;

    return framesWritten;
}

int32 psf_stop(PSX_STATE *psx) {
    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

#if DEBUG_DISASM
    fclose(psx->mipscpu.file);
#endif
    return AO_SUCCESS;
}

int32 psf_command(PSX_STATE *psx, int32 command, int32 parameter) {
    union cpuinfo mipsinfo;

    psx->error_ptr = psx->error_buffer;
    psx->error_buffer[0] = '\0';

    switch (command) {
        case COMMAND_RESTART:
            memcpy(psx->psx_ram, psx->initial_ram, 2 * 1024 * 1024);

            mips_init(&psx->mipscpu);
            mips_reset(&psx->mipscpu, NULL);
            psx_hw_init(psx, 1);
            spu_clear_state(SPUSTATE, 1);

            mipsinfo.i = psx->initialPC;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_PC, &mipsinfo);
            mipsinfo.i = psx->initialSP;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R29, &mipsinfo);
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R30, &mipsinfo);
            mipsinfo.i = psx->initialGP;
            mips_set_info(&psx->mipscpu, CPUINFO_INT_REGISTER + MIPS_R28, &mipsinfo);

            mips_execute(&psx->mipscpu, 5000);

            return AO_SUCCESS;

    }
    return AO_FAIL;
}
