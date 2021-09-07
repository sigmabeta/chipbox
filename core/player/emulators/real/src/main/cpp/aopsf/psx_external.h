/*
	Audio Overload SDK - PSX emulation interface

	Copyright (c) 2007 R. Belmont and Richard Bannister.
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

/*
    psx_external.h - External interface to minimal console innards

*/

#ifndef _PSX_EXTERNAL_H_
#define _PSX_EXTERNAL_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "ao.h"

typedef struct psx_state PSX_STATE;

extern uint32 psx_get_state_size(uint32 version);

extern void psx_set_refresh(PSX_STATE *psx, uint32 refresh);

extern const char *psx_get_last_error(PSX_STATE *psx);

typedef void(*psx_console_callback_t)(void *context, const char *message);

extern void
psx_register_console_callback(PSX_STATE *psx, psx_console_callback_t callback, void *context);

extern uint32 psf_load_section(PSX_STATE *psx, const uint8 *buffer, uint32 length, uint32 first);

extern int32 psf_start(PSX_STATE *psx);

extern int32 psf_gen(PSX_STATE *psx, int16 *buffer, uint32 samples);

extern int32 psf_stop(PSX_STATE *psx);

extern int32 psf_command(PSX_STATE *psx, int32 command, int32 parameter);

typedef int (*virtual_readfile)(void *context, const char *path, int offset, char *buffer,
                                int length);

extern void psf2_register_readfile(PSX_STATE *psx, virtual_readfile function, void *context);

extern int32 psf2_start(PSX_STATE *psx);

extern int32 psf2_gen(PSX_STATE *psx, int16 *buffer, uint32 samples);

extern int32 psf2_stop(PSX_STATE *state);

extern int32 psf2_command(PSX_STATE *state, int32 command, int32 parameter);

#ifdef __cplusplus
}
#endif

#endif
