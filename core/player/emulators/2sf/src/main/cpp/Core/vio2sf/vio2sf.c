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
#include "desmume/barray.h"

#include "../../common/common.h"

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

void add_le32(u8 ** start_ptr, u8 ** current_ptr, u8 ** end_ptr, u32 word)
{
	if (*end_ptr - *current_ptr < 4)
	{
		size_t current_offset = *current_ptr - *start_ptr;
		size_t current_size = *end_ptr - *start_ptr;
		u8 * new_block = (u8 *) realloc( *start_ptr, current_size + 1024 );
		if (!new_block) return;
		*start_ptr = new_block;
		*current_ptr = new_block + current_offset;
		*end_ptr = new_block + current_size + 1024;
	}

	(*current_ptr)[0] = word;
	(*current_ptr)[1] = word >> 8;
	(*current_ptr)[2] = word >> 16;
	(*current_ptr)[3] = word >> 24;

	*current_ptr += 4;
}

void add_block(u8 ** start_ptr, u8 ** current_ptr, u8 ** end_ptr, const u8 * block, u32 size)
{
	if (*end_ptr - *current_ptr < size)
	{
		size_t current_offset = *current_ptr - *start_ptr;
		size_t current_size = *end_ptr - *start_ptr;
		u8 * new_block = (u8 *) realloc( *start_ptr, current_offset + size + 1024 );
		if (!new_block) return;
		*start_ptr = new_block;
		*current_ptr = new_block + current_offset;
		*end_ptr = new_block + current_offset + size + 1024;
	}

	memcpy(*current_ptr, block, size);

	*current_ptr += size;
}

void add_block_zero(u8 ** start_ptr, u8 ** current_ptr, u8 ** end_ptr, u32 size)
{
	if (*end_ptr - *current_ptr < size)
	{
		size_t current_offset = *current_ptr - *start_ptr;
		size_t current_size = *end_ptr - *start_ptr;
		u8 * new_block = (u8 *) realloc( *start_ptr, current_offset + size + 1024 );
		if (!new_block) return;
		*start_ptr = new_block;
		*current_ptr = new_block + current_offset;
		*end_ptr = new_block + current_offset + size + 1024;
	}

	memset(*current_ptr, 0, size);

	*current_ptr += size;
}

NDS_state * core;
struct twosf_loader_state state;

//int xsf_start(void *pfile, unsigned bytes)
int xsf_start(char *filename)
{
	core = ( NDS_state * ) calloc(1, sizeof(NDS_state));

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
		if (state.rom) free(state.rom);
		if (state.state) free(state.state);

		return -1;
	}

	if (state_init(core)) {
		state_deinit(core);
		if (state.rom) free(state.rom);
		if (state.state) free(state.state);

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
		state_setrom(core, state.rom, (u32) state.rom_size, 1);
	}

	state_loadstate(core, state.state, (u32) state.state_size );

	if (state.state) {
		free(state.state);
	}

	return 0;
}

int xsf_gen(void *pbuffer, unsigned samples)
{
	state_render(core, (s16*)pbuffer, samples);

	return samples;
}

void xsf_term(void)
{
	state_deinit(core);
	if (state.rom) free(state.rom);
}
