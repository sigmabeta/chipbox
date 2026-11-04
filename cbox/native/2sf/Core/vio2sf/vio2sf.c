#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

#include "desmume/MMU.h"
#include "desmume/armcpu.h"
#include "desmume/NDSSystem.h"
#include "desmume/SPU.hpp"
#include "desmume/cp15.h"

#include "desmume/state.h"

#include <chipbox_psf_io.h>

struct twosf_loader_state
{
	uint8_t * rom;
	uint8_t * state;
	size_t rom_size;
	size_t state_size;

	int initial_frames;
	int sync_type;
	int clockdown;
	int arm9_clockdown_level;
	int arm7_clockdown_level;
};

NDS_state * core;
struct twosf_loader_state state;

/* A loader failure after ownership moved into the loader state must not leave a
 * dangling pointer/size: the caller's error path frees whatever is still there,
 * so a stale value would be a double free. */
static void twosf_clear_loader_slot(struct twosf_loader_state *loader, int issave)
{
	if (issave)
	{
		loader->state = 0;
		loader->state_size = 0;
	}
	else
	{
		loader->rom = 0;
		loader->rom_size = 0;
	}
}

/* Frees the process-global core exactly once, unmapping the ROM from the MMU
 * (via state_deinit) before the loader state releases the ROM buffer. */
static void twosf_release_core(void)
{
	if (core)
	{
		state_deinit(core);
		free(core);
		core = 0;
	}
}

/* Frees every buffer owned by the loader state. The ROM is mapped into the
 * core for the whole track, so this must only run after the core is gone (or
 * after a failed load that never mapped it). */
static void twosf_release_loader_state(void)
{
	if (state.rom)
	{
		free(state.rom);
		state.rom = 0;
	}
	state.rom_size = 0;

	if (state.state)
	{
		free(state.state);
		state.state = 0;
	}
	state.state_size = 0;
}

static int load_twosf_map(struct twosf_loader_state *state, int issave, const unsigned char *udata, unsigned usize)
{
	if (usize < 8) return -1;

	unsigned char *iptr;
	size_t isize;
	unsigned char *xptr;
	unsigned xsize = get_le32(udata + 4);
	unsigned xofs = get_le32(udata + 0);
	if (issave)
	{
		iptr = state->state;
		isize = state->state_size;
		state->state = 0;
		state->state_size = 0;
	}
	else
	{
		iptr = state->rom;
		isize = state->rom_size;
		state->rom = 0;
		state->rom_size = 0;
	}
	if (!iptr)
	{
		size_t rsize = xofs + xsize;
		if (!issave)
		{
			rsize -= 1;
			rsize |= rsize >> 1;
			rsize |= rsize >> 2;
			rsize |= rsize >> 4;
			rsize |= rsize >> 8;
			rsize |= rsize >> 16;
			rsize += 1;
		}
		iptr = (unsigned char *) malloc(rsize + 10);
		if (!iptr)
			return -1;
		memset(iptr, 0, rsize + 10);
		isize = rsize;
	}
	else if (isize < xofs + xsize)
	{
		size_t rsize = xofs + xsize;
		if (!issave)
		{
			rsize -= 1;
			rsize |= rsize >> 1;
			rsize |= rsize >> 2;
			rsize |= rsize >> 4;
			rsize |= rsize >> 8;
			rsize |= rsize >> 16;
			rsize += 1;
		}
		xptr = (unsigned char *) realloc(iptr, xofs + rsize + 10);
		if (!xptr)
		{
			/* Ownership had already moved out of the loader state; clear the
			 * slot so the caller's error path cannot free the pointer a second
			 * time, then release the buffer here (exactly once). */
			twosf_clear_loader_slot(state, issave);
			free(iptr);
			return -1;
		}
		iptr = xptr;
		isize = rsize;
	}
	memcpy(iptr + xofs, udata + 8, xsize);
	if (issave)
	{
		state->state = iptr;
		state->state_size = isize;
	}
	else
	{
		state->rom = iptr;
		state->rom_size = isize;
	}
	return 0;
}

static int load_twosf_mapz(struct twosf_loader_state *state, int issave, const unsigned char *zdata, unsigned zsize, unsigned zcrc)
{
	int ret;
	int zerr;
	uLongf usize = 8;
	uLongf rsize = usize;
	unsigned char *udata;
	unsigned char *rdata;

	udata = (unsigned char *) malloc(usize);
	if (!udata)
		return -1;

	while (Z_OK != (zerr = uncompress(udata, &usize, zdata, zsize)))
	{
		if (Z_MEM_ERROR != zerr && Z_BUF_ERROR != zerr)
		{
			free(udata);
			return -1;
		}
		if (usize >= 8)
		{
			usize = get_le32(udata + 4) + 8;
			if (usize < rsize)
			{
				rsize += rsize;
				usize = rsize;
			}
			else
				rsize = usize;
		}
		else
		{
			rsize += rsize;
			usize = rsize;
		}
		rdata = (unsigned char *) realloc(udata, usize);
		if (!rdata)
		{
			free(udata);
			return -1;
		}
		udata = rdata;
	}

	rdata = (unsigned char *) realloc(udata, usize);
	if (!rdata)
	{
		free(udata);
		return -1;
	}

	if (0)
	{
		uLong ccrc = crc32(crc32(0L, Z_NULL, 0), rdata, (uInt) usize);
		if (ccrc != zcrc)
			return -1;
	}

	ret = load_twosf_map(state, issave, rdata, (unsigned) usize);
	free(rdata);
	return ret;
}

static int twosf_loader(void * context, const uint8_t * exe, size_t exe_size,
						const uint8_t * reserved, size_t reserved_size)
{
	struct twosf_loader_state * state = ( struct twosf_loader_state * ) context;

	if ( exe_size >= 8 )
	{
		if ( load_twosf_map(state, 0, exe, (unsigned) exe_size) )
			return -1;
	}

	if ( reserved_size )
	{
		size_t resv_pos = 0;
		if ( reserved_size < 16 )
			return -1;
		while ( resv_pos + 12 < reserved_size )
		{
			unsigned save_size = get_le32(reserved + resv_pos + 4);
			unsigned save_crc = get_le32(reserved + resv_pos + 8);
			if (get_le32(reserved + resv_pos + 0) == 0x45564153)
			{
				if (resv_pos + 12 + save_size > reserved_size)
					return -1;
				if (load_twosf_mapz(state, 1, reserved + resv_pos + 12, save_size, save_crc))
					return -1;
			}
			resv_pos += 12 + save_size;
		}
	}

	return 0;
}

static int twosf_info(void * context, const char * name, const char * value)
{
	struct twosf_loader_state * state = ( struct twosf_loader_state * ) context;
	char * end;

	if ( !strcasecmp(name, "_frames") )
	{
		state->initial_frames = strtoul(value, &end, 10);
	}
	else if ( !strcasecmp(name, "_clockdown") )
	{
		state->clockdown = strtoul(value, &end, 10);
	}
	else if ( !strcasecmp(name, "_vio2sf_sync_type") )
	{
		state->sync_type = strtoul(value, &end, 10);
	}
	else if ( !strcasecmp(name, "_vio2sf_arm9_clockdown_level") )
	{
		state->arm9_clockdown_level = strtoul(value, &end, 10);
	}
	else if ( !strcasecmp(name, "_vio2sf_arm7_clockdown_level") )
	{
		state->arm7_clockdown_level = strtoul(value, &end, 10);
	}

	return 0;
}

int xsf_start(char *filename)
{
	/* Defensive: a load that failed leaves Kotlin's hasLoadedTrack false, so the
	 * next loadTrack does not call teardown first. A bare second xsf_start (WASM
	 * callers, a wrapper bug) must not overwrite the previous core/buffers. */
	twosf_release_core();
	twosf_release_loader_state();

	core = ( NDS_state * ) calloc(1, sizeof(NDS_state));
	if (!core)
		return -2;

	memset( &state, 0, sizeof(state) );
	state.initial_frames = -1;

	int result = psf_load(
			filename,
			&psf_file_system,
			0x24,
			twosf_loader,
			&state,
			twosf_info,
			&state,
			1,
			0,
			0
	);

	if (result <= 0) {
		twosf_release_loader_state();
		twosf_release_core();

		return -1;
	}

	if (state_init(core)) {
		state_deinit(core);
		free(core);
		core = 0;
		twosf_release_loader_state();

		return -2;
	}

	core->dwInterpolation = 0;
	core->dwChannelMute = 0;

	if (!state.arm7_clockdown_level) {
		state.arm7_clockdown_level = state.clockdown;
	}

	if (!state.arm9_clockdown_level) {
		state.arm9_clockdown_level = state.clockdown;
	}

	core->initial_frames = state.initial_frames;
	core->sync_type = state.sync_type;
	core->arm7_clockdown_level = state.arm7_clockdown_level;
	core->arm9_clockdown_level = state.arm9_clockdown_level;

	if (state.rom) {
		state_setrom(core, state.rom, (u32) state.rom_size);
	}

	state_loadstate(core, state.state, (u32) state.state_size );

	/* The core copies what it needs out of the SAVE image during the restore,
	 * so the buffer is not needed for the life of the track. */
	if (state.state) {
		free(state.state);
		state.state = NULL;
		state.state_size = 0;
	}

	return 0;
}

int xsf_gen(void *pbuffer, unsigned samples)
{
	if (!core)
		return 0;

	state_render(core, (s16*)pbuffer, samples);

	return samples;
}

void xsf_term(void) {
	twosf_release_core();
	twosf_release_loader_state();
}
