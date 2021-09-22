/*  SPU.h

	Copyright 2006 Theo Berkau
    Copyright (C) 2006-2009 DeSmuME team

    This file is part of DeSmuME

    DeSmuME is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    DeSmuME is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with DeSmuME; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

#ifndef SPU_H
#define SPU_H

#include <string.h>
#include "types.h"
#include <math.h>
#include <assert.h>
#include <stdbool.h>

#include "resampler.h"

u32 u32floor_float(float f)
{
#ifdef ENABLE_SSE2
	return (u32)_mm_cvtt_ss2si(_mm_set_ss(f));
#else
	return (u32)f;
#endif
}

u32 u32floor_double(double d)
{
#ifdef ENABLE_SSE2
	return (u32)_mm_cvttsd_si32(_mm_set_sd(d));
#else
	return (u32)d;
#endif
}

//same as above but works for negative values too.
//be sure that the results are the same thing as floorf!
s32 s32floor(float f)
{
#ifdef ENABLE_SSE2
	return _mm_cvtss_si32( _mm_add_ss(_mm_set_ss(-0.5f),_mm_add_ss(_mm_set_ss(f), _mm_set_ss(f))) ) >> 1;
#else
	return (s32)floorf(f);
#endif
}

s32 spumuldiv7(s32 val, u8 multiplier) {
	assert(multiplier <= 127);
	return (multiplier == 127) ? val : ((val * multiplier) >> 7);
}

#define SNDCORE_DEFAULT         -1
#define SNDCORE_DUMMY           0

#define CHANSTAT_STOPPED          0
#define CHANSTAT_PLAY             1
#define CHANSTAT_EMPTYBUFFER      2

enum SPUInterpolationMode
{
	SPUInterpolation_None = 0,
    SPUInterpolation_Blep = 1,
	SPUInterpolation_Linear = 2,
    SPUInterpolation_Cubic = 3,
	SPUInterpolation_Sinc = 4
};

typedef struct NDS_state NDS_state;

extern SoundInterface_struct SNDDummy;
extern SoundInterface_struct SNDFile;

static bool resampler_initialized = false;

SoundInterface_struct *SPU_SoundCore(NDS_state *);

void SPU_Pause(NDS_state *, int pause);
void SPU_SetVolume(NDS_state *, int volume);
void SPU_KeyOn(NDS_state *, int channel);
void SPU_Emulate_core(NDS_state *);




void WAV_End(NDS_state *);
bool WAV_Begin(NDS_state *, const char* fname);
bool WAV_IsRecording(NDS_state *);
void WAV_WavSoundUpdate(NDS_state *, void* soundData, int numSamples);

#endif
