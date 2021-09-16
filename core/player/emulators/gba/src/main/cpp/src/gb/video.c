/* Copyright (c) 2013-2016 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <mgba/internal/gb/video.h>

#include <mgba/core/sync.h>
#include <mgba/core/thread.h>
#include <mgba/core/cache-set.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/io.h>
#include <mgba/internal/gb/renderers/cache-set.h>
#include <mgba/internal/gb/serialize.h>
#include <mgba/internal/sm83/sm83.h>

#include <mgba-util/memory.h>

mLOG_DEFINE_CATEGORY(GB_VIDEO, "GB Video", "gb.video");

static void GBVideoDummyRendererInit(struct GBVideoRenderer* renderer, enum GBModel model, bool borders);
static void GBVideoDummyRendererDeinit(struct GBVideoRenderer* renderer);
static uint8_t GBVideoDummyRendererWriteVideoRegister(struct GBVideoRenderer* renderer, uint16_t address, uint8_t value);
static void GBVideoDummyRendererWriteSGBPacket(struct GBVideoRenderer* renderer, uint8_t* data);
static void GBVideoDummyRendererWritePalette(struct GBVideoRenderer* renderer, int index, uint16_t value);
static void GBVideoDummyRendererWriteVRAM(struct GBVideoRenderer* renderer, uint16_t address);
static void GBVideoDummyRendererWriteOAM(struct GBVideoRenderer* renderer, uint16_t oam);
static void GBVideoDummyRendererDrawRange(struct GBVideoRenderer* renderer, int startX, int endX, int y);
static void GBVideoDummyRendererFinishScanline(struct GBVideoRenderer* renderer, int y);
static void GBVideoDummyRendererFinishFrame(struct GBVideoRenderer* renderer);
static void GBVideoDummyRendererEnableSGBBorder(struct GBVideoRenderer* renderer, bool enable);
static void GBVideoDummyRendererGetPixels(struct GBVideoRenderer* renderer, size_t* stride, const void** pixels);
static void GBVideoDummyRendererPutPixels(struct GBVideoRenderer* renderer, size_t stride, const void* pixels);

static void _cleanOAM(struct GBVideo* video, int y);

static void _endMode0(struct mTiming* timing, void* context, uint32_t cyclesLate);
static void _endMode1(struct mTiming* timing, void* context, uint32_t cyclesLate);
static void _endMode2(struct mTiming* timing, void* context, uint32_t cyclesLate);
static void _endMode3(struct mTiming* timing, void* context, uint32_t cyclesLate);
static void _updateFrameCount(struct mTiming* timing, void* context, uint32_t cyclesLate);

void GBVideoInit(struct GBVideo* video) {
	video->renderer = NULL;
	video->vram = anonymousMemoryMap(GB_SIZE_VRAM);
	video->frameskip = 0;

	video->modeEvent.context = video;
	video->modeEvent.name = "GB Video Mode";
	video->modeEvent.callback = NULL;
	video->modeEvent.priority = 8;
	video->frameEvent.context = video;
	video->frameEvent.name = "GB Video Frame";
	video->frameEvent.callback = _updateFrameCount;
	video->frameEvent.priority = 9;

	video->dmgPalette[0] = 0x7FFF;
	video->dmgPalette[1] = 0x56B5;
	video->dmgPalette[2] = 0x294A;
	video->dmgPalette[3] = 0x0000;
	video->dmgPalette[4] = 0x7FFF;
	video->dmgPalette[5] = 0x56B5;
	video->dmgPalette[6] = 0x294A;
	video->dmgPalette[7] = 0x0000;
	video->dmgPalette[8] = 0x7FFF;
	video->dmgPalette[9] = 0x56B5;
	video->dmgPalette[10] = 0x294A;
	video->dmgPalette[11] = 0x0000;

	video->sgbBorders = true;
}

void GBVideoReset(struct GBVideo* video) {
	video->ly = 0;
	video->x = 0;
	video->mode = 1;
	video->stat = 1;

	video->frameCounter = 0;
	video->frameskipCounter = 0;

	GBVideoSwitchBank(video, 0);
	memset(video->vram, 0, GB_SIZE_VRAM);
	video->renderer->vram = video->vram;
	memset(&video->oam, 0, sizeof(video->oam));
	video->renderer->oam = &video->oam;
	memset(&video->palette, 0, sizeof(video->palette));

	if (video->p->model & GB_MODEL_SGB) {
		if (video->renderer->sgbCharRam) {
			memset(video->renderer->sgbCharRam, 0, SGB_SIZE_CHAR_RAM);
		} else {
			video->renderer->sgbCharRam = anonymousMemoryMap(SGB_SIZE_CHAR_RAM);
		}
		if (video->renderer->sgbMapRam) {
			memset(video->renderer->sgbMapRam, 0, SGB_SIZE_MAP_RAM);
		} else {
			video->renderer->sgbMapRam = anonymousMemoryMap(SGB_SIZE_MAP_RAM);
		}
		if (video->renderer->sgbPalRam) {
			memset(video->renderer->sgbPalRam, 0, SGB_SIZE_PAL_RAM);
		} else {
			video->renderer->sgbPalRam = anonymousMemoryMap(SGB_SIZE_PAL_RAM);
		}
		if (video->renderer->sgbAttributeFiles) {
			memset(video->renderer->sgbAttributeFiles, 0, SGB_SIZE_ATF_RAM);
		} else {
			video->renderer->sgbAttributeFiles = anonymousMemoryMap(SGB_SIZE_ATF_RAM);
		}
		if (!video->renderer->sgbAttributes) {
			video->renderer->sgbAttributes = malloc(90 * 45);
		}
		memset(video->renderer->sgbAttributes, 0, 90 * 45);
		video->sgbCommandHeader = 0;
		video->sgbBufferIndex = 0;
	} else {
		if (video->renderer->sgbCharRam) {
			mappedMemoryFree(video->renderer->sgbCharRam, SGB_SIZE_CHAR_RAM);
			video->renderer->sgbCharRam = NULL;
		}
		if (video->renderer->sgbMapRam) {
			mappedMemoryFree(video->renderer->sgbMapRam, SGB_SIZE_MAP_RAM);
			video->renderer->sgbMapRam = NULL;
		}
		if (video->renderer->sgbPalRam) {
			mappedMemoryFree(video->renderer->sgbPalRam, SGB_SIZE_PAL_RAM);
			video->renderer->sgbPalRam = NULL;
		}
		if (video->renderer->sgbAttributeFiles) {
			mappedMemoryFree(video->renderer->sgbAttributeFiles, SGB_SIZE_ATF_RAM);
			video->renderer->sgbAttributeFiles = NULL;
		}
		if (video->renderer->sgbAttributes) {
			free(video->renderer->sgbAttributes);
			video->renderer->sgbAttributes = NULL;
		}
	}

	video->palette[0] = video->dmgPalette[0];
	video->palette[1] = video->dmgPalette[1];
	video->palette[2] = video->dmgPalette[2];
	video->palette[3] = video->dmgPalette[3];
	video->palette[8 * 4 + 0] = video->dmgPalette[4];
	video->palette[8 * 4 + 1] = video->dmgPalette[5];
	video->palette[8 * 4 + 2] = video->dmgPalette[6];
	video->palette[8 * 4 + 3] = video->dmgPalette[7];
	video->palette[9 * 4 + 0] = video->dmgPalette[8];
	video->palette[9 * 4 + 1] = video->dmgPalette[9];
	video->palette[9 * 4 + 2] = video->dmgPalette[10];
	video->palette[9 * 4 + 3] = video->dmgPalette[11];

	if (!video->renderer) {
		mLOG(GB_VIDEO, FATAL, "No renderer associated");
		return;
	}

	video->renderer->deinit(video->renderer);
	video->renderer->init(video->renderer, video->p->model, video->sgbBorders);

	video->renderer->writePalette(video->renderer, 0, video->palette[0]);
	video->renderer->writePalette(video->renderer, 1, video->palette[1]);
	video->renderer->writePalette(video->renderer, 2, video->palette[2]);
	video->renderer->writePalette(video->renderer, 3, video->palette[3]);
	video->renderer->writePalette(video->renderer, 8 * 4 + 0, video->palette[8 * 4 + 0]);
	video->renderer->writePalette(video->renderer, 8 * 4 + 1, video->palette[8 * 4 + 1]);
	video->renderer->writePalette(video->renderer, 8 * 4 + 2, video->palette[8 * 4 + 2]);
	video->renderer->writePalette(video->renderer, 8 * 4 + 3, video->palette[8 * 4 + 3]);
	video->renderer->writePalette(video->renderer, 9 * 4 + 0, video->palette[9 * 4 + 0]);
	video->renderer->writePalette(video->renderer, 9 * 4 + 1, video->palette[9 * 4 + 1]);
	video->renderer->writePalette(video->renderer, 9 * 4 + 2, video->palette[9 * 4 + 2]);
	video->renderer->writePalette(video->renderer, 9 * 4 + 3, video->palette[9 * 4 + 3]);
}

void GBVideoDeinit(struct GBVideo* video) {
	video->renderer->deinit(video->renderer);
	mappedMemoryFree(video->vram, GB_SIZE_VRAM);
	if (video->renderer->sgbCharRam) {
		mappedMemoryFree(video->renderer->sgbCharRam, SGB_SIZE_CHAR_RAM);
		video->renderer->sgbCharRam = NULL;
	}
	if (video->renderer->sgbMapRam) {
		mappedMemoryFree(video->renderer->sgbMapRam, SGB_SIZE_MAP_RAM);
		video->renderer->sgbMapRam = NULL;
	}
	if (video->renderer->sgbPalRam) {
		mappedMemoryFree(video->renderer->sgbPalRam, SGB_SIZE_PAL_RAM);
		video->renderer->sgbPalRam = NULL;
	}
	if (video->renderer->sgbAttributeFiles) {
		mappedMemoryFree(video->renderer->sgbAttributeFiles, SGB_SIZE_ATF_RAM);
		video->renderer->sgbAttributeFiles = NULL;
	}
	if (video->renderer->sgbAttributes) {
		free(video->renderer->sgbAttributes);
		video->renderer->sgbAttributes = NULL;
	}
}

void GBVideoDummyRendererCreate(struct GBVideoRenderer* renderer) {
	static const struct GBVideoRenderer dummyRenderer = {
		.init = GBVideoDummyRendererInit,
		.deinit = GBVideoDummyRendererDeinit,
		.writeVideoRegister = GBVideoDummyRendererWriteVideoRegister,
		.writeSGBPacket = GBVideoDummyRendererWriteSGBPacket,
		.writeVRAM = GBVideoDummyRendererWriteVRAM,
		.writeOAM = GBVideoDummyRendererWriteOAM,
		.writePalette = GBVideoDummyRendererWritePalette,
		.drawRange = GBVideoDummyRendererDrawRange,
		.finishScanline = GBVideoDummyRendererFinishScanline,
		.finishFrame = GBVideoDummyRendererFinishFrame,
		.enableSGBBorder = GBVideoDummyRendererEnableSGBBorder,
		.getPixels = GBVideoDummyRendererGetPixels,
		.putPixels = GBVideoDummyRendererPutPixels,
	};
	memcpy(renderer, &dummyRenderer, sizeof(*renderer));
}

void GBVideoAssociateRenderer(struct GBVideo* video, struct GBVideoRenderer* renderer) {
	if (video->renderer) {
		video->renderer->deinit(video->renderer);
		renderer->cache = video->renderer->cache;
		renderer->sgbRenderMode = video->renderer->sgbRenderMode;
		renderer->sgbCharRam = video->renderer->sgbCharRam;
		renderer->sgbMapRam = video->renderer->sgbMapRam;
		renderer->sgbPalRam = video->renderer->sgbPalRam;
		renderer->sgbAttributeFiles = video->renderer->sgbAttributeFiles;
		renderer->sgbAttributes = video->renderer->sgbAttributes;
	} else {
		renderer->cache = NULL;
		renderer->sgbRenderMode = 0;
		renderer->sgbCharRam = NULL;
		renderer->sgbMapRam = NULL;
		renderer->sgbPalRam = NULL;
		renderer->sgbAttributeFiles = NULL;
		renderer->sgbAttributes = NULL;
	}
	video->renderer = renderer;
	renderer->vram = video->vram;
	video->renderer->init(video->renderer, video->p->model, video->sgbBorders);
}

static bool _statIRQAsserted(GBRegisterSTAT stat) {
	// TODO: variable for the IRQ line value?
	if (GBRegisterSTATIsLYCIRQ(stat) && GBRegisterSTATIsLYC(stat)) {
		return true;
	}
	switch (GBRegisterSTATGetMode(stat)) {
	case 0:
		if (GBRegisterSTATIsHblankIRQ(stat)) {
			return true;
		}
		break;
	case 1:
		if (GBRegisterSTATIsVblankIRQ(stat)) {
			return true;
		}
		break;
	case 2:
		if (GBRegisterSTATIsOAMIRQ(stat)) {
			return true;
		}
		break;
	case 3:
		break;
	}
	return false;
}

void GBVideoSkipBIOS(struct GBVideo* video) {
	video->mode = 1;
	video->modeEvent.callback = _endMode1;

	int32_t next;
	if (video->p->model & GB_MODEL_CGB) {
		video->ly = GB_VIDEO_VERTICAL_PIXELS;
		video->p->memory.io[GB_REG_LY] = video->ly;
		video->stat = GBRegisterSTATClearLYC(video->stat);
		next = 20;
	} else {
		video->ly = GB_VIDEO_VERTICAL_TOTAL_PIXELS;
		video->p->memory.io[GB_REG_LY] = 0;
		next = 56;
	}
	video->stat = GBRegisterSTATSetMode(video->stat, video->mode);

	video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_VBLANK);
	GBUpdateIRQs(video->p);
	video->p->memory.io[GB_REG_STAT] = video->stat;
	mTimingDeschedule(&video->p->timing, &video->modeEvent);
	mTimingSchedule(&video->p->timing, &video->modeEvent, next << 1);
}

void _endMode0(struct mTiming* timing, void* context, uint32_t cyclesLate) {
	struct GBVideo* video = context;
	if (video->frameskipCounter <= 0) {
		video->renderer->finishScanline(video->renderer, video->ly);
	}
	int lyc = video->p->memory.io[GB_REG_LYC];
	int32_t next;
	++video->ly;
	video->p->memory.io[GB_REG_LY] = video->ly;
	GBRegisterSTAT oldStat = video->stat;
	if (video->ly < GB_VIDEO_VERTICAL_PIXELS) {
		next = GB_VIDEO_MODE_2_LENGTH;
		video->mode = 2;
		video->modeEvent.callback = _endMode2;
	} else {
		next = GB_VIDEO_HORIZONTAL_LENGTH;
		video->mode = 1;
		video->modeEvent.callback = _endMode1;

		mTimingDeschedule(&video->p->timing, &video->frameEvent);
		mTimingSchedule(&video->p->timing, &video->frameEvent, -cyclesLate);

		if (!_statIRQAsserted(oldStat) && GBRegisterSTATIsOAMIRQ(video->stat)) {
			video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
		}
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_VBLANK);
	}
	video->stat = GBRegisterSTATSetMode(video->stat, video->mode);
	if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
	}

	// LYC stat is delayed 1 T-cycle
	oldStat = video->stat;
	video->stat = GBRegisterSTATSetLYC(video->stat, lyc == video->ly);
	if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
	}

	GBUpdateIRQs(video->p);
	video->p->memory.io[GB_REG_STAT] = video->stat;
	mTimingSchedule(timing, &video->modeEvent, (next << 1) - cyclesLate);
}

void _endMode1(struct mTiming* timing, void* context, uint32_t cyclesLate) {
	struct GBVideo* video = context;
	if (!GBRegisterLCDCIsEnable(video->p->memory.io[GB_REG_LCDC])) {
		return;
	}
	int lyc = video->p->memory.io[GB_REG_LYC];
	// TODO: One M-cycle delay
	++video->ly;
	int32_t next;
	if (video->ly == GB_VIDEO_VERTICAL_TOTAL_PIXELS + 1) {
		video->ly = 0;
		video->p->memory.io[GB_REG_LY] = video->ly;
		next = GB_VIDEO_MODE_2_LENGTH;
		video->mode = 2;
		video->modeEvent.callback = _endMode2;
	} else if (video->ly == GB_VIDEO_VERTICAL_TOTAL_PIXELS) {
		video->p->memory.io[GB_REG_LY] = 0;
		next = GB_VIDEO_HORIZONTAL_LENGTH - 8;
	} else if (video->ly == GB_VIDEO_VERTICAL_TOTAL_PIXELS - 1) {
		video->p->memory.io[GB_REG_LY] = video->ly;
		next = 8;
	} else {
		video->p->memory.io[GB_REG_LY] = video->ly;
		next = GB_VIDEO_HORIZONTAL_LENGTH;
	}

	GBRegisterSTAT oldStat = video->stat;
	video->stat = GBRegisterSTATSetMode(video->stat, video->mode);
	video->stat = GBRegisterSTATSetLYC(video->stat, lyc == video->p->memory.io[GB_REG_LY]);
	if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
		GBUpdateIRQs(video->p);
	}
	video->p->memory.io[GB_REG_STAT] = video->stat;
	mTimingSchedule(timing, &video->modeEvent, (next << 1) - cyclesLate);
}

void _endMode2(struct mTiming* timing, void* context, uint32_t cyclesLate) {
	struct GBVideo* video = context;
	_cleanOAM(video, video->ly);
	video->x = -(video->p->memory.io[GB_REG_SCX] & 7);
	video->dotClock = mTimingCurrentTime(timing) - cyclesLate + 10 - (video->x << 1);
	int32_t next = GB_VIDEO_MODE_3_LENGTH_BASE + video->objMax * 6 - video->x;
	video->mode = 3;
	video->modeEvent.callback = _endMode3;
	GBRegisterSTAT oldStat = video->stat;
	video->stat = GBRegisterSTATSetMode(video->stat, video->mode);
	if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
		GBUpdateIRQs(video->p);
	}
	video->p->memory.io[GB_REG_STAT] = video->stat;
	mTimingSchedule(timing, &video->modeEvent, (next << 1) - cyclesLate);
}

void _endMode3(struct mTiming* timing, void* context, uint32_t cyclesLate) {
	struct GBVideo* video = context;
	GBVideoProcessDots(video, cyclesLate);
	if (video->ly < GB_VIDEO_VERTICAL_PIXELS && video->p->memory.isHdma && video->p->memory.io[GB_REG_HDMA5] != 0xFF) {
		video->p->memory.hdmaRemaining = 0x10;
		video->p->cpuBlocked = true;
		mTimingDeschedule(timing, &video->p->memory.hdmaEvent);
		mTimingSchedule(timing, &video->p->memory.hdmaEvent, 0);
	}
	video->mode = 0;
	video->modeEvent.callback = _endMode0;
	GBRegisterSTAT oldStat = video->stat;
	video->stat = GBRegisterSTATSetMode(video->stat, video->mode);
	if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
		GBUpdateIRQs(video->p);
	}
	video->p->memory.io[GB_REG_STAT] = video->stat;
	// TODO: Cache SCX & 7 in case it changes
	int32_t next = GB_VIDEO_MODE_0_LENGTH_BASE - video->objMax * 6 - (video->p->memory.io[GB_REG_SCX] & 7);
	mTimingSchedule(timing, &video->modeEvent, (next << 1) - cyclesLate);
}

void _updateFrameCount(struct mTiming* timing, void* context, uint32_t cyclesLate) {
	UNUSED(cyclesLate);
	struct GBVideo* video = context;
	if (video->p->cpu->executionState != SM83_CORE_FETCH) {
		mTimingSchedule(timing, &video->frameEvent, (4 - ((video->p->cpu->executionState + 1) & 3)) * (2 - video->p->doubleSpeed));
		return;
	}
	if (!GBRegisterLCDCIsEnable(video->p->memory.io[GB_REG_LCDC])) {
		mTimingSchedule(timing, &video->frameEvent, GB_VIDEO_TOTAL_LENGTH << 1);
	}

	--video->frameskipCounter;
	if (video->frameskipCounter < 0) {
		video->renderer->finishFrame(video->renderer);
		video->frameskipCounter = video->frameskip;
	}
	GBFrameEnded(video->p);
	mCoreSyncPostFrame(video->p->sync);
	++video->frameCounter;
	video->p->earlyExit = true;

	GBFrameStarted(video->p);
}

static void _cleanOAM(struct GBVideo* video, int y) {
	int spriteHeight = 8;
	if (GBRegisterLCDCIsObjSize(video->p->memory.io[GB_REG_LCDC])) {
		spriteHeight = 16;
	}
	int o = 0;
	int i;
	for (i = 0; i < GB_VIDEO_MAX_OBJ && o < GB_VIDEO_MAX_LINE_OBJ; ++i) {
		uint8_t oy = video->oam.obj[i].y;
		if (y < oy - 16 || y >= oy - 16 + spriteHeight) {
			continue;
		}
		++o;
	}
	video->objMax = o;
}

void GBVideoProcessDots(struct GBVideo* video, uint32_t cyclesLate) {
	if (video->mode != 3) {
		return;
	}
	int oldX = video->x;
	video->x = ((int32_t) (mTimingCurrentTime(&video->p->timing) - cyclesLate - video->dotClock)) >> 1;
	if (video->x > GB_VIDEO_HORIZONTAL_PIXELS) {
		video->x = GB_VIDEO_HORIZONTAL_PIXELS;
	} else if (video->x < 0) {
		return;
	}
	if (oldX < 0) {
		oldX = 0;
	}
	if (video->frameskipCounter <= 0) {
		video->renderer->drawRange(video->renderer, oldX, video->x, video->ly);
	}
}

void GBVideoWriteLCDC(struct GBVideo* video, GBRegisterLCDC value) {
	if (!GBRegisterLCDCIsEnable(video->p->memory.io[GB_REG_LCDC]) && GBRegisterLCDCIsEnable(value)) {
		video->mode = 2;
		video->modeEvent.callback = _endMode2;
		int32_t next = GB_VIDEO_MODE_2_LENGTH - 5; // TODO: Why is this fudge factor needed? Might be related to T-cycles for load/store differing
		mTimingDeschedule(&video->p->timing, &video->modeEvent);
		mTimingSchedule(&video->p->timing, &video->modeEvent, next << 1);

		video->ly = 0;
		video->p->memory.io[GB_REG_LY] = 0;
		GBRegisterSTAT oldStat = video->stat;
		video->stat = GBRegisterSTATSetMode(video->stat, 0);
		video->stat = GBRegisterSTATSetLYC(video->stat, video->ly == video->p->memory.io[GB_REG_LYC]);
		if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
			video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
			GBUpdateIRQs(video->p);
		}
		video->p->memory.io[GB_REG_STAT] = video->stat;
		video->renderer->writePalette(video->renderer, 0, video->palette[0]);

		mTimingDeschedule(&video->p->timing, &video->frameEvent);
	}
	if (GBRegisterLCDCIsEnable(video->p->memory.io[GB_REG_LCDC]) && !GBRegisterLCDCIsEnable(value)) {
		// TODO: Fix serialization; this gets internal and visible modes out of sync
		video->mode = 0;
		video->stat = GBRegisterSTATSetMode(video->stat, 0);
		video->p->memory.io[GB_REG_STAT] = video->stat;
		video->ly = 0;
		video->p->memory.io[GB_REG_LY] = 0;
		video->renderer->writePalette(video->renderer, 0, video->dmgPalette[0]);
	
		mTimingDeschedule(&video->p->timing, &video->modeEvent);
		mTimingDeschedule(&video->p->timing, &video->frameEvent);
		mTimingSchedule(&video->p->timing, &video->frameEvent, GB_VIDEO_TOTAL_LENGTH << 1);
	}
	video->p->memory.io[GB_REG_STAT] = video->stat;
}

void GBVideoWriteSTAT(struct GBVideo* video, GBRegisterSTAT value) {
	GBRegisterSTAT oldStat = video->stat;
	video->stat = (video->stat & 0x7) | (value & 0x78);
	if (!GBRegisterLCDCIsEnable(video->p->memory.io[GB_REG_LCDC]) || video->p->model >= GB_MODEL_CGB) {
		return;
	}
	if (!_statIRQAsserted(oldStat) && video->mode < 3) {
		// TODO: variable for the IRQ line value?
		video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
		GBUpdateIRQs(video->p);
	}
}

void GBVideoWriteLYC(struct GBVideo* video, uint8_t value) {
	GBRegisterSTAT oldStat = video->stat;
	if (GBRegisterLCDCIsEnable(video->p->memory.io[GB_REG_LCDC])) {
		video->stat = GBRegisterSTATSetLYC(video->stat, value == video->ly);
		if (!_statIRQAsserted(oldStat) && _statIRQAsserted(video->stat)) {
			video->p->memory.io[GB_REG_IF] |= (1 << GB_IRQ_LCDSTAT);
			GBUpdateIRQs(video->p);
		}
	}
	video->p->memory.io[GB_REG_STAT] = video->stat;
}

void GBVideoWritePalette(struct GBVideo* video, uint16_t address, uint8_t value) {
	if (video->p->model < GB_MODEL_SGB) {
		switch (address) {
		case GB_REG_BGP:
			video->palette[0] = video->dmgPalette[value & 3];
			video->palette[1] = video->dmgPalette[(value >> 2) & 3];
			video->palette[2] = video->dmgPalette[(value >> 4) & 3];
			video->palette[3] = video->dmgPalette[(value >> 6) & 3];
			video->renderer->writePalette(video->renderer, 0, video->palette[0]);
			video->renderer->writePalette(video->renderer, 1, video->palette[1]);
			video->renderer->writePalette(video->renderer, 2, video->palette[2]);
			video->renderer->writePalette(video->renderer, 3, video->palette[3]);
			break;
		case GB_REG_OBP0:
			video->palette[8 * 4 + 0] = video->dmgPalette[(value & 3) + 4];
			video->palette[8 * 4 + 1] = video->dmgPalette[((value >> 2) & 3) + 4];
			video->palette[8 * 4 + 2] = video->dmgPalette[((value >> 4) & 3) + 4];
			video->palette[8 * 4 + 3] = video->dmgPalette[((value >> 6) & 3) + 4];
			video->renderer->writePalette(video->renderer, 8 * 4 + 0, video->palette[8 * 4 + 0]);
			video->renderer->writePalette(video->renderer, 8 * 4 + 1, video->palette[8 * 4 + 1]);
			video->renderer->writePalette(video->renderer, 8 * 4 + 2, video->palette[8 * 4 + 2]);
			video->renderer->writePalette(video->renderer, 8 * 4 + 3, video->palette[8 * 4 + 3]);
			break;
		case GB_REG_OBP1:
			video->palette[9 * 4 + 0] = video->dmgPalette[(value & 3) + 8];
			video->palette[9 * 4 + 1] = video->dmgPalette[((value >> 2) & 3) + 8];
			video->palette[9 * 4 + 2] = video->dmgPalette[((value >> 4) & 3) + 8];
			video->palette[9 * 4 + 3] = video->dmgPalette[((value >> 6) & 3) + 8];
			video->renderer->writePalette(video->renderer, 9 * 4 + 0, video->palette[9 * 4 + 0]);
			video->renderer->writePalette(video->renderer, 9 * 4 + 1, video->palette[9 * 4 + 1]);
			video->renderer->writePalette(video->renderer, 9 * 4 + 2, video->palette[9 * 4 + 2]);
			video->renderer->writePalette(video->renderer, 9 * 4 + 3, video->palette[9 * 4 + 3]);
			break;
		}
	} else if (video->p->model >= GB_MODEL_CGB) {
		switch (address) {
		case GB_REG_BCPD:
			if (video->mode != 3) {
				if (video->bcpIndex & 1) {
					video->palette[video->bcpIndex >> 1] &= 0x00FF;
					video->palette[video->bcpIndex >> 1] |= value << 8;
				} else {
					video->palette[video->bcpIndex >> 1] &= 0xFF00;
					video->palette[video->bcpIndex >> 1] |= value;
				}
				video->renderer->writePalette(video->renderer, video->bcpIndex >> 1, video->palette[video->bcpIndex >> 1]);
			}
			if (video->bcpIncrement) {
				++video->bcpIndex;
				video->bcpIndex &= 0x3F;
				video->p->memory.io[GB_REG_BCPS] &= 0x80;
				video->p->memory.io[GB_REG_BCPS] |= video->bcpIndex;
			}
			video->p->memory.io[GB_REG_BCPD] = video->palette[video->bcpIndex >> 1] >> (8 * (video->bcpIndex & 1));
			break;
		case GB_REG_OCPD:
			if (video->mode != 3) {
				if (video->ocpIndex & 1) {
					video->palette[8 * 4 + (video->ocpIndex >> 1)] &= 0x00FF;
					video->palette[8 * 4 + (video->ocpIndex >> 1)] |= value << 8;
				} else {
					video->palette[8 * 4 + (video->ocpIndex >> 1)] &= 0xFF00;
					video->palette[8 * 4 + (video->ocpIndex >> 1)] |= value;
				}
				video->renderer->writePalette(video->renderer, 8 * 4 + (video->ocpIndex >> 1), video->palette[8 * 4 + (video->ocpIndex >> 1)]);
			}
			if (video->ocpIncrement) {
				++video->ocpIndex;
				video->ocpIndex &= 0x3F;
				video->p->memory.io[GB_REG_OCPS] &= 0x80;
				video->p->memory.io[GB_REG_OCPS] |= video->ocpIndex;
			}
			video->p->memory.io[GB_REG_OCPD] = video->palette[8 * 4 + (video->ocpIndex >> 1)] >> (8 * (video->ocpIndex & 1));
			break;
		}
	} else {
		video->renderer->writeVideoRegister(video->renderer, address, value);
	}
}

void GBVideoSwitchBank(struct GBVideo* video, uint8_t value) {
	value &= 1;
	video->vramBank = &video->vram[value * GB_SIZE_VRAM_BANK0];
	video->vramCurrentBank = value;
}

void GBVideoSetPalette(struct GBVideo* video, unsigned index, uint32_t color) {
	if (index >= 12) {
		return;
	}
	video->dmgPalette[index] = M_RGB8_TO_RGB5(color);
}

void GBVideoDisableCGB(struct GBVideo* video) {
	video->dmgPalette[0] = video->palette[0];
	video->dmgPalette[1] = video->palette[1];
	video->dmgPalette[2] = video->palette[2];
	video->dmgPalette[3] = video->palette[3];
	video->dmgPalette[4] = video->palette[8 * 4 + 0];
	video->dmgPalette[5] = video->palette[8 * 4 + 1];
	video->dmgPalette[6] = video->palette[8 * 4 + 2];
	video->dmgPalette[7] = video->palette[8 * 4 + 3];
	video->dmgPalette[8] = video->palette[9 * 4 + 0];
	video->dmgPalette[9] = video->palette[9 * 4 + 1];
	video->dmgPalette[10] = video->palette[9 * 4 + 2];
	video->dmgPalette[11] = video->palette[9 * 4 + 3];
	video->renderer->deinit(video->renderer);
	video->renderer->init(video->renderer, video->p->model, video->sgbBorders);
}

void GBVideoWriteSGBPacket(struct GBVideo* video, uint8_t* data) {
	int i;
	if (!(video->sgbCommandHeader & 7)) {
		video->sgbBufferIndex = 0;
		if ((data[0] >> 3) > SGB_OBJ_TRN) {
			video->sgbCommandHeader = 0;
			return;
		}
		video->sgbCommandHeader = data[0];
	}
	--video->sgbCommandHeader;
	memcpy(&video->sgbPacketBuffer[video->sgbBufferIndex << 4], data, 16);
	++video->sgbBufferIndex;
	if (video->sgbCommandHeader & 7) {
		return;
	}
	switch (video->sgbCommandHeader >> 3) {
	case SGB_PAL01:
		video->palette[0] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);
		video->palette[1] = video->sgbPacketBuffer[3] | (video->sgbPacketBuffer[4] << 8);
		video->palette[2] = video->sgbPacketBuffer[5] | (video->sgbPacketBuffer[6] << 8);
		video->palette[3] = video->sgbPacketBuffer[7] | (video->sgbPacketBuffer[8] << 8);

		video->palette[4] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);
		video->palette[5] = video->sgbPacketBuffer[9] | (video->sgbPacketBuffer[10] << 8);
		video->palette[6] = video->sgbPacketBuffer[11] | (video->sgbPacketBuffer[12] << 8);
		video->palette[7] = video->sgbPacketBuffer[13] | (video->sgbPacketBuffer[14] << 8);

		video->palette[8] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);
		video->palette[12] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);

		video->renderer->writePalette(video->renderer, 0, video->palette[0]);
		video->renderer->writePalette(video->renderer, 1, video->palette[1]);
		video->renderer->writePalette(video->renderer, 2, video->palette[2]);
		video->renderer->writePalette(video->renderer, 3, video->palette[3]);
		video->renderer->writePalette(video->renderer, 4, video->palette[4]);
		video->renderer->writePalette(video->renderer, 5, video->palette[5]);
		video->renderer->writePalette(video->renderer, 6, video->palette[6]);
		video->renderer->writePalette(video->renderer, 7, video->palette[7]);
		video->renderer->writePalette(video->renderer, 8, video->palette[8]);
		video->renderer->writePalette(video->renderer, 12, video->palette[12]);
		break;
	case SGB_PAL23:
		video->palette[9] = video->sgbPacketBuffer[3] | (video->sgbPacketBuffer[4] << 8);
		video->palette[10] = video->sgbPacketBuffer[5] | (video->sgbPacketBuffer[6] << 8);
		video->palette[11] = video->sgbPacketBuffer[7] | (video->sgbPacketBuffer[8] << 8);

		video->palette[13] = video->sgbPacketBuffer[9] | (video->sgbPacketBuffer[10] << 8);
		video->palette[14] = video->sgbPacketBuffer[11] | (video->sgbPacketBuffer[12] << 8);
		video->palette[15] = video->sgbPacketBuffer[13] | (video->sgbPacketBuffer[14] << 8);
		video->renderer->writePalette(video->renderer, 9, video->palette[9]);
		video->renderer->writePalette(video->renderer, 10, video->palette[10]);
		video->renderer->writePalette(video->renderer, 11, video->palette[11]);
		video->renderer->writePalette(video->renderer, 13, video->palette[13]);
		video->renderer->writePalette(video->renderer, 14, video->palette[14]);
		video->renderer->writePalette(video->renderer, 15, video->palette[15]);
		break;
	case SGB_PAL03:
		video->palette[0] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);
		video->palette[1] = video->sgbPacketBuffer[3] | (video->sgbPacketBuffer[4] << 8);
		video->palette[2] = video->sgbPacketBuffer[5] | (video->sgbPacketBuffer[6] << 8);
		video->palette[3] = video->sgbPacketBuffer[7] | (video->sgbPacketBuffer[8] << 8);

		video->palette[4] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);
		video->palette[8] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);

		video->palette[12] = video->sgbPacketBuffer[1] | (video->sgbPacketBuffer[2] << 8);
		video->palette[13] = video->sgbPacketBuffer[9] | (video->sgbPacketBuffer[10] << 8);
		video->palette[14] = video->sgbPacketBuffer[11] | (video->sgbPacketBuffer[12] << 8);
		video->palette[15] = video->sgbPacketBuffer[13] | (video->sgbPacketBuffer[14] << 8);
		video->renderer->writePalette(video->renderer, 0, video->palette[0]);
		video->renderer->writePalette(video->renderer, 1, video->palette[1]);
		video->renderer->writePalette(video->renderer, 2, video->palette[2]);
		video->renderer->writePalette(video->renderer, 3, video->palette[3]);
		video->renderer->writePalette(video->renderer, 4, video->palette[4]);
		video->renderer->writePalette(video->renderer, 8, video->palette[8]);
		video->renderer->writePalette(video->renderer, 12, video->palette[12]);
		video->renderer->writePalette(video->renderer, 13, video->palette[13]);
		video->renderer->writePalette(video->renderer, 14, video->palette[14]);
		video->renderer->writePalette(video->renderer, 15, video->palette[15]);
		break;
	case SGB_PAL12:
		video->palette[5] = video->sgbPacketBuffer[3] | (video->sgbPacketBuffer[4] << 8);
		video->palette[6] = video->sgbPacketBuffer[5] | (video->sgbPacketBuffer[6] << 8);
		video->palette[7] = video->sgbPacketBuffer[7] | (video->sgbPacketBuffer[8] << 8);

		video->palette[9] = video->sgbPacketBuffer[9] | (video->sgbPacketBuffer[10] << 8);
		video->palette[10] = video->sgbPacketBuffer[11] | (video->sgbPacketBuffer[12] << 8);
		video->palette[11] = video->sgbPacketBuffer[13] | (video->sgbPacketBuffer[14] << 8);
		video->renderer->writePalette(video->renderer, 5, video->palette[5]);
		video->renderer->writePalette(video->renderer, 6, video->palette[6]);
		video->renderer->writePalette(video->renderer, 7, video->palette[7]);
		video->renderer->writePalette(video->renderer, 9, video->palette[9]);
		video->renderer->writePalette(video->renderer, 10, video->palette[10]);
		video->renderer->writePalette(video->renderer, 11, video->palette[11]);
		break;
	case SGB_PAL_SET:
		for (i = 0; i < 4; ++i) {
			uint16_t entry = (video->sgbPacketBuffer[2 + (i * 2)] << 8) | video->sgbPacketBuffer[1 + (i * 2)];
			if (entry >= 0x200) {
				mLOG(GB, STUB, "Unimplemented SGB palette overflow: %03X", entry);
				continue;
			}
			LOAD_16LE(video->palette[i * 4 + 0], entry * 8 + 0, video->renderer->sgbPalRam);
			video->renderer->writePalette(video->renderer, i * 4 + 0, video->palette[i * 4 + 0]);
			LOAD_16LE(video->palette[i * 4 + 1], entry * 8 + 2, video->renderer->sgbPalRam);
			video->renderer->writePalette(video->renderer, i * 4 + 1, video->palette[i * 4 + 1]);
			LOAD_16LE(video->palette[i * 4 + 2], entry * 8 + 4, video->renderer->sgbPalRam);
			video->renderer->writePalette(video->renderer, i * 4 + 2, video->palette[i * 4 + 2]);
			LOAD_16LE(video->palette[i * 4 + 3], entry * 8 + 6, video->renderer->sgbPalRam);
			video->renderer->writePalette(video->renderer, i * 4 + 3, video->palette[i * 4 + 3]);
		}
		break;
	case SGB_ATTR_BLK:
	case SGB_ATTR_DIV:
	case SGB_ATTR_CHR:
	case SGB_ATTR_LIN:
	case SGB_PAL_TRN:
	case SGB_ATRC_EN:
	case SGB_CHR_TRN:
	case SGB_PCT_TRN:
	case SGB_ATTR_TRN:
	case SGB_ATTR_SET:
		break;
	case SGB_MLT_REQ:
		if ((video->sgbPacketBuffer[1] & 0x3) == 2) { // XXX: This unmasked increment appears to be an SGB hardware bug
			++video->p->sgbCurrentController;
		}
		video->p->sgbControllers = video->sgbPacketBuffer[1] & 0x3;
		video->p->sgbCurrentController &= video->p->sgbControllers;
		return;
	case SGB_MASK_EN:
		video->renderer->sgbRenderMode = video->sgbPacketBuffer[1] & 0x3;
		break;
	default:
		mLOG(GB, STUB, "Unimplemented SGB command: %02X", video->sgbPacketBuffer[0] >> 3);
		return;
	}
	video->renderer->writeSGBPacket(video->renderer, video->sgbPacketBuffer);
}

static void GBVideoDummyRendererInit(struct GBVideoRenderer* renderer, enum GBModel model, bool borders) {
	UNUSED(renderer);
	UNUSED(model);
	UNUSED(borders);
	// Nothing to do
}

static void GBVideoDummyRendererDeinit(struct GBVideoRenderer* renderer) {
	UNUSED(renderer);
	// Nothing to do
}

static uint8_t GBVideoDummyRendererWriteVideoRegister(struct GBVideoRenderer* renderer, uint16_t address, uint8_t value) {
	if (renderer->cache) {
		GBVideoCacheWriteVideoRegister(renderer->cache, address, value);
	}
	return value;
}

static void GBVideoDummyRendererWriteSGBPacket(struct GBVideoRenderer* renderer, uint8_t* data) {
	UNUSED(renderer);
	UNUSED(data);
}

static void GBVideoDummyRendererWriteVRAM(struct GBVideoRenderer* renderer, uint16_t address) {
	if (renderer->cache) {
		mCacheSetWriteVRAM(renderer->cache, address);
	}
}

static void GBVideoDummyRendererWriteOAM(struct GBVideoRenderer* renderer, uint16_t oam) {
	UNUSED(renderer);
	UNUSED(oam);
	// Nothing to do
}

static void GBVideoDummyRendererWritePalette(struct GBVideoRenderer* renderer, int index, uint16_t value) {
	if (renderer->cache) {
		mCacheSetWritePalette(renderer->cache, index, mColorFrom555(value));
	}
}

static void GBVideoDummyRendererDrawRange(struct GBVideoRenderer* renderer, int startX, int endX, int y) {
	UNUSED(renderer);
	UNUSED(endX);
	UNUSED(startX);
	UNUSED(y);
	// Nothing to do
}

static void GBVideoDummyRendererFinishScanline(struct GBVideoRenderer* renderer, int y) {
	UNUSED(renderer);
	UNUSED(y);
	// Nothing to do
}

static void GBVideoDummyRendererFinishFrame(struct GBVideoRenderer* renderer) {
	UNUSED(renderer);
	// Nothing to do
}

static void GBVideoDummyRendererEnableSGBBorder(struct GBVideoRenderer* renderer, bool enable) {
	UNUSED(renderer);
	UNUSED(enable);
	// Nothing to do
}

static void GBVideoDummyRendererGetPixels(struct GBVideoRenderer* renderer, size_t* stride, const void** pixels) {
	UNUSED(renderer);
	UNUSED(stride);
	UNUSED(pixels);
	// Nothing to do
}

static void GBVideoDummyRendererPutPixels(struct GBVideoRenderer* renderer, size_t stride, const void* pixels) {
	UNUSED(renderer);
	UNUSED(stride);
	UNUSED(pixels);
	// Nothing to do
}

void GBVideoSerialize(const struct GBVideo* video, struct GBSerializedState* state) {
	STORE_16LE(video->x, 0, &state->video.x);
	STORE_16LE(video->ly, 0, &state->video.ly);
	STORE_32LE(video->frameCounter, 0, &state->video.frameCounter);
	STORE_32LE(video->dotClock, 0, &state->video.dotCounter);
	state->video.vramCurrentBank = video->vramCurrentBank;

	GBSerializedVideoFlags flags = 0;
	flags = GBSerializedVideoFlagsSetBcpIncrement(flags, video->bcpIncrement);
	flags = GBSerializedVideoFlagsSetOcpIncrement(flags, video->ocpIncrement);
	flags = GBSerializedVideoFlagsSetMode(flags, video->mode);
	flags = GBSerializedVideoFlagsSetNotModeEventScheduled(flags, !mTimingIsScheduled(&video->p->timing, &video->modeEvent));
	flags = GBSerializedVideoFlagsSetNotFrameEventScheduled(flags, !mTimingIsScheduled(&video->p->timing, &video->frameEvent));
	state->video.flags = flags;
	STORE_16LE(video->bcpIndex, 0, &state->video.bcpIndex);
	STORE_16LE(video->ocpIndex, 0, &state->video.ocpIndex);

	size_t i;
	for (i = 0; i < 64; ++i) {
		STORE_16LE(video->palette[i], i * 2, state->video.palette);
	}

	STORE_32LE(video->modeEvent.when - mTimingCurrentTime(&video->p->timing), 0, &state->video.nextMode);
	STORE_32LE(video->frameEvent.when - mTimingCurrentTime(&video->p->timing), 0, &state->video.nextFrame);

	memcpy(state->vram, video->vram, GB_SIZE_VRAM);
	memcpy(state->oam, &video->oam.raw, GB_SIZE_OAM);
}

void GBVideoDeserialize(struct GBVideo* video, const struct GBSerializedState* state) {
	LOAD_16LE(video->x, 0, &state->video.x);
	LOAD_16LE(video->ly, 0, &state->video.ly);
	LOAD_32LE(video->frameCounter, 0, &state->video.frameCounter);
	LOAD_32LE(video->dotClock, 0, &state->video.dotCounter);
	video->x = (int16_t) video->x; // Ensure proper sign extension--the LOAD_16 is unsigned

	video->vramCurrentBank = state->video.vramCurrentBank;

	GBSerializedVideoFlags flags = state->video.flags;
	video->bcpIncrement = GBSerializedVideoFlagsGetBcpIncrement(flags);
	video->ocpIncrement = GBSerializedVideoFlagsGetOcpIncrement(flags);
	video->mode = GBSerializedVideoFlagsGetMode(flags);
	LOAD_16LE(video->bcpIndex, 0, &state->video.bcpIndex);
	video->bcpIndex &= 0x3F;
	LOAD_16LE(video->ocpIndex, 0, &state->video.ocpIndex);
	video->ocpIndex &= 0x3F;

	switch (video->mode) {
	case 0:
		video->modeEvent.callback = _endMode0;
		break;
	case 1:
		video->modeEvent.callback = _endMode1;
		break;
	case 2:
		video->modeEvent.callback = _endMode2;
		break;
	case 3:
		video->modeEvent.callback = _endMode3;
		break;
	}

	uint32_t when;
	LOAD_32LE(when, 0, &state->video.nextMode);
	if (!GBSerializedVideoFlagsIsNotModeEventScheduled(flags)) {
		mTimingSchedule(&video->p->timing, &video->modeEvent, when);
	} else {
		video->modeEvent.when = when + mTimingCurrentTime(&video->p->timing);
	}
	LOAD_32LE(when, 0, &state->video.nextFrame);
	if (!GBSerializedVideoFlagsIsNotFrameEventScheduled(flags)) {
		mTimingSchedule(&video->p->timing, &video->frameEvent, when);
	} else {
		video->frameEvent.when = when + mTimingCurrentTime(&video->p->timing);
	}

	video->renderer->deinit(video->renderer);
	video->renderer->init(video->renderer, video->p->model, video->sgbBorders);

	size_t i;
	for (i = 0; i < 64; ++i) {
		LOAD_16LE(video->palette[i], i * 2, state->video.palette);
		video->renderer->writePalette(video->renderer, i, video->palette[i]);
	}

	memcpy(video->vram, state->vram, GB_SIZE_VRAM);
	memcpy(&video->oam.raw, state->oam, GB_SIZE_OAM);

	_cleanOAM(video, video->ly);
	GBVideoSwitchBank(video, video->vramCurrentBank);
}
