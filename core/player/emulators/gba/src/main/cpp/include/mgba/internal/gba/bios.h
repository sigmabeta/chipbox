/* Copyright (c) 2013-2015 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#ifndef GBA_BIOS_H
#define GBA_BIOS_H

#include <mgba-util/common.h>

CXX_GUARD_START

#include <mgba/core/log.h>

        mLOG_DECLARE_CATEGORY(GBA_BIOS);

enum GBASwi {
    GBA_SWI_SOFT_RESET = 0x00,
    GBA_SWI_REGISTER_RAM_RESET = 0x01,
    GBA_SWI_HALT = 0x02,
    GBA_SWI_STOP = 0x03,
    GBA_SWI_INTR_WAIT = 0x04,
    GBA_SWI_VBLANK_INTR_WAIT = 0x05,
    GBA_SWI_DIV = 0x06,
    GBA_SWI_DIV_ARM = 0x07,
    GBA_SWI_SQRT = 0x08,
    GBA_SWI_ARCTAN = 0x09,
    GBA_SWI_ARCTAN2 = 0x0A,
    GBA_SWI_CPU_SET = 0x0B,
    GBA_SWI_CPU_FAST_SET = 0x0C,
    GBA_SWI_GET_BIOS_CHECKSUM = 0x0D,
    GBA_SWI_BG_AFFINE_SET = 0x0E,
    GBA_SWI_OBJ_AFFINE_SET = 0x0F,
    GBA_SWI_BIT_UNPACK = 0x10,
    GBA_SWI_LZ77_UNCOMP_WRAM = 0x11,
    GBA_SWI_LZ77_UNCOMP_VRAM = 0x12,
    GBA_SWI_HUFFMAN_UNCOMP = 0x13,
    GBA_SWI_RL_UNCOMP_WRAM = 0x14,
    GBA_SWI_RL_UNCOMP_VRAM = 0x15,
    GBA_SWI_DIFF_8BIT_UNFILTER_WRAM = 0x16,
    GBA_SWI_DIFF_8BIT_UNFILTER_VRAM = 0x17,
    GBA_SWI_DIFF_16BIT_UNFILTER = 0x18,
    GBA_SWI_SOUND_BIAS = 0x19,
    GBA_SWI_SOUND_DRIVER_INIT = 0x1A,
    GBA_SWI_SOUND_DRIVER_MODE = 0x1B,
    GBA_SWI_SOUND_DRIVER_MAIN = 0x1C,
    GBA_SWI_SOUND_DRIVER_VSYNC = 0x1D,
    GBA_SWI_SOUND_CHANNEL_CLEAR = 0x1E,
    GBA_SWI_MIDI_KEY_2_FREQ = 0x1F,
    GBA_SWI_MUSIC_PLAYER_OPEN = 0x20,
    GBA_SWI_MUSIC_PLAYER_START = 0x21,
    GBA_SWI_MUSIC_PLAYER_STOP = 0x22,
    GBA_SWI_MUSIC_PLAYER_CONTINUE = 0x23,
    GBA_SWI_MUSIC_PLAYER_FADE_OUT = 0x24,
    GBA_SWI_MULTI_BOOT = 0x25,
    GBA_SWI_HARD_RESET = 0x26,
    GBA_SWI_CUSTOM_HALT = 0x27,
    GBA_SWI_SOUND_DRIVER_VSYNC_OFF = 0x28,
    GBA_SWI_SOUND_DRIVER_VSYNC_ON = 0x29,
    GBA_SWI_SOUND_DRIVER_GET_JUMP_LIST = 0x2A,
};

struct ARMCore;

void GBASwi16(struct ARMCore *cpu, int immediate);

void GBASwi32(struct ARMCore *cpu, int immediate);

uint32_t GBAChecksum(uint32_t *memory, size_t size);

extern const uint32_t GBA_BIOS_CHECKSUM;
extern const uint32_t GBA_DS_BIOS_CHECKSUM;

CXX_GUARD_END

#endif