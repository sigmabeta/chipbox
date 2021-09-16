/* Copyright (c) 2013-2016 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <mgba/internal/gb/mbc.h>

#include <mgba/core/interface.h>
#include <mgba/internal/sm83/sm83.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/memory.h>
#include <mgba-util/crc32.h>
#include <mgba-util/vfs.h>

const uint32_t GB_LOGO_HASH = 0x46195417;

mLOG_DEFINE_CATEGORY(GB_MBC, "GB MBC", "gb.mbc");

static void _GBMBCNone(struct GB* gb, uint16_t address, uint8_t value) {
	UNUSED(gb);
	UNUSED(address);
	UNUSED(value);

	mLOG(GB_MBC, GAME_ERROR, "Wrote to invalid MBC");
}

static void _GBMBC1(struct GB*, uint16_t address, uint8_t value);
static void _GBMBC2(struct GB*, uint16_t address, uint8_t value);
static void _GBMBC3(struct GB*, uint16_t address, uint8_t value);
static void _GBMBC5(struct GB*, uint16_t address, uint8_t value);
static void _GBMBC6(struct GB*, uint16_t address, uint8_t value);
static void _GBMBC7(struct GB*, uint16_t address, uint8_t value);
static void _GBMMM01(struct GB*, uint16_t address, uint8_t value);
static void _GBHuC1(struct GB*, uint16_t address, uint8_t value);
static void _GBHuC3(struct GB*, uint16_t address, uint8_t value);
static void _GBPocketCam(struct GB* gb, uint16_t address, uint8_t value);
static void _GBTAMA5(struct GB* gb, uint16_t address, uint8_t value);
static void _GBWisdomTree(struct GB* gb, uint16_t address, uint8_t value);
static void _GBPKJD(struct GB* gb, uint16_t address, uint8_t value);
static void _GBBBD(struct GB* gb, uint16_t address, uint8_t value);
static void _GBHitek(struct GB* gb, uint16_t address, uint8_t value);

static uint8_t _GBMBC2Read(struct GBMemory*, uint16_t address);
static uint8_t _GBMBC6Read(struct GBMemory*, uint16_t address);
static uint8_t _GBMBC7Read(struct GBMemory*, uint16_t address);
static void _GBMBC7Write(struct GBMemory* memory, uint16_t address, uint8_t value);

static uint8_t _GBTAMA5Read(struct GBMemory*, uint16_t address);
static uint8_t _GBPKJDRead(struct GBMemory*, uint16_t address);
static uint8_t _GBBBDRead(struct GBMemory*, uint16_t address);
static uint8_t _GBHitekRead(struct GBMemory*, uint16_t address);

static uint8_t _GBPocketCamRead(struct GBMemory*, uint16_t address);
static void _GBPocketCamCapture(struct GBMemory*);

static void _GBMBC6MapChip(struct GB*, int half, uint8_t value);

void GBMBCSwitchBank(struct GB* gb, int bank) {
	size_t bankStart = bank * GB_SIZE_CART_BANK0;
	if (bankStart + GB_SIZE_CART_BANK0 > gb->memory.romSize) {
		mLOG(GB_MBC, GAME_ERROR, "Attempting to switch to an invalid ROM bank: %0X", bank);
		bankStart &= (gb->memory.romSize - 1);
		bank = bankStart / GB_SIZE_CART_BANK0;
	}
	gb->memory.romBank = &gb->memory.rom[bankStart];
	gb->memory.currentBank = bank;
	if (gb->cpu->pc < GB_BASE_VRAM) {
		gb->cpu->memory.setActiveRegion(gb->cpu, gb->cpu->pc);
	}
}

void GBMBCSwitchBank0(struct GB* gb, int bank) {
	size_t bankStart = bank * GB_SIZE_CART_BANK0;
	if (bankStart + GB_SIZE_CART_BANK0 > gb->memory.romSize) {
		mLOG(GB_MBC, GAME_ERROR, "Attempting to switch to an invalid ROM bank: %0X", bank);
		bankStart &= (gb->memory.romSize - 1);
	}
	gb->memory.romBase = &gb->memory.rom[bankStart];
	gb->memory.currentBank0 = bank;
	if (gb->cpu->pc < GB_SIZE_CART_BANK0) {
		gb->cpu->memory.setActiveRegion(gb->cpu, gb->cpu->pc);
	}
}

void GBMBCSwitchHalfBank(struct GB* gb, int half, int bank) {
	size_t bankStart = bank * GB_SIZE_CART_HALFBANK;
	bool isFlash = half ? gb->memory.mbcState.mbc6.flashBank1 : gb->memory.mbcState.mbc6.flashBank0;
	if (isFlash) {
		if (bankStart + GB_SIZE_CART_HALFBANK > GB_SIZE_MBC6_FLASH) {
			mLOG(GB_MBC, GAME_ERROR, "Attempting to switch to an invalid Flash bank: %0X", bank);
			bankStart &= GB_SIZE_MBC6_FLASH - 1;
			bank = bankStart / GB_SIZE_CART_HALFBANK;
		}
		bankStart += gb->sramSize - GB_SIZE_MBC6_FLASH;
	} else {
		if (bankStart + GB_SIZE_CART_HALFBANK > gb->memory.romSize) {
			mLOG(GB_MBC, GAME_ERROR, "Attempting to switch to an invalid ROM bank: %0X", bank);
			bankStart &= gb->memory.romSize - 1;
			bank = bankStart / GB_SIZE_CART_HALFBANK;
			if (!bank) {
				++bank;
			}
		}
	}
	if (!half) {
		if (isFlash) {
			gb->memory.romBank = &gb->memory.sram[bankStart];
		} else {
			gb->memory.romBank = &gb->memory.rom[bankStart];
		}
		gb->memory.currentBank = bank;
	} else {
		if (isFlash) {
			gb->memory.mbcState.mbc6.romBank1 = &gb->memory.sram[bankStart];
		} else {
			gb->memory.mbcState.mbc6.romBank1 = &gb->memory.rom[bankStart];
		}
		gb->memory.mbcState.mbc6.currentBank1 = bank;
	}
	if (gb->cpu->pc < GB_BASE_VRAM) {
		gb->cpu->memory.setActiveRegion(gb->cpu, gb->cpu->pc);
	}
}

static bool _isMulticart(const uint8_t* mem) {
	bool success;
	struct VFile* vf;

	vf = VFileFromConstMemory(&mem[GB_SIZE_CART_BANK0 * 0x10], 1024);
	success = GBIsROM(vf);
	vf->close(vf);

	if (!success) {
		return false;
	}

	vf = VFileFromConstMemory(&mem[GB_SIZE_CART_BANK0 * 0x20], 1024);
	success = GBIsROM(vf);
	vf->close(vf);

	if (!success) {
		vf = VFileFromConstMemory(&mem[GB_SIZE_CART_BANK0 * 0x30], 1024);
		success = GBIsROM(vf);
		vf->close(vf);
	}
	
	return success;
}

static bool _isWisdomTree(const uint8_t* mem, size_t size) {
	size_t i;
	for (i = 0x134; i < 0x14C; i += 4) {
		if (*(uint32_t*) &mem[i] != 0) {
			return false;
		}
	}
	for (i = 0xF0; i < 0x100; i += 4) {
		if (*(uint32_t*) &mem[i] != 0) {
			return false;
		}
	}
	if (mem[0x14D] != 0xE7) {
		return false;
	}
	for (i = 0x300; i < size - 11; ++i) {
		if (memcmp(&mem[i], "WISDOM", 6) == 0 && memcmp(&mem[i + 7], "TREE", 4) == 0) {
			return true;
		}
	}
	return false;
}

static enum GBMemoryBankControllerType _detectUnlMBC(const uint8_t* mem, size_t size) {
	const struct GBCartridge* cart = (const struct GBCartridge*) &mem[0x100];

	switch (cart->type) {
		case 0:
		if (_isWisdomTree(mem, size)) {
			return GB_UNL_WISDOM_TREE;
		}
		break;
	}

	uint32_t secondaryLogo = doCrc32(&mem[0x184], 0x30);
	switch (secondaryLogo) {
	case 0x4fdab691:
		return GB_UNL_HITEK;
	case 0xc7d8c1df:
	case 0x6d1ea662: // Garou
		if (mem[0x7FFF] != 0x01) { // Make sure we're not using a "fixed" version
			return GB_UNL_BBD;
		}
	}

	return GB_MBC_AUTODETECT;
}

void GBMBCSwitchSramBank(struct GB* gb, int bank) {
	size_t bankStart = bank * GB_SIZE_EXTERNAL_RAM;
	if (bankStart + GB_SIZE_EXTERNAL_RAM > gb->sramSize) {
		mLOG(GB_MBC, GAME_ERROR, "Attempting to switch to an invalid RAM bank: %0X", bank);
		bankStart &= (gb->sramSize - 1);
		bank = bankStart / GB_SIZE_EXTERNAL_RAM;
	}
	gb->memory.sramBank = &gb->memory.sram[bankStart];
	gb->memory.sramCurrentBank = bank;
}

void GBMBCSwitchSramHalfBank(struct GB* gb, int half, int bank) {
	size_t bankStart = bank * GB_SIZE_EXTERNAL_RAM_HALFBANK;
	size_t sramSize = gb->sramSize - GB_SIZE_MBC6_FLASH;
	if (bankStart + GB_SIZE_EXTERNAL_RAM_HALFBANK > sramSize) {
		mLOG(GB_MBC, GAME_ERROR, "Attempting to switch to an invalid RAM bank: %0X", bank);
		bankStart &= (sramSize - 1);
		bank = bankStart / GB_SIZE_EXTERNAL_RAM_HALFBANK;
	}
	if (!half) {
		gb->memory.sramBank = &gb->memory.sram[bankStart];
		gb->memory.sramCurrentBank = bank;
	} else {
		gb->memory.mbcState.mbc6.sramBank1 = &gb->memory.sram[bankStart];
		gb->memory.mbcState.mbc6.currentSramBank1 = bank;
	}
}

void GBMBCInit(struct GB* gb) {
	const struct GBCartridge* cart = (const struct GBCartridge*) &gb->memory.rom[0x100];
	if (gb->memory.rom && gb->memory.romSize) {
		if (gb->memory.romSize >= 0x8000) {
			const struct GBCartridge* cartFooter = (const struct GBCartridge*) &gb->memory.rom[gb->memory.romSize - 0x7F00];
			if (doCrc32(cartFooter->logo, sizeof(cartFooter->logo)) == GB_LOGO_HASH && cartFooter->type >= 0x0B && cartFooter->type <= 0x0D) {
				cart = cartFooter;
			}
		}
		switch (cart->ramSize) {
		case 0:
			gb->sramSize = 0;
			break;
		default:
		case 2:
			gb->sramSize = 0x2000;
			break;
		case 3:
			gb->sramSize = 0x8000;
			break;
		case 4:
			gb->sramSize = 0x20000;
			break;
		case 5:
			gb->sramSize = 0x10000;
			break;
		}
		if (gb->memory.mbcType == GB_MBC_AUTODETECT) {
			gb->memory.mbcType = _detectUnlMBC(gb->memory.rom, gb->memory.romSize);
		}

		if (gb->memory.mbcType == GB_MBC_AUTODETECT) {
			switch (cart->type) {
			case 0:
			case 8:
			case 9:
				gb->memory.mbcType = GB_MBC_NONE;
				break;
			case 1:
			case 2:
			case 3:
				gb->memory.mbcType = GB_MBC1;
				break;
			case 5:
			case 6:
				gb->memory.mbcType = GB_MBC2;
				break;
			case 0x0B:
			case 0x0C:
			case 0x0D:
				gb->memory.mbcType = GB_MMM01;
				break;
			case 0x0F:
			case 0x10:
				gb->memory.mbcType = GB_MBC3_RTC;
				break;
			case 0x11:
			case 0x12:
			case 0x13:
				gb->memory.mbcType = GB_MBC3;
				break;
			default:
				mLOG(GB_MBC, WARN, "Unknown MBC type: %02X", cart->type);
				// Fall through
			case 0x19:
			case 0x1A:
			case 0x1B:
				gb->memory.mbcType = GB_MBC5;
				break;
			case 0x1C:
			case 0x1D:
			case 0x1E:
				gb->memory.mbcType = GB_MBC5_RUMBLE;
				break;
			case 0x20:
				gb->memory.mbcType = GB_MBC6;
				break;
			case 0x22:
				gb->memory.mbcType = GB_MBC7;
				break;
			case 0xFC:
				gb->memory.mbcType = GB_POCKETCAM;
				break;
			case 0xFD:
				gb->memory.mbcType = GB_TAMA5;
				break;
			case 0xFE:
				gb->memory.mbcType = GB_HuC3;
				break;
			case 0xFF:
				gb->memory.mbcType = GB_HuC1;
				break;
			}
		}
	} else {
		gb->memory.mbcType = GB_MBC_NONE;
	}
	gb->memory.mbcRead = NULL;
	gb->memory.directSramAccess = true;
	gb->memory.cartBusDecay = 4;
	switch (gb->memory.mbcType) {
	case GB_MBC_NONE:
		gb->memory.mbcWrite = _GBMBCNone;
		break;
	case GB_MBC1:
		gb->memory.mbcWrite = _GBMBC1;
		if (gb->memory.romSize >= GB_SIZE_CART_BANK0 * 0x31 && _isMulticart(gb->memory.rom)) {
			gb->memory.mbcState.mbc1.multicartStride = 4;
		} else {
			gb->memory.mbcState.mbc1.multicartStride = 5;
		}
		break;
	case GB_MBC2:
		gb->memory.mbcWrite = _GBMBC2;
		gb->memory.mbcRead = _GBMBC2Read;
		gb->memory.directSramAccess = false;
		gb->sramSize = 0x100;
		break;
	case GB_MBC3:
		gb->memory.mbcWrite = _GBMBC3;
		break;
	default:
		mLOG(GB_MBC, WARN, "Unknown MBC type: %02X", cart->type);
		// Fall through
	case GB_MBC5:
		gb->memory.mbcWrite = _GBMBC5;
		break;
	case GB_MBC6:
		gb->memory.mbcWrite = _GBMBC6;
		gb->memory.mbcRead = _GBMBC6Read;
		gb->memory.directSramAccess = false;
		if (!gb->sramSize) {
			gb->sramSize = GB_SIZE_EXTERNAL_RAM; // Force minimum size for convenience
		}
		gb->sramSize += GB_SIZE_MBC6_FLASH; // Flash is concatenated at the end
		break;
	case GB_MBC7:
		gb->memory.mbcWrite = _GBMBC7;
		gb->memory.mbcRead = _GBMBC7Read;
		gb->sramSize = 0x100;
		break;
	case GB_MMM01:
		gb->memory.mbcWrite = _GBMMM01;
		break;
	case GB_HuC1:
		gb->memory.mbcWrite = _GBHuC1;
		break;
	case GB_HuC3:
		gb->memory.mbcWrite = _GBHuC3;
		break;
	case GB_TAMA5:
		mLOG(GB_MBC, WARN, "unimplemented MBC: TAMA5");
		memset(gb->memory.rtcRegs, 0, sizeof(gb->memory.rtcRegs));
		gb->memory.mbcWrite = _GBTAMA5;
		gb->memory.mbcRead = _GBTAMA5Read;
		gb->sramSize = 0x20;
		break;
	case GB_MBC3_RTC:
		memset(gb->memory.rtcRegs, 0, sizeof(gb->memory.rtcRegs));
		gb->memory.mbcWrite = _GBMBC3;
		break;
	case GB_MBC5_RUMBLE:
		gb->memory.mbcWrite = _GBMBC5;
		break;
	case GB_POCKETCAM:
		gb->memory.mbcWrite = _GBPocketCam;
		gb->memory.mbcRead = _GBPocketCamRead;
		if (!gb->sramSize) {
			gb->sramSize = GB_SIZE_EXTERNAL_RAM; // Force minimum size for convenience
		}
		if (gb->memory.cam && gb->memory.cam->startRequestImage) {
			gb->memory.cam->startRequestImage(gb->memory.cam, GBCAM_WIDTH, GBCAM_HEIGHT, mCOLOR_ANY);
		}
		break;
	case GB_UNL_WISDOM_TREE:
		gb->memory.mbcWrite = _GBWisdomTree;
		break;
	case GB_UNL_BBD:
		gb->memory.mbcWrite = _GBBBD;
		gb->memory.mbcRead = _GBBBDRead;
		break;
	case GB_UNL_HITEK:
		gb->memory.mbcWrite = _GBHitek;
		gb->memory.mbcRead = _GBHitekRead;
		gb->memory.mbcState.bbd.dataSwapMode = 7;
		gb->memory.mbcState.bbd.bankSwapMode = 7;
		break;
	case GB_UNL_PKJD:
		gb->memory.mbcWrite = _GBPKJD;
		gb->memory.mbcRead = _GBPKJDRead;
		break;
	}

	gb->memory.currentBank = 1;
	gb->memory.sramCurrentBank = 0;
	gb->memory.sramAccess = false;
	gb->memory.rtcAccess = false;
	gb->memory.activeRtcReg = 0;
	gb->memory.rtcLatched = false;
	gb->memory.rtcLastLatch = 0;
	if (gb->memory.rtc) {
		if (gb->memory.rtc->sample) {
			gb->memory.rtc->sample(gb->memory.rtc);
		}
		gb->memory.rtcLastLatch = gb->memory.rtc->unixTime(gb->memory.rtc);
	} else {
		gb->memory.rtcLastLatch = time(0);
	}
	memset(&gb->memory.rtcRegs, 0, sizeof(gb->memory.rtcRegs));

	GBResizeSram(gb, gb->sramSize);

	if (gb->memory.mbcType == GB_MBC3_RTC) {
		GBMBCRTCRead(gb);
	}
}

void GBMBCReset(struct GB* gb) {
	gb->memory.currentBank0 = 0;
	gb->memory.romBank = &gb->memory.rom[GB_SIZE_CART_BANK0];
	gb->memory.cartBus = 0xFF;
	gb->memory.cartBusPc = 0;
	gb->memory.cartBusDecay = 1;

	memset(&gb->memory.mbcState, 0, sizeof(gb->memory.mbcState));
	GBMBCInit(gb);
	switch (gb->memory.mbcType) {
	case GB_MBC1:
		gb->memory.mbcState.mbc1.mode = 0;
		gb->memory.mbcState.mbc1.bankLo = 1;
		break;
	case GB_MBC6:
		GBMBCSwitchHalfBank(gb, 0, 2);
		GBMBCSwitchHalfBank(gb, 1, 3);
		gb->memory.mbcState.mbc6.sramAccess = false;
		GBMBCSwitchSramHalfBank(gb, 0, 0);
		GBMBCSwitchSramHalfBank(gb, 0, 1);
		break;
	case GB_MMM01:
		GBMBCSwitchBank0(gb, gb->memory.romSize / GB_SIZE_CART_BANK0 - 2);
		GBMBCSwitchBank(gb, gb->memory.romSize / GB_SIZE_CART_BANK0 - 1);
		break;
	default:
		break;
	}
	gb->memory.sramBank = gb->memory.sram;
}

static void _latchRtc(struct mRTCSource* rtc, uint8_t* rtcRegs, time_t* rtcLastLatch) {
	time_t t;
	if (rtc) {
		if (rtc->sample) {
			rtc->sample(rtc);
		}
		t = rtc->unixTime(rtc);
	} else {
		t = time(0);
	}
	time_t currentLatch = t;
	t -= *rtcLastLatch;
	*rtcLastLatch = currentLatch;

	int64_t diff;
	diff = rtcRegs[0] + t % 60;
	if (diff < 0) {
		diff += 60;
		t -= 60;
	}
	rtcRegs[0] = diff % 60;
	t /= 60;
	t += diff / 60;

	diff = rtcRegs[1] + t % 60;
	if (diff < 0) {
		diff += 60;
		t -= 60;
	}
	rtcRegs[1] = diff % 60;
	t /= 60;
	t += diff / 60;

	diff = rtcRegs[2] + t % 24;
	if (diff < 0) {
		diff += 24;
		t -= 24;
	}
	rtcRegs[2] = diff % 24;
	t /= 24;
	t += diff / 24;

	diff = rtcRegs[3] + ((rtcRegs[4] & 1) << 8) + (t & 0x1FF);
	rtcRegs[3] = diff;
	rtcRegs[4] &= 0xFE;
	rtcRegs[4] |= (diff >> 8) & 1;
	if (diff & 0x200) {
		rtcRegs[4] |= 0x80;
	}
}

static void _GBMBC1Update(struct GB* gb) {
	struct GBMBC1State* state = &gb->memory.mbcState.mbc1;
	int bank = state->bankLo;
	bank &= (1 << state->multicartStride) - 1;
	bank |= state->bankHi << state->multicartStride;
	if (state->mode) {
		GBMBCSwitchBank0(gb, state->bankHi << state->multicartStride);
		GBMBCSwitchSramBank(gb, state->bankHi & 3);
	} else {
		GBMBCSwitchBank0(gb, 0);
		GBMBCSwitchSramBank(gb, 0);
	}
	if (!(state->bankLo & 0x1F)) {
		++state->bankLo;
		++bank;
	}
	GBMBCSwitchBank(gb, bank);
}

void _GBMBC1(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank = value & 0x1F;
	switch (address >> 13) {
	case 0x0:
		switch (value & 0xF) {
		case 0:
			memory->sramAccess = false;
			break;
		case 0xA:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "MBC1 unknown value %02X", value);
			break;
		}
		break;
	case 0x1:
		memory->mbcState.mbc1.bankLo = bank;
		_GBMBC1Update(gb);
		break;
	case 0x2:
		bank &= 3;
		memory->mbcState.mbc1.bankHi = bank;
		_GBMBC1Update(gb);
		break;
	case 0x3:
		memory->mbcState.mbc1.mode = value & 1;
		_GBMBC1Update(gb);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "MBC1 unknown address: %04X:%02X", address, value);
		break;
	}
}

void _GBMBC2(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int shift = (address & 1) * 4;
	int bank = value & 0xF;
	switch ((address & 0xC100) >> 8) {
	case 0x0:
		switch (value & 0x0F) {
		case 0:
			memory->sramAccess = false;
			break;
		case 0xA:
			memory->sramAccess = true;
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "MBC2 unknown value %02X", value);
			break;
		}
		break;
	case 0x1:
		if (!bank) {
			++bank;
		}
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x80:
	case 0x81:
	case 0x82:
	case 0x83:
		if (!memory->sramAccess) {
			return;
		}
		address &= 0x1FF;
		memory->sramBank[(address >> 1)] &= 0xF0 >> shift;
		memory->sramBank[(address >> 1)] |= (value & 0xF) << shift;
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "MBC2 unknown address: %04X:%02X", address, value);
		break;
	}
}

static uint8_t _GBMBC2Read(struct GBMemory* memory, uint16_t address) {
	if (!memory->sramAccess) {
		return 0xFF;
	}
	address &= 0x1FF;
	int shift = (address & 1) * 4;
	return (memory->sramBank[(address >> 1)] >> shift) | 0xF0;
}

void _GBMBC3(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank = value;
	switch (address >> 13) {
	case 0x0:
		switch (value & 0xF) {
		case 0:
			memory->sramAccess = false;
			break;
		case 0xA:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "MBC3 unknown value %02X", value);
			break;
		}
		break;
	case 0x1:
		if (gb->memory.romSize < GB_SIZE_CART_BANK0 * 0x80) {
			bank &= 0x7F;
		}
		if (!bank) {
			++bank;
		}
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x2:
		bank &= 0xF;
		if (bank < 8) {
			GBMBCSwitchSramBank(gb, value);
			memory->rtcAccess = false;
		} else if (bank <= 0xC) {
			memory->activeRtcReg = bank - 8;
			memory->rtcAccess = true;
		}
		break;
	case 0x3:
		if (memory->rtcLatched && value == 0) {
			memory->rtcLatched = false;
		} else if (!memory->rtcLatched && value == 1) {
			_latchRtc(gb->memory.rtc, gb->memory.rtcRegs, &gb->memory.rtcLastLatch);
			memory->rtcLatched = true;
		}
		break;
	}
}

void _GBMBC5(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank;
	switch (address >> 12) {
	case 0x0:
	case 0x1:
		switch (value) {
		case 0:
			memory->sramAccess = false;
			break;
		case 0xA:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "MBC5 unknown value %02X", value);
			break;
		}
		break;
	case 0x2:
		bank = (memory->currentBank & 0x100) | value;
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x3:
		bank = (memory->currentBank & 0xFF) | ((value & 1) << 8);
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x4:
	case 0x5:
		if (memory->mbcType == GB_MBC5_RUMBLE && memory->rumble) {
			memory->rumble->setRumble(memory->rumble, (value >> 3) & 1);
			value &= ~8;
		}
		GBMBCSwitchSramBank(gb, value & 0xF);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "MBC5 unknown address: %04X:%02X", address, value);
		break;
	}
}

void _GBMBC6(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank = value;
	switch (address >> 10) {
	case 0:
		switch (value) {
		case 0:
			memory->sramAccess = false;
			break;
		case 0xA:
			memory->sramAccess = true;
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "MBC6 unknown value %02X", value);
			break;
		}
		break;
	case 0x1:
		GBMBCSwitchSramHalfBank(gb, 0, bank);
		break;
	case 0x2:
		GBMBCSwitchSramHalfBank(gb, 1, bank);
		break;
	case 0x3:
		mLOG(GB_MBC, STUB, "MBC6 unimplemented flash OE write: %04X:%02X", address, value);
		break;
	case 0x4:
		mLOG(GB_MBC, STUB, "MBC6 unimplemented flash WE write: %04X:%02X", address, value);
		break;
	case 0x8:
	case 0x9:
		GBMBCSwitchHalfBank(gb, 0, bank);
		break;
	case 0xA:
	case 0xB:
		_GBMBC6MapChip(gb, 0, value);
		break;
	case 0xC:
	case 0xD:
		GBMBCSwitchHalfBank(gb, 1, bank);
		break;
	case 0xE:
	case 0xF:
		_GBMBC6MapChip(gb, 1, value);
		break;
	case 0x28:
	case 0x29:
	case 0x2A:
	case 0x2B:
		if (memory->sramAccess) {
			memory->sramBank[address & (GB_SIZE_EXTERNAL_RAM_HALFBANK - 1)] = value;
		}
		break;
	case 0x2C:
	case 0x2D:
	case 0x2E:
	case 0x2F:
		if (memory->sramAccess) {
			memory->mbcState.mbc6.sramBank1[address & (GB_SIZE_EXTERNAL_RAM_HALFBANK - 1)] = value;
		}
		break;
	default:
		mLOG(GB_MBC, STUB, "MBC6 unknown address: %04X:%02X", address, value);
		break;
	}
}

uint8_t _GBMBC6Read(struct GBMemory* memory, uint16_t address) {
	if (!memory->sramAccess) {
		return 0xFF;
	}
	switch (address >> 12) {
	case 0xA:
		return memory->sramBank[address & (GB_SIZE_EXTERNAL_RAM_HALFBANK - 1)];
	case 0xB:
		return memory->mbcState.mbc6.sramBank1[address & (GB_SIZE_EXTERNAL_RAM_HALFBANK - 1)];
	}
	return 0xFF;
}

static void _GBMBC6MapChip(struct GB* gb, int half, uint8_t value) {
	if (!half) {
		gb->memory.mbcState.mbc6.flashBank0 = !!(value & 0x08);
		GBMBCSwitchHalfBank(gb, half, gb->memory.currentBank);
	} else {
		gb->memory.mbcState.mbc6.flashBank1 = !!(value & 0x08);
		GBMBCSwitchHalfBank(gb, half, gb->memory.mbcState.mbc6.currentBank1);
	}
}

void _GBMBC7(struct GB* gb, uint16_t address, uint8_t value) {
	int bank = value & 0x7F;
	switch (address >> 13) {
	case 0x0:
		switch (value) {
		default:
		case 0:
			gb->memory.mbcState.mbc7.access = 0;
			break;
		case 0xA:
			gb->memory.mbcState.mbc7.access |= 1;
			break;
		}
		break;
	case 0x1:
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x2:
		if (value == 0x40) {
			gb->memory.mbcState.mbc7.access |= 2;
		} else {
			gb->memory.mbcState.mbc7.access &= ~2;
		}
		break;
	case 0x5:
		_GBMBC7Write(&gb->memory, address, value);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "MBC7 unknown address: %04X:%02X", address, value);
		break;
	}
}

uint8_t _GBMBC7Read(struct GBMemory* memory, uint16_t address) {
	struct GBMBC7State* mbc7 = &memory->mbcState.mbc7;
	if (mbc7->access != 3) {
		return 0xFF;
	}
	switch (address & 0xF0) {
	case 0x20:
		if (memory->rotation && memory->rotation->readTiltX) {
			int32_t x = -memory->rotation->readTiltX(memory->rotation);
			x >>= 21;
			x += 0x81D0;
			return x;
		}
		return 0xFF;
	case 0x30:
		if (memory->rotation && memory->rotation->readTiltX) {
			int32_t x = -memory->rotation->readTiltX(memory->rotation);
			x >>= 21;
			x += 0x81D0;
			return x >> 8;
		}
		return 7;
	case 0x40:
		if (memory->rotation && memory->rotation->readTiltY) {
			int32_t y = -memory->rotation->readTiltY(memory->rotation);
			y >>= 21;
			y += 0x81D0;
			return y;
		}
		return 0xFF;
	case 0x50:
		if (memory->rotation && memory->rotation->readTiltY) {
			int32_t y = -memory->rotation->readTiltY(memory->rotation);
			y >>= 21;
			y += 0x81D0;
			return y >> 8;
		}
		return 7;
	case 0x60:
		return 0;
	case 0x80:
		return mbc7->eeprom;
	default:
		return 0xFF;
	}
}

static void _GBMBC7Write(struct GBMemory* memory, uint16_t address, uint8_t value) {
	struct GBMBC7State* mbc7 = &memory->mbcState.mbc7;
	if (mbc7->access != 3) {
		return;
	}
	switch (address & 0xF0) {
	case 0x00:
		mbc7->latch = (value & 0x55) == 0x55;
		return;
	case 0x10:
		mbc7->latch |= (value & 0xAA);
		if (mbc7->latch == 0xAB && memory->rotation && memory->rotation->sample) {
			memory->rotation->sample(memory->rotation);
		}
		mbc7->latch = 0;
		return;
	default:
		mLOG(GB_MBC, STUB, "MBC7 unknown register: %04X:%02X", address, value);
		return;
	case 0x80:
		break;
	}
	GBMBC7Field old = memory->mbcState.mbc7.eeprom;
	value = GBMBC7FieldFillDO(value); // Hi-Z
	if (!GBMBC7FieldIsCS(old) && GBMBC7FieldIsCS(value)) {
		mbc7->state = GBMBC7_STATE_IDLE;
	}
	if (!GBMBC7FieldIsCLK(old) && GBMBC7FieldIsCLK(value)) {
		if (mbc7->state == GBMBC7_STATE_READ_COMMAND || mbc7->state == GBMBC7_STATE_EEPROM_WRITE || mbc7->state == GBMBC7_STATE_EEPROM_WRAL) {
			mbc7->sr <<= 1;
			mbc7->sr |= GBMBC7FieldGetDI(value);
			++mbc7->srBits;
		}
		switch (mbc7->state) {
		case GBMBC7_STATE_IDLE:
			if (GBMBC7FieldIsDI(value)) {
				mbc7->state = GBMBC7_STATE_READ_COMMAND;
				mbc7->srBits = 0;
				mbc7->sr = 0;
			}
			break;
		case GBMBC7_STATE_READ_COMMAND:
			if (mbc7->srBits == 10) {
				mbc7->state = 0x10 | (mbc7->sr >> 6);
				if (mbc7->state & 0xC) {
					mbc7->state &= ~0x3;
				}
				mbc7->srBits = 0;
				mbc7->address = mbc7->sr & 0x7F;
			}
			break;
		case GBMBC7_STATE_DO:
			value = GBMBC7FieldSetDO(value, mbc7->sr >> 15);
			mbc7->sr <<= 1;
			--mbc7->srBits;
			if (!mbc7->srBits) {
				mbc7->state = GBMBC7_STATE_IDLE;
			}
			break;
		default:
			break;
		}
		switch (mbc7->state) {
		case GBMBC7_STATE_EEPROM_EWEN:
			mbc7->writable = true;
			mbc7->state = GBMBC7_STATE_IDLE;
			break;
		case GBMBC7_STATE_EEPROM_EWDS:
			mbc7->writable = false;
			mbc7->state = GBMBC7_STATE_IDLE;
			break;
		case GBMBC7_STATE_EEPROM_WRITE:
			if (mbc7->srBits == 16) {
				if (mbc7->writable) {
					memory->sram[mbc7->address * 2] = mbc7->sr >> 8;
					memory->sram[mbc7->address * 2 + 1] = mbc7->sr;
				}
				mbc7->state = GBMBC7_STATE_IDLE;
			}
			break;
		case GBMBC7_STATE_EEPROM_ERASE:
			if (mbc7->writable) {
				memory->sram[mbc7->address * 2] = 0xFF;
				memory->sram[mbc7->address * 2 + 1] = 0xFF;
			}
			mbc7->state = GBMBC7_STATE_IDLE;
			break;
		case GBMBC7_STATE_EEPROM_READ:
			mbc7->srBits = 16;
			mbc7->sr = memory->sram[mbc7->address * 2] << 8;
			mbc7->sr |= memory->sram[mbc7->address * 2 + 1];
			mbc7->state = GBMBC7_STATE_DO;
			value = GBMBC7FieldClearDO(value);
			break;
		case GBMBC7_STATE_EEPROM_WRAL:
			if (mbc7->srBits == 16) {
				if (mbc7->writable) {
					int i;
					for (i = 0; i < 128; ++i) {
						memory->sram[i * 2] = mbc7->sr >> 8;
						memory->sram[i * 2 + 1] = mbc7->sr;
					}
				}
				mbc7->state = GBMBC7_STATE_IDLE;
			}
			break;
		case GBMBC7_STATE_EEPROM_ERAL:
			if (mbc7->writable) {
				int i;
				for (i = 0; i < 128; ++i) {
					memory->sram[i * 2] = 0xFF;
					memory->sram[i * 2 + 1] = 0xFF;
				}
			}
			mbc7->state = GBMBC7_STATE_IDLE;
			break;
		default:
			break;
		}
	} else if (GBMBC7FieldIsCS(value) && GBMBC7FieldIsCLK(old) && !GBMBC7FieldIsCLK(value)) {
		value = GBMBC7FieldSetDO(value, GBMBC7FieldGetDO(old));
	}
	mbc7->eeprom = value;
}

void _GBMMM01(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	if (!memory->mbcState.mmm01.locked) {
		switch (address >> 13) {
		case 0x0:
			memory->mbcState.mmm01.locked = true;
			GBMBCSwitchBank0(gb, memory->mbcState.mmm01.currentBank0);
			break;
		case 0x1:
			memory->mbcState.mmm01.currentBank0 &= ~0x7F;
			memory->mbcState.mmm01.currentBank0 |= value & 0x7F;
			break;
		case 0x2:
			memory->mbcState.mmm01.currentBank0 &= ~0x180;
			memory->mbcState.mmm01.currentBank0 |= (value & 0x30) << 3;
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "MMM01 unknown address: %04X:%02X", address, value);
			break;
		}
		return;
	}
	switch (address >> 13) {
	case 0x0:
		switch (value) {
		case 0xA:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		default:
			memory->sramAccess = false;
			break;
		}
		break;
	case 0x1:
		GBMBCSwitchBank(gb, value + memory->mbcState.mmm01.currentBank0);
		break;
	case 0x2:
		GBMBCSwitchSramBank(gb, value);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "MMM01 unknown address: %04X:%02X", address, value);
		break;
	}
}

void _GBHuC1(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank = value & 0x3F;
	switch (address >> 13) {
	case 0x0:
		switch (value) {
		case 0xE:
			memory->sramAccess = false;
			break;
		default:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		}
		break;
	case 0x1:
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x2:
		GBMBCSwitchSramBank(gb, value);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "HuC-1 unknown address: %04X:%02X", address, value);
		break;
	}
}

void _GBHuC3(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank = value & 0x3F;
	if (address & 0x1FFF) {
		mLOG(GB_MBC, STUB, "HuC-3 unknown value %04X:%02X", address, value);
	}

	switch (address >> 13) {
	case 0x0:
		switch (value) {
		case 0xA:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		default:
			memory->sramAccess = false;
			break;
		}
		break;
	case 0x1:
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x2:
		GBMBCSwitchSramBank(gb, bank);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "HuC-3 unknown address: %04X:%02X", address, value);
		break;
	}
}

void _GBPocketCam(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	int bank = value & 0x3F;
	switch (address >> 13) {
	case 0x0:
		switch (value) {
		case 0:
			memory->sramAccess = false;
			break;
		case 0xA:
			memory->sramAccess = true;
			GBMBCSwitchSramBank(gb, memory->sramCurrentBank);
			break;
		default:
			// TODO
			mLOG(GB_MBC, STUB, "Pocket Cam unknown value %02X", value);
			break;
		}
		break;
	case 0x1:
		GBMBCSwitchBank(gb, bank);
		break;
	case 0x2:
		if (value < 0x10) {
			GBMBCSwitchSramBank(gb, value);
			memory->mbcState.pocketCam.registersActive = false;
		} else {
			memory->mbcState.pocketCam.registersActive = true;
		}
		break;
	case 0x5:
		address &= 0x7F;
		if (address == 0 && value & 1) {
			value &= 6; // TODO: Timing
			_GBPocketCamCapture(memory);
		}
		if (address < sizeof(memory->mbcState.pocketCam.registers)) {
			memory->mbcState.pocketCam.registers[address] = value;
		}
		break;
	default:
		mLOG(GB_MBC, STUB, "Pocket Cam unknown address: %04X:%02X", address, value);
		break;
	}
}

uint8_t _GBPocketCamRead(struct GBMemory* memory, uint16_t address) {
	if (memory->mbcState.pocketCam.registersActive) {
		if ((address & 0x7F) == 0) {
			return memory->mbcState.pocketCam.registers[0];
		}
		return 0;
	}
	return memory->sramBank[address & (GB_SIZE_EXTERNAL_RAM - 1)];
}

void _GBPocketCamCapture(struct GBMemory* memory) {
	if (!memory->cam) {
		return;
	}
	const void* image = NULL;
	size_t stride;
	enum mColorFormat format;
	memory->cam->requestImage(memory->cam, &image, &stride, &format);
	if (!image) {
		return;
	}
	memset(&memory->sram[0x100], 0, GBCAM_HEIGHT * GBCAM_WIDTH / 4);
	struct GBPocketCamState* pocketCam = &memory->mbcState.pocketCam;
	size_t x, y;
	for (y = 0; y < GBCAM_HEIGHT; ++y) {
		for (x = 0; x < GBCAM_WIDTH; ++x) {
			uint32_t gray;
			uint32_t color;
			switch (format) {
			case mCOLOR_XBGR8:
			case mCOLOR_XRGB8:
			case mCOLOR_ARGB8:
			case mCOLOR_ABGR8:
				color = ((const uint32_t*) image)[y * stride + x];
				gray = (color & 0xFF) + ((color >> 8) & 0xFF) + ((color >> 16) & 0xFF);
				break;
			case mCOLOR_BGRX8:
			case mCOLOR_RGBX8:
			case mCOLOR_RGBA8:
			case mCOLOR_BGRA8:
				color = ((const uint32_t*) image)[y * stride + x];
				gray = ((color >> 8) & 0xFF) + ((color >> 16) & 0xFF) + ((color >> 24) & 0xFF);
				break;
			case mCOLOR_BGR5:
			case mCOLOR_RGB5:
			case mCOLOR_ARGB5:
			case mCOLOR_ABGR5:
				color = ((const uint16_t*) image)[y * stride + x];
				gray = ((color << 3) & 0xF8) + ((color >> 2) & 0xF8) + ((color >> 7) & 0xF8);
				break;
			case mCOLOR_BGR565:
			case mCOLOR_RGB565:
				color = ((const uint16_t*) image)[y * stride + x];
				gray = ((color << 3) & 0xF8) + ((color >> 3) & 0xFC) + ((color >> 8) & 0xF8);
				break;
			case mCOLOR_BGRA5:
			case mCOLOR_RGBA5:
				color = ((const uint16_t*) image)[y * stride + x];
				gray = ((color << 2) & 0xF8) + ((color >> 3) & 0xF8) + ((color >> 8) & 0xF8);
				break;
			default:
				mLOG(GB_MBC, WARN, "Unsupported pixel format: %X", format);
				return;
			}
			uint16_t exposure = (pocketCam->registers[2] << 8) | (pocketCam->registers[3]);
			gray = (gray + 1) * exposure / 0x300;
			// TODO: Additional processing
			int matrixEntry = 3 * ((x & 3) + 4 * (y & 3));
			if (gray < pocketCam->registers[matrixEntry + 6]) {
				gray = 0x101;
			} else if (gray < pocketCam->registers[matrixEntry + 7]) {
				gray = 0x100;
			} else if (gray < pocketCam->registers[matrixEntry + 8]) {
				gray = 0x001;
			} else {
				gray = 0;
			}
			int coord = (((x >> 3) & 0xF) * 8 + (y & 0x7)) * 2 + (y & ~0x7) * 0x20;
			uint16_t existing;
			LOAD_16LE(existing, coord + 0x100, memory->sram);
			existing |= gray << (7 - (x & 7));
			STORE_16LE(existing, coord + 0x100, memory->sram);
		}
	}
}

void _GBTAMA5(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	struct GBTAMA5State* tama5 = &memory->mbcState.tama5;
	switch (address >> 13) {
	case 0x5:
		if (address & 1) {
			tama5->reg = value;
		} else {
			value &= 0xF;
			if (tama5->reg < GBTAMA5_MAX) {
				tama5->registers[tama5->reg] = value;
				uint8_t address = ((tama5->registers[GBTAMA5_CS] << 4) & 0x10) | tama5->registers[GBTAMA5_ADDR_LO];
				uint8_t out = (tama5->registers[GBTAMA5_WRITE_HI] << 4) | tama5->registers[GBTAMA5_WRITE_LO];
				switch (tama5->reg) {
				case GBTAMA5_BANK_LO:
				case GBTAMA5_BANK_HI:
					GBMBCSwitchBank(gb, tama5->registers[GBTAMA5_BANK_LO] | (tama5->registers[GBTAMA5_BANK_HI] << 4));
					break;
				case GBTAMA5_WRITE_LO:
				case GBTAMA5_WRITE_HI:
				case GBTAMA5_CS:
					break;
				case GBTAMA5_ADDR_LO:
					switch (tama5->registers[GBTAMA5_CS] >> 1) {
					case 0x0: // RAM write
						memory->sram[address] = out;
						break;
					case 0x1: // RAM read
						break;
					default:
						mLOG(GB_MBC, STUB, "TAMA5 unknown address: %X-%02X:%02X", tama5->registers[GBTAMA5_CS] >> 1, address, out);
					}
					break;
				default:
					mLOG(GB_MBC, STUB, "TAMA5 unknown write: %02X:%X", tama5->reg, value);
					break;
				}
			} else {
				mLOG(GB_MBC, STUB, "TAMA5 unknown write: %02X", tama5->reg);
			}
		}
		break;
	default:
		mLOG(GB_MBC, STUB, "TAMA5 unknown address: %04X:%02X", address, value);
	}
}

uint8_t _GBTAMA5Read(struct GBMemory* memory, uint16_t address) {
	struct GBTAMA5State* tama5 = &memory->mbcState.tama5;
	if ((address & 0x1FFF) > 1) {
		mLOG(GB_MBC, STUB, "TAMA5 unknown address: %04X", address);
	}
	if (address & 1) {
		return 0xFF;
	} else {
		uint8_t value = 0xF0;
		uint8_t address = ((tama5->registers[GBTAMA5_CS] << 4) & 0x10) | tama5->registers[GBTAMA5_ADDR_LO];
		switch (tama5->reg) {
		case GBTAMA5_ACTIVE:
			return 0xF1;
		case GBTAMA5_READ_LO:
		case GBTAMA5_READ_HI:
			switch (tama5->registers[GBTAMA5_CS] >> 1) {
			case 1:
				value = memory->sram[address];
				break;
			default:
				mLOG(GB_MBC, STUB, "TAMA5 unknown read: %02X", tama5->reg);
				break;
			}
			if (tama5->reg == GBTAMA5_READ_HI) {
				value >>= 4;
			}
			value |= 0xF0;
			return value;
		default:
			mLOG(GB_MBC, STUB, "TAMA5 unknown read: %02X", tama5->reg);
			return 0xF1;
		}
	}
}

void _GBWisdomTree(struct GB* gb, uint16_t address, uint8_t value) {
	UNUSED(value);
	int bank = address & 0x3F;
	switch (address >> 14) {
	case 0x0:
		GBMBCSwitchBank0(gb, bank * 2);
		GBMBCSwitchBank(gb, bank * 2 + 1);
		break;
	default:
		// TODO
		mLOG(GB_MBC, STUB, "Wisdom Tree unknown address: %04X:%02X", address, value);
		break;
	}
}

void _GBPKJD(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	switch (address >> 13) {
	case 0x2:
		if (value < 8) {
			memory->directSramAccess = true;
			memory->activeRtcReg = 0;
		} else if (value >= 0xD && value <= 0xF) {
			memory->directSramAccess = false;
			memory->rtcAccess = false;
			memory->activeRtcReg = value - 8;
		}
		break;
	case 0x5:
		if (!memory->sramAccess) {
			return;
		}
		switch (memory->activeRtcReg) {
		case 0:
			memory->sramBank[address & (GB_SIZE_EXTERNAL_RAM - 1)] = value;
			break;
		case 5:
		case 6:
			memory->mbcState.pkjd.reg[memory->activeRtcReg - 5] = value;
			break;
		case 7:
			switch (value) {
			case 0x11:
				memory->mbcState.pkjd.reg[0]--;
				break;
			case 0x12:
				memory->mbcState.pkjd.reg[1]--;
				break;
			case 0x41:
				memory->mbcState.pkjd.reg[0] += memory->mbcState.pkjd.reg[1];
				break;
			case 0x42:
				memory->mbcState.pkjd.reg[1] += memory->mbcState.pkjd.reg[0];
				break;
			case 0x51:
				memory->mbcState.pkjd.reg[0]++;
				break;
			case 0x52:
				memory->mbcState.pkjd.reg[1]--;
				break;
			}
			break;
		}
		return;
	}
	_GBMBC3(gb, address, value);
}

static uint8_t _GBPKJDRead(struct GBMemory* memory, uint16_t address) {
	if (!memory->sramAccess) {
		return 0xFF;
	}
	switch (memory->activeRtcReg) {
	case 0:
		return memory->sramBank[address & (GB_SIZE_EXTERNAL_RAM - 1)];
	case 5:
	case 6:
		return memory->mbcState.pkjd.reg[memory->activeRtcReg - 5];
	default:
		return 0;
	}
}

static uint8_t _reorderBits(uint8_t input, const uint8_t* reorder) {
	uint8_t newbyte = 0;
	int i;
	for(i = 0; i < 8; ++i) {
		int oldbit = reorder[i];
		int newbit = i;
		newbyte += ((input >> oldbit) & 1) << newbit;
	}

	return newbyte;
}

static const uint8_t _bbdDataReordering[8][8] = {
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 00 - Normal
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 01 - NOT KNOWN YET
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 02 - NOT KNOWN YET
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 03 - NOT KNOWN YET
	{ 0, 5, 1, 3, 4, 2, 6, 7 }, // 04 - Garou
	{ 0, 4, 2, 3, 1, 5, 6, 7 }, // 05 - Harry
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 06 - NOT KNOWN YET
	{ 0, 1, 5, 3, 4, 2, 6, 7 }, // 07 - Digimon
};

static const uint8_t _bbdBankReordering[8][8] = {
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 00 - Normal
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 01 - NOT KNOWN YET
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 02 - NOT KNOWN YET
	{ 3, 4, 2, 0, 1, 5, 6, 7 }, // 03 - 0,1 unconfirmed. Digimon/Garou
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 04 - NOT KNOWN YET
	{ 1, 2, 3, 4, 0, 5, 6, 7 }, // 05 - 0,1 unconfirmed. Harry
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 06 - NOT KNOWN YET
	{ 0, 1, 2, 3, 4, 5, 6, 7 }, // 07 - NOT KNOWN YET
};

void  _GBBBD(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	switch (address & 0xF0FF) {
	case 0x2000:
		value = _reorderBits(value, _bbdBankReordering[memory->mbcState.bbd.bankSwapMode]);
		break;
	case 0x2001:
		memory->mbcState.bbd.dataSwapMode = value & 0x07;
		if (!(memory->mbcState.bbd.dataSwapMode == 0x07 || memory->mbcState.bbd.dataSwapMode == 0x05 || memory->mbcState.bbd.dataSwapMode == 0x04 || memory->mbcState.bbd.dataSwapMode == 0x00)) {
			mLOG(GB_MBC, STUB, "Bitswap mode unsupported: %X", memory->mbcState.bbd.dataSwapMode);
		}
		break;
	case 0x2080:
		memory->mbcState.bbd.bankSwapMode = value & 0x07;
		if (!(memory->mbcState.bbd.bankSwapMode == 0x03 || memory->mbcState.bbd.bankSwapMode == 0x05 || memory->mbcState.bbd.bankSwapMode == 0x00)) {
			mLOG(GB_MBC, STUB, "Bankswap mode unsupported: %X", memory->mbcState.bbd.dataSwapMode);
		}
		break;
	}
	_GBMBC5(gb, address, value);
}

uint8_t _GBBBDRead(struct GBMemory* memory, uint16_t address) {
	switch (address >> 14) {
	case 0:
	default:
		return memory->romBank[address & (GB_SIZE_CART_BANK0 - 1)];
	case 1:
		return _reorderBits(memory->romBank[address & (GB_SIZE_CART_BANK0 - 1)], _bbdDataReordering[memory->mbcState.bbd.dataSwapMode]);
	}
}

static const uint8_t _hitekDataReordering[8][8] = {
	{ 0, 1, 2, 3, 4, 5, 6, 7 },
	{ 0, 6, 5, 3, 4, 1, 2, 7 },
	{ 0, 5, 6, 3, 4, 2, 1, 7 },
	{ 0, 6, 2, 3, 4, 5, 1, 7 },
	{ 0, 6, 1, 3, 4, 5, 2, 7 },
	{ 0, 1, 6, 3, 4, 5, 2, 7 },
	{ 0, 2, 6, 3, 4, 1, 5, 7 },
	{ 0, 6, 2, 3, 4, 1, 5, 7 },
};

static const uint8_t _hitekBankReordering[8][8] = {
	{ 0, 1, 2, 3, 4, 5, 6, 7 },
	{ 3, 2, 1, 0, 4, 5, 6, 7 },
	{ 2, 1, 0, 3, 4, 5, 6, 7 },
	{ 1, 0, 3, 2, 4, 5, 6, 7 },
	{ 0, 3, 2, 1, 4, 5, 6, 7 },
	{ 2, 3, 0, 1, 4, 5, 6, 7 },
	{ 3, 0, 1, 2, 4, 5, 6, 7 },
	{ 2, 0, 3, 1, 4, 5, 6, 7 },
};

void  _GBHitek(struct GB* gb, uint16_t address, uint8_t value) {
	struct GBMemory* memory = &gb->memory;
	switch (address & 0xF0FF) {
	case 0x2000:
		value = _reorderBits(value, _hitekBankReordering[memory->mbcState.bbd.bankSwapMode]);
		break;
	case 0x2001:
		memory->mbcState.bbd.dataSwapMode = value & 0x07;
		break;
	case 0x2080:
		memory->mbcState.bbd.bankSwapMode = value & 0x07;
		break;
	case 0x300:
		// See hhugboy src/memory/mbc/MbcUnlHitek.cpp for commentary on this return
		return;	
	}
	_GBMBC5(gb, address, value);
}

uint8_t _GBHitekRead(struct GBMemory* memory, uint16_t address) {
	switch (address >> 14) {
	case 0:
	default:
		return memory->romBank[address & (GB_SIZE_CART_BANK0 - 1)];
	case 1:
		return _reorderBits(memory->romBank[address & (GB_SIZE_CART_BANK0 - 1)], _hitekDataReordering[memory->mbcState.bbd.dataSwapMode]);
	}
}

void GBMBCRTCRead(struct GB* gb) {
	struct GBMBCRTCSaveBuffer rtcBuffer;
	struct VFile* vf = gb->sramVf;
	if (!vf) {
		return;
	}
	vf->seek(vf, gb->sramSize, SEEK_SET);
	if (vf->read(vf, &rtcBuffer, sizeof(rtcBuffer)) < (ssize_t) sizeof(rtcBuffer) - 4) {
		return;
	}

	LOAD_32LE(gb->memory.rtcRegs[0], 0, &rtcBuffer.latchedSec);
	LOAD_32LE(gb->memory.rtcRegs[1], 0, &rtcBuffer.latchedMin);
	LOAD_32LE(gb->memory.rtcRegs[2], 0, &rtcBuffer.latchedHour);
	LOAD_32LE(gb->memory.rtcRegs[3], 0, &rtcBuffer.latchedDays);
	LOAD_32LE(gb->memory.rtcRegs[4], 0, &rtcBuffer.latchedDaysHi);
	LOAD_64LE(gb->memory.rtcLastLatch, 0, &rtcBuffer.unixTime);
}

void GBMBCRTCWrite(struct GB* gb) {
	struct VFile* vf = gb->sramVf;
	if (!vf) {
		return;
	}

	uint8_t rtcRegs[5];
	memcpy(rtcRegs, gb->memory.rtcRegs, sizeof(rtcRegs));
	time_t rtcLastLatch = gb->memory.rtcLastLatch;
	_latchRtc(gb->memory.rtc, rtcRegs, &rtcLastLatch);

	struct GBMBCRTCSaveBuffer rtcBuffer;
	STORE_32LE(rtcRegs[0], 0, &rtcBuffer.sec);
	STORE_32LE(rtcRegs[1], 0, &rtcBuffer.min);
	STORE_32LE(rtcRegs[2], 0, &rtcBuffer.hour);
	STORE_32LE(rtcRegs[3], 0, &rtcBuffer.days);
	STORE_32LE(rtcRegs[4], 0, &rtcBuffer.daysHi);
	STORE_32LE(gb->memory.rtcRegs[0], 0, &rtcBuffer.latchedSec);
	STORE_32LE(gb->memory.rtcRegs[1], 0, &rtcBuffer.latchedMin);
	STORE_32LE(gb->memory.rtcRegs[2], 0, &rtcBuffer.latchedHour);
	STORE_32LE(gb->memory.rtcRegs[3], 0, &rtcBuffer.latchedDays);
	STORE_32LE(gb->memory.rtcRegs[4], 0, &rtcBuffer.latchedDaysHi);
	STORE_64LE(gb->memory.rtcLastLatch, 0, &rtcBuffer.unixTime);

	if ((size_t) vf->size(vf) < gb->sramSize + sizeof(rtcBuffer)) {
		// Writing past the end of the file can invalidate the file mapping
		vf->unmap(vf, gb->memory.sram, gb->sramSize);
		gb->memory.sram = NULL;
	}
	vf->seek(vf, gb->sramSize, SEEK_SET);
	vf->write(vf, &rtcBuffer, sizeof(rtcBuffer));
	if (!gb->memory.sram) {
		gb->memory.sram = vf->map(vf, gb->sramSize, MAP_WRITE);
		GBMBCSwitchSramBank(gb, gb->memory.sramCurrentBank);
	}
}
