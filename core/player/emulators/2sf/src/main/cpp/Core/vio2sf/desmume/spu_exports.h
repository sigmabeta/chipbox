#ifndef _SPU_EXPORTS_H
#define _SPU_EXPORTS_H

#ifndef _SPU_CPP_

#include "types.h"

typedef struct NDS_state NDS_state;

void SPU_WriteLong(NDS_state *, u32 addr, u32 val);
void SPU_WriteByte(NDS_state *, u32 addr, u8 val);
void SPU_WriteWord(NDS_state *, u32 addr, u16 val);
void SPU_EmulateSamples(NDS_state *, int numsamples);
int SPU_ChangeSoundCore(NDS_state *, int coreid, int buffersize);
void SPU_Reset(NDS_state *);
int SPU_Init(NDS_state *, int, int);
void SPU_DeInit(NDS_state *);
void SPU_Pause(NDS_state *state, int pause);
void SPU_SetVolume(NDS_state *state, int volume);

#endif //_SPU_CPP_

#endif //_SPU_EXPORTS_H
