/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *   Mupen64plus - rom.c                                                   *
 *   Mupen64Plus homepage: https://mupen64plus.org/                        *
 *   Copyright (C) 2008 Tillin9                                            *
 *   Copyright (C) 2002 Hacktarux                                          *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.          *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

#include <ctype.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

#define M64P_CORE_PROTOTYPES 1
#include "api/callbacks.h"
#include "api/config.h"
#include "api/m64p_config.h"
#include "api/m64p_types.h"
#include "device/device.h"
#include "main.h"
#include "osal/files.h"
#include "osal/preproc.h"
#include "osd/osd.h"
#include "rom.h"
#include "util.h"

#define CHUNKSIZE 1024*128 /* Read files 128KB at a time. */

/* Number of cpu cycles per instruction */
enum { DEFAULT_COUNT_PER_OP = 2 };
/* by default, extra mem is enabled */
enum { DEFAULT_DISABLE_EXTRA_MEM = 0 };
/* Default SI DMA duration */
enum { DEFAULT_SI_DMA_DURATION = 0x900 };

static _romdatabase g_romdatabase;

/* Global loaded rom size. */
int g_rom_size = 0;

m64p_rom_header   ROM_HEADER;
rom_params        ROM_PARAMS;
m64p_rom_settings ROM_SETTINGS;

static m64p_system_type rom_country_code_to_system_type(uint16_t country_code);

static const uint8_t Z64_SIGNATURE[4] = { 0x80, 0x37, 0x12, 0x40 };
static const uint8_t V64_SIGNATURE[4] = { 0x37, 0x80, 0x40, 0x12 };
static const uint8_t N64_SIGNATURE[4] = { 0x40, 0x12, 0x37, 0x80 };

/* Tests if a file is a valid N64 rom by checking the first 4 bytes. */
static int is_valid_rom(const unsigned char *buffer)
{
    if (memcmp(buffer, Z64_SIGNATURE, sizeof(Z64_SIGNATURE)) == 0
     || memcmp(buffer, V64_SIGNATURE, sizeof(V64_SIGNATURE)) == 0
     || memcmp(buffer, N64_SIGNATURE, sizeof(N64_SIGNATURE)) == 0)
        return 1;
    else
        return 0;
}

/* Copies the source block of memory to the destination block of memory while
 * switching the endianness of .v64 and .n64 images to the .z64 format, which
 * is native to the Nintendo 64. The data extraction routines and MD5 hashing
 * function may only act on the .z64 big-endian format.
 *
 * IN: src: The source block of memory. This must be a valid Nintendo 64 ROM
 *          image of 'len' bytes.
 *     len: The length of the source and destination, in bytes.
 * OUT: dst: The destination block of memory. This must be a valid buffer for
 *           at least 'len' bytes.
 *      imagetype: A pointer to a byte that gets updated with the value of
 *                 V64IMAGE, N64IMAGE or Z64IMAGE according to the format of
 *                 the source block. The value is undefined if 'src' does not
 *                 represent a valid Nintendo 64 ROM image.
 */
static void swap_copy_rom(void* dst, const void* src, size_t len, unsigned char* imagetype)
{
    if (memcmp(src, V64_SIGNATURE, sizeof(V64_SIGNATURE)) == 0)
    {
        size_t i;
        const uint16_t* src16 = (const uint16_t*) src;
        uint16_t* dst16 = (uint16_t*) dst;

        *imagetype = V64IMAGE;
        /* .v64 images have byte-swapped half-words (16-bit). */
        for (i = 0; i < len; i += 2)
        {
            *dst16++ = m64p_swap16(*src16++);
        }
    }
    else if (memcmp(src, N64_SIGNATURE, sizeof(N64_SIGNATURE)) == 0)
    {
        size_t i;
        const uint32_t* src32 = (const uint32_t*) src;
        uint32_t* dst32 = (uint32_t*) dst;

        *imagetype = N64IMAGE;
        /* .n64 images have byte-swapped words (32-bit). */
        for (i = 0; i < len; i += 4)
        {
            *dst32++ = m64p_swap32(*src32++);
        }
    }
    else {
        *imagetype = Z64IMAGE;
        memcpy(dst, src, len);
    }
}

m64p_error open_rom(const unsigned char* romimage, unsigned int size)
{
    romdatabase_entry* entry;
    char buffer[256];
    unsigned char imagetype;
    int i;

    /* check input requirements */
    if (romimage == NULL || !is_valid_rom(romimage))
    {
        DebugMessage(M64MSG_ERROR, "open_rom(): not a valid ROM image");
        return M64ERR_INPUT_INVALID;
    }

    /* Clear Byte-swapped flag, since ROM is now deleted. */
    g_RomWordsLittleEndian = 0;
    /* allocate new buffer for ROM and copy into this buffer */
    g_rom_size = size;
    swap_copy_rom((uint8_t*)mem_base_u32(g_mem_base, MM_CART_ROM), romimage, size, &imagetype);
    /* ROM is now in N64 native (big endian) byte order */

    memcpy(&ROM_HEADER, (uint8_t*)mem_base_u32(g_mem_base, MM_CART_ROM), sizeof(m64p_rom_header));

    /* add some useful properties to ROM_PARAMS */
    ROM_PARAMS.systemtype = rom_country_code_to_system_type(ROM_HEADER.Country_code);
    ROM_PARAMS.cheats = NULL;

    memcpy(ROM_PARAMS.headername, ROM_HEADER.Name, 20);
    ROM_PARAMS.headername[20] = '\0';
    trim(ROM_PARAMS.headername); /* Remove trailing whitespace from ROM name. */

    // Defaults since we removed md5 and rom DB scanning code.
    strcpy(ROM_SETTINGS.goodname, ROM_PARAMS.headername);
    strcat(ROM_SETTINGS.goodname, " (unknown rom)");
    /* There's no way to guess the save type, but 4K EEPROM is better than nothing */
    ROM_SETTINGS.savetype = SAVETYPE_EEPROM_4K;
    ROM_SETTINGS.status = 0;
    ROM_SETTINGS.players = 4;
    ROM_SETTINGS.rumble = 1;
    ROM_SETTINGS.transferpak = 0;
    ROM_SETTINGS.mempak = 1;
    ROM_SETTINGS.biopak = 0;
    ROM_SETTINGS.countperop = DEFAULT_COUNT_PER_OP;
    ROM_SETTINGS.disableextramem = DEFAULT_DISABLE_EXTRA_MEM;
    ROM_SETTINGS.sidmaduration = DEFAULT_SI_DMA_DURATION;
    ROM_PARAMS.cheats = NULL;

    /* print out a bunch of info about the ROM */
    DebugMessage(M64MSG_INFO, "Goodname: %s", ROM_SETTINGS.goodname);
    DebugMessage(M64MSG_INFO, "Name: %s", ROM_HEADER.Name);
    imagestring(imagetype, buffer);
    DebugMessage(M64MSG_INFO, "MD5: %s", ROM_SETTINGS.MD5);
    DebugMessage(M64MSG_INFO, "CRC: %08" PRIX32 " %08" PRIX32, tohl(ROM_HEADER.CRC1), tohl(ROM_HEADER.CRC2));
    DebugMessage(M64MSG_INFO, "Imagetype: %s", buffer);
    DebugMessage(M64MSG_INFO, "Rom size: %d bytes (or %d Mb or %d Megabits)", g_rom_size, g_rom_size/1024/1024, g_rom_size/1024/1024*8);
    DebugMessage(M64MSG_VERBOSE, "ClockRate = %" PRIX32, tohl(ROM_HEADER.ClockRate));
    DebugMessage(M64MSG_INFO, "Version: %" PRIX32, tohl(ROM_HEADER.Release));
    if(tohl(ROM_HEADER.Manufacturer_ID) == 'N')
        DebugMessage(M64MSG_INFO, "Manufacturer: Nintendo");
    else
        DebugMessage(M64MSG_INFO, "Manufacturer: %" PRIX32, tohl(ROM_HEADER.Manufacturer_ID));
    DebugMessage(M64MSG_VERBOSE, "Cartridge_ID: %" PRIX16, ROM_HEADER.Cartridge_ID);
    countrycodestring(ROM_HEADER.Country_code, buffer);
    DebugMessage(M64MSG_INFO, "Country: %s", buffer);
    DebugMessage(M64MSG_VERBOSE, "PC = %" PRIX32, tohl(ROM_HEADER.PC));
    DebugMessage(M64MSG_VERBOSE, "Save type: %d", ROM_SETTINGS.savetype);

    return M64ERR_SUCCESS;
}

m64p_error close_rom(void)
{
    /* Clear Byte-swapped flag, since ROM is now deleted. */
    g_RomWordsLittleEndian = 0;
    DebugMessage(M64MSG_STATUS, "Rom closed.");

    return M64ERR_SUCCESS;
}

/********************************************************************************************/
/* ROM utility functions */

// Get the system type associated to a ROM country code.
static m64p_system_type rom_country_code_to_system_type(uint16_t country_code)
{
    switch (country_code & UINT16_C(0xFF))
    {
        // PAL codes
        case 0x44:
        case 0x46:
        case 0x49:
        case 0x50:
        case 0x53:
        case 0x55:
        case 0x58:
        case 0x59:
            return SYSTEM_PAL;

        // NTSC codes
        case 0x37:
        case 0x41:
        case 0x45:
        case 0x4a:
        default: // Fallback for unknown codes
            return SYSTEM_NTSC;
    }
}
