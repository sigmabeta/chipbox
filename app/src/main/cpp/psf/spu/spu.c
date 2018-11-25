/***************************************************************************
                            spu.c  -  description
                             -------------------
    begin                : Wed May 15 2002
    copyright            : (C) 2002 by Pete Bernert
    email                : BlackDove@addcom.de
 ***************************************************************************/

/***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version. See also the license.txt file for *
 *   additional informations.                                              *
 *                                                                         *
 ***************************************************************************/

//*************************************************************************//
// History of changes:
//
// 2003/03/01 - linuzappz
// - libraryName changes using ALSA
//
// 2003/02/28 - Pete
// - added option for type of interpolation
// - adjusted spu irqs again (Thousant Arms, Valkyrie Profile)
// - added MONO support for MSWindows DirectSound
//
// 2003/02/20 - kode54
// - amended interpolation code, goto GOON could skip initialization of gpos and cause segfault
//
// 2003/02/19 - kode54
// - moved SPU IRQ handler and changed sample flag processing
//
// 2003/02/18 - kode54
// - moved ADSR calculation outside of the sample decode loop, somehow I doubt that
//   ADSR timing is relative to the frequency at which a sample is played... I guess
//   this remains to be seen, and I don't know whether ADSR is applied to noise channels...
//
// 2003/02/09 - kode54
// - one-shot samples now process the end block before stopping
// - in light of removing fmod hack, now processing ADSR on frequency channel as well
//
// 2003/02/08 - kode54
// - replaced easy interpolation with gaussian
// - removed fmod averaging hack
// - changed .sinc to be updated from .iRawPitch, no idea why it wasn't done this way already (<- Pete: because I sometimes fail to see the obvious, haharhar :)
//
// 2003/02/08 - linuzappz
// - small bugfix for one usleep that was 1 instead of 1000
// - added iDisStereo for no stereo (Linux)
//
// 2003/01/22 - Pete
// - added easy interpolation & small noise adjustments
//
// 2003/01/19 - Pete
// - added Neill's reverb
//
// 2003/01/12 - Pete
// - added recording window handlers
//
// 2003/01/06 - Pete
// - added Neill's ADSR timings
//
// 2002/12/28 - Pete
// - adjusted spu irq handling, fmod handling and loop handling
//
// 2002/08/14 - Pete
// - added extra reverb
//
// 2002/06/08 - linuzappz
// - SPUupdate changed for SPUasync
//
// 2002/05/15 - Pete
// - generic cleanup for the Peops release
//
//*************************************************************************//

#define _IN_SPU

#define CHIPBOX_TAG "BackendPSF"

#include "stdafx.h"
#include "externals.h"
#include "spu.h"

#include "../driver.h"

#define debug_printf(...)
//#define debug_printf sexypsf_dbg_printf
//extern void sexypsf_dbg_printf(char* fmt, ...);

////////////////////////////////////////////////////////////////////////
// globals
////////////////////////////////////////////////////////////////////////

// psx buffer / addresses

static u16 regArea[0x200];
static u16 spuMem[256 * 1024];
static u8 *spuMemC;
static u8 *pSpuIrq = 0;
static u8 *pSpuBuffer;

// user settings
static int iVolume;

// MAIN infos struct for each channel

static SPUCHAN s_chan[
        MAXCHAN + 1];                     // channel + 1 infos (1 is security for fmod handling)
static REVERBInfo rvb;

static u32 dwNoiseVal = 1;                          // global noise generator

static u16 spuCtrl = 0;                             // some vars to store psx reg infos
static u16 spuStat = 0;
static u16 spuIrq = 0;
static u32 spuAddr = 0xffffffff;                    // address into spu mem
static int bSPUIsOpen = 0;

static const int f[5][2] = {
        {0,   0},
        {60,  0},
        {115, -52},
        {98,  -55},
        {122, -60}};
s16 *pNextSample;

static int muted_channels[MAXCHAN];

uint64_t g_sound_buffer_size = 0;

////////////////////////////////////////////////////////////////////////
// CODE AREA
////////////////////////////////////////////////////////////////////////

// dirty inline func includes

#include "reverb.c"
#include "adsr.c"

// Try this to increase speed.
#include "registers.c"
#include "dma.c"

////////////////////////////////////////////////////////////////////////
// helpers for so-called "gauss interpolation"

#define gval0 (((int *)(&s_chan[ch].SB[29]))[gpos])
#define gval(x) (((int *)(&s_chan[ch].SB[29]))[(gpos+x)&3])

#include "gauss_i.h"

////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////
// START SOUND... called by main thread to setup a new sound on a channel
////////////////////////////////////////////////////////////////////////

static INLINE void StartSound(int ch) {
    StartADSR(ch);

    s_chan[ch].pCurr = s_chan[ch].pStart;                   // set sample start

    s_chan[ch].s_1 = 0;                                     // init mixing vars
    s_chan[ch].s_2 = 0;
    s_chan[ch].iSBPos = 28;

    s_chan[ch].bNew = 0;                                    // init channel flags
    s_chan[ch].bStop = 0;
    s_chan[ch].bOn = 1;

    s_chan[ch].SB[29] = 0;                                  // init our interpolation helpers
    s_chan[ch].SB[30] = 0;

    s_chan[ch].spos = 0x40000L;
    s_chan[ch].SB[28] = 0;  // -> start with more decoding
}

////////////////////////////////////////////////////////////////////////
// MAIN SPU FUNCTION
// here is the main job handler... thread, timer or direct func call
// basically the whole sound processing is done in this fat func!
////////////////////////////////////////////////////////////////////////

static u32 sampcount;
static u32 decaybegin;
static u32 decayend;
static s32 shouldInfiniteLoop;
static u32 shouldCheckSilence;
static u32 silenceSamples;
static u32 nonSilenceSample;
const static u32 SilenceThreshold = 3 /*seconds*/ * 44100;
const static u32 NonSilenceThreshold = 15 /*seconds*/ * 44100;


// Return 1 if spu OK to exit
// Return 0 otherwise
static s32 SpuShouldGotoExit() {
    if (shouldInfiniteLoop) {
        /* if decayend == 0 means sexy_end is called
         *   return 1
         * if sampcount >= decayend
         *   mark song plays beyond the decayend, if not marked yet
         *   if silence samples is larger than a specific value
         *     return 1
         *   else
         *     return 0
         * else
         *   return 0
        */
        if (decayend == 0) {
            return 1;
        }
        if (sampcount >= decayend) {
            if (!shouldCheckSilence && nonSilenceSample < NonSilenceThreshold) {
                // Check silence for a SilenceThreshold
                // If the song keeps playing, do not check silence anymore
                debug_printf("Start to check silence, sampcount: %u, decayend: %u", sampcount,
                             decayend);
                shouldCheckSilence = 1;
            } else if (shouldCheckSilence && nonSilenceSample >= NonSilenceThreshold) {
                debug_printf("Stop to check silence, sampcount: %u, decayend: %u", sampcount,
                             decayend);
                shouldCheckSilence = 0;
            }
            if (silenceSamples >= SilenceThreshold) {
                return 1;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    } else {
        return sampcount >= decaybegin;
    }
}

static void CheckSilenceSamples(u8 *buffer, u32 count) {
    // Calculate silenceSample
    // If non-silenceSamples is larger than a threshold, do not count again...
    //debug_printf("CheckSilenceSamples: buffer %p, count %u\n", buffer, count);

    u32 zeroCount = 0;
    u32 i;
    for (i = 0; i < count; ++i) {
        if (buffer[i] == 0) {
            zeroCount++;
        }
    }
    if (zeroCount > (count / 2)) {
        silenceSamples += (count / 2); // 2 bytes is a sample
        debug_printf("count: %u, silence samples is added by %u and becomes %u\n", count, count / 2,
                     silenceSamples);
    } else {
        nonSilenceSample += (count / 2);
        debug_printf("count: %u, non-silence samples is added by %u and becomes %u\n", count,
                     count / 2, nonSilenceSample);
    }
}

// Counting to 65536 results in full volume offage.
void SPUsetlength(s32 stop, s32 fade) {
    if (stop == ~0) {
        decaybegin = ~0;
    } else {
        stop = (stop * 441) / 10;
        fade = (fade * 441) / 10;

        decaybegin = stop;
        decayend = stop + fade;
    }
    shouldCheckSilence = 0;
    shouldInfiniteLoop = 0;
    silenceSamples = 0;
    nonSilenceSample = 0;
}

void SPUSetInfiniteLoop(s32 infiniteLoop) {
    shouldInfiniteLoop = infiniteLoop;
}

static s32 seektime;
static s32 poo;

int sexy_seek(u32 t) {
    seektime = t * 441 / 10;
    if (seektime > sampcount) return (1);
    return (0);
}

int my_sexy_seek(s32 t) {
    seektime = sampcount + (t * 441 / 10);
//printf("seek: sampcount: %d, seektime: %d\n", sampcount/44100, seektime/44100);
    if (seektime < 0)
        seektime = 0;
    return seektime * 10 / 441;
}

int my_sexy_get_cur_time() {
    return sampcount * 10 / 441;
}

#define CLIP(_x) {if(_x>32767) _x=32767; if(_x<-32767) _x=-32767;}

int SPUasync(u32 cycles) {
    int volmul = iVolume;
    static s32 dosampies;
    s32 temp;

    poo += cycles;
    dosampies = poo / 384;
    if (!dosampies) return (1);
    poo -= dosampies * 384;
    temp = dosampies;

    while (temp) {
        s32 revLeft = 0, revRight = 0;
        s32 sl = 0, sr = 0;
        int ch, fa;

        temp--;
        //--------------------------------------------------//
        //- main channel loop                              -//
        //--------------------------------------------------//
        {
            for (ch = 0; ch < MAXCHAN; ch++)                         // loop em all.
            {
                if (muted_channels[ch]) {
                    continue;
                }

                if (s_chan[ch].bNew) StartSound(ch);             // start new sound
                if (!s_chan[ch].bOn) continue;                   // channel not playing? next


                if (s_chan[ch].iActFreq != s_chan[ch].iUsedFreq)   // new psx frequency?
                {
                    s_chan[ch].iUsedFreq = s_chan[ch].iActFreq;     // -> take it and calc steps
                    s_chan[ch].sinc = s_chan[ch].iRawPitch << 4;
                    if (!s_chan[ch].sinc) s_chan[ch].sinc = 1;
                }

                while (s_chan[ch].spos >= 0x10000L) {
                    if (s_chan[ch].iSBPos == 28)                   // 28 reached?
                    {
                        int predict_nr, shift_factor, flags, d, s;
                        u8 *start;
                        unsigned int nSample;
                        int s_1, s_2;

                        start = s_chan[ch].pCurr;                   // set up the current pos

                        if (start == (u8 *) -1)          // special "stop" sign
                        {
                            s_chan[ch].bOn = 0;                       // -> turn everything off
                            s_chan[ch].ADSRX.lVolume = 0;
                            s_chan[ch].ADSRX.EnvelopeVol = 0;
                            goto ENDX;                              // -> and done for this channel
                        }

                        s_chan[ch].iSBPos = 0;    // Reset buffer play index.

                        //////////////////////////////////////////// spu irq handler here? mmm... do it later

                        s_1 = s_chan[ch].s_1;
                        s_2 = s_chan[ch].s_2;

                        predict_nr = (int) *start;
                        start++;
                        shift_factor = predict_nr & 0xf;
                        predict_nr >>= 4;
                        flags = (int) *start;
                        start++;

                        // -------------------------------------- //
                        // Decode new samples into s_chan[ch].SB[0 through 27]
                        for (nSample = 0; nSample < 28; start++) {
                            d = (int) *start;
                            s = ((d & 0xf) << 12);
                            if (s & 0x8000) s |= 0xffff0000;

                            fa = (s >> shift_factor);
                            fa = fa + ((s_1 * f[predict_nr][0]) >> 6) +
                                 ((s_2 * f[predict_nr][1]) >> 6);
                            s_2 = s_1;
                            s_1 = fa;
                            s = ((d & 0xf0) << 8);

                            s_chan[ch].SB[nSample++] = fa;

                            if (s & 0x8000) s |= 0xffff0000;
                            fa = (s >> shift_factor);
                            fa = fa + ((s_1 * f[predict_nr][0]) >> 6) +
                                 ((s_2 * f[predict_nr][1]) >> 6);
                            s_2 = s_1;
                            s_1 = fa;

                            s_chan[ch].SB[nSample++] = fa;
                        }

                        //////////////////////////////////////////// irq check

                        if (spuCtrl & 0x40)                    // irq active?
                        {
                            if ((pSpuIrq > start - 16 &&              // irq address reached?
                                 pSpuIrq <= start) ||
                                ((flags & 1) &&
                                 // special: irq on looping addr, when stop/loop flag is set
                                 (pSpuIrq > s_chan[ch].pLoop - 16 &&
                                  pSpuIrq <= s_chan[ch].pLoop))) {
                                //extern s32 spuirqvoodoo;
                                s_chan[ch].iIrqDone = 1;                // -> debug flag
                                SPUirq();
                                //puts("IRQ");
                                //if(spuirqvoodoo!=-1)
                                //{
                                // spuirqvoodoo=temp*384;
                                // temp=0;
                                //}
                            }
                        }

                        //////////////////////////////////////////// flag handler

                        if ((flags & 4) && (!s_chan[ch].bIgnoreLoop))
                            s_chan[ch].pLoop = start - 16;               // loop adress

                        if (flags & 1)                               // 1: stop/loop
                        {
                            // We play this block out first...
                            //if(!(flags&2))                          // 1+2: do loop... otherwise: stop
                            if (flags != 3 || s_chan[ch].pLoop ==
                                              NULL)  // PETE: if we don't check exactly for 3, loop hang ups will happen (DQ4, for example)
                            {                                      // and checking if pLoop is set avoids crashes, yeah
                                start = (u8 *) -1;
                            } else {
                                start = s_chan[ch].pLoop;
                            }
                        }

                        s_chan[ch].pCurr = start;                   // store values for next cycle
                        s_chan[ch].s_1 = s_1;
                        s_chan[ch].s_2 = s_2;

                        ////////////////////////////////////////////
                    }

                    fa = s_chan[ch].SB[s_chan[ch].iSBPos++];      // get sample data

                    if ((spuCtrl & 0x4000) == 0) fa = 0;               // muted?
                    else CLIP(fa);

                    {
                        int gpos;
                        gpos = s_chan[ch].SB[28];
                        gval0 = fa;
                        gpos = (gpos + 1) & 3;
                        s_chan[ch].SB[28] = gpos;
                    }
                    s_chan[ch].spos -= 0x10000L;
                }

                ////////////////////////////////////////////////
                // noise handler... just produces some noise data
                // surely wrong... and no noise frequency (spuCtrl&0x3f00) will be used...
                // and sometimes the noise will be used as fmod modulation... pfff

                if (s_chan[ch].bNoise) {
                    //puts("Noise");
                    if ((dwNoiseVal <<= 1) & 0x80000000L) {
                        dwNoiseVal ^= 0x0040001L;
                        fa = ((dwNoiseVal >> 2) & 0x7fff);
                        fa = -fa;
                    } else fa = (dwNoiseVal >> 2) & 0x7fff;

                    // mmm... depending on the noise freq we allow bigger/smaller changes to the previous val
                    fa = s_chan[ch].iOldNoise +
                         ((fa - s_chan[ch].iOldNoise) / ((0x001f - ((spuCtrl & 0x3f00) >> 9)) + 1));
                    if (fa > 32767L) fa = 32767L;
                    if (fa < -32767L) fa = -32767L;
                    s_chan[ch].iOldNoise = fa;

                }                                            //----------------------------------------
                else                                         // NO NOISE (NORMAL SAMPLE DATA) HERE
                {
                    int vl, vr, gpos;
                    vl = (s_chan[ch].spos >> 6) & ~3;
                    gpos = s_chan[ch].SB[28];
                    vr = (gauss[vl] * gval0) >> 9;
                    vr += (gauss[vl + 1] * gval(1)) >> 9;
                    vr += (gauss[vl + 2] * gval(2)) >> 9;
                    vr += (gauss[vl + 3] * gval(3)) >> 9;
                    fa = vr >> 2;
                }

                s_chan[ch].sval = (MixADSR(ch) * fa) >> 10;     // / 1023;  // add adsr
                if (s_chan[ch].bFMod == 2)                       // fmod freq channel
                {
                    int NP = s_chan[ch + 1].iRawPitch;
                    NP = ((32768L + s_chan[ch].sval) * NP) >> 15; ///32768L;

                    if (NP > 0x3fff) NP = 0x3fff;
                    if (NP < 0x1) NP = 0x1;

                    // mmmm... if I do this, all is screwed
                    //           s_chan[ch+1].iRawPitch=NP;

                    NP = (44100L * NP) / (4096L);                     // calc frequency

                    s_chan[ch + 1].iActFreq = NP;
                    s_chan[ch + 1].iUsedFreq = NP;
                    s_chan[ch + 1].sinc = (((NP / 10) << 16) / 4410);
                    if (!s_chan[ch + 1].sinc) s_chan[ch + 1].sinc = 1;

                    // mmmm... set up freq decoding positions?
                    //           s_chan[ch+1].iSBPos=28;
                    //           s_chan[ch+1].spos=0x10000L;
                } else {
                    //////////////////////////////////////////////
                    // ok, left/right sound volume (psx volume goes from 0 ... 0x3fff)
                    int tmpl, tmpr;

                    tmpl = (s_chan[ch].sval * s_chan[ch].iLeftVolume) >> 14;
                    tmpr = (s_chan[ch].sval * s_chan[ch].iRightVolume) >> 14;

                    sl += tmpl;
                    sr += tmpr;

                    if (((rvb.Enabled >> ch) & 1) && (spuCtrl & 0x80)) {
                        revLeft += tmpl;
                        revRight += tmpr;
                    }
                }

                s_chan[ch].spos += s_chan[ch].sinc;
                ENDX:;
            }
        }

        ///////////////////////////////////////////////////////
        // mix all channels (including reverb) into one buffer
        MixREVERBLeftRight(&sl, &sr, revLeft, revRight);
        if (SpuShouldGotoExit()) {
            s32 dmul;
            if (decaybegin != ~0) // Is anyone REALLY going to be playing a song
                // for 13 hours?
            {
                // TODO: check if we should return depending on infinite loop setting
                if (sampcount >= decayend) {
                    return (0);
                }
                dmul = 256 - (256 * (sampcount - decaybegin) / (decayend - decaybegin));
                sl = (sl * dmul) >> 8;
                sr = (sr * dmul) >> 8;
            }
        }
        sampcount++;
        sl = (sl * volmul) >> 8;
        sr = (sr * volmul) >> 8;

        //{
        // static double asl=0;
        // static double asr=0;

        // asl+=(sl-asl)/5;
        // asr+=(sl-asr)/5;

        //sl-=asl;
        //sr-=asr;

        // if(sl>32767 || sl < -32767) printf("Left: %d, %f\n",sl,asl);
        // if(sr>32767 || sr < -32767) printf("Right: %d, %f\n",sl,asl);
        //}

        if (sl > 32767) sl = 32767;
        if (sl < -32767) sl = -32767;
        if (sr > 32767) sr = 32767;
        if (sr < -32767) sr = -32767;
        *pNextSample++ = sl;
        *pNextSample++ = sr;
    }

    return (1);
}

void sexy_stop(void) {
    decaybegin = decayend = 0;
}

uint64_t sexy_update_count = 0;

void SPUendflush(uint64_t *executed) {
    if ((seektime != ~0) && seektime > sampcount) {
        pNextSample = (s16 *) pSpuBuffer;
//        sexyd_update(0, 0);
    } else {
        u8 *local_ptr_spu_buffer = (u8 *) pSpuBuffer;
        u8 *local_ptr_end_of_audio = (u8 *) pNextSample;
        if (local_ptr_end_of_audio > (local_ptr_spu_buffer + 1024)) {
            uint64_t audio_size = local_ptr_end_of_audio - local_ptr_spu_buffer;
            g_sound_buffer_size = audio_size;
            if (shouldCheckSilence) {
                // check the buffer if it's almost all 0, and set silence sample count
                u32 count = audio_size;
                CheckSilenceSamples(pSpuBuffer, count);
            }
//            __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "IntExecute called %llu times",
//                                *executed);
            *executed = 0;
//            __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG,
//                                "Spu Buffer address %08X; Contains %lu bytes",
//                                local_ptr_spu_buffer, audio_size);
//            sexyd_update(local_ptr_spu_buffer, audio_size);
        } else {
            g_sound_buffer_size = 0;
        }
    }
}

#ifdef TIMEO
static u64 begintime;
static u64 SexyTime64(void)
{
 struct timeval tv;
 u64 ret;

 gettimeofday(&tv,0);
 ret=tv.tv_sec;
 ret*=1000000;
 ret+=tv.tv_usec;
 return(ret);
}
#endif
////////////////////////////////////////////////////////////////////////
// INIT/EXIT STUFF
////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////
// SPUINIT: this func will be called first by the main emu
////////////////////////////////////////////////////////////////////////

int SPUinit(void) {
    spuMemC = (u8 *) spuMem;                      // just small setup
    memset((void *) s_chan, 0, MAXCHAN * sizeof(SPUCHAN));
    memset((void *) &rvb, 0, sizeof(REVERBInfo));
    memset(regArea, 0, sizeof(regArea));
    memset(spuMem, 0, sizeof(spuMem));
    InitADSR();
    sampcount = poo = 0;
    seektime = ~0;
#ifdef TIMEO
    begintime=SexyTime64();
#endif
    return 0;
}

////////////////////////////////////////////////////////////////////////
// SETUPSTREAMS: init most of the spu buffers
////////////////////////////////////////////////////////////////////////

void SetupStreams(void) {
    int i;

    pSpuBuffer = (u8 *) malloc(32768);            // alloc mixing buffer
    pNextSample = (s16 *) pSpuBuffer;

    for (i = 0; i < MAXCHAN; i++)                                // loop sound channels
    {
        s_chan[i].ADSRX.SustainLevel = 1024;                // -> init sustain
        s_chan[i].iIrqDone = 0;
        s_chan[i].pLoop = spuMemC;
        s_chan[i].pStart = spuMemC;
        s_chan[i].pCurr = spuMemC;
    }
}

////////////////////////////////////////////////////////////////////////
// REMOVESTREAMS: free most buffer
////////////////////////////////////////////////////////////////////////

void RemoveStreams(void) {
    free(pSpuBuffer);                                     // free mixing buffer
    pSpuBuffer = NULL;

#ifdef TIMEO
    {
     u64 tmp;
     tmp=SexyTime64();
     tmp-=begintime;
     if(tmp)
      tmp=(u64)sampcount*1000000/tmp;
     printf("%lld samples per second\n",tmp);
    }
#endif
}


////////////////////////////////////////////////////////////////////////
// SPUOPEN: called by main emu after init
////////////////////////////////////////////////////////////////////////

int SPUopen(void) {
    if (bSPUIsOpen) return 0;                              // security for some stupid main emus
    spuIrq = 0;

    spuStat = spuCtrl = 0;
    spuAddr = 0xffffffff;
    dwNoiseVal = 1;

    spuMemC = (u8 *) spuMem;
    memset((void *) s_chan, 0, (MAXCHAN + 1) * sizeof(SPUCHAN));
    pSpuIrq = 0;

    iVolume = 128; //85;
    SetupStreams();                                       // prepare streaming

    bSPUIsOpen = 1;

    return 1;
}

////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////
// SPUCLOSE: called before shutdown
////////////////////////////////////////////////////////////////////////

int SPUclose(void) {
    if (!bSPUIsOpen) return 0;                             // some security

    bSPUIsOpen = 0;                                         // no more open

    RemoveStreams();                                      // no more streaming

    return 0;
}

////////////////////////////////////////////////////////////////////////
// SPUSHUTDOWN: called by main emu on final exit
////////////////////////////////////////////////////////////////////////

int SPUshutdown(void) {
    return 0;
}

int *get_muted_channel_array() {
    return muted_channels;
}

uint8_t *get_sound_buffer_ptr() {
    return pSpuBuffer;
}

uint64_t get_sound_buffer_size() {
    return g_sound_buffer_size;
}

void reset_sound_buffer_size() {
    pNextSample = (s16 *) pSpuBuffer;
}

int is_cpu_open() {
    return bSPUIsOpen;
}