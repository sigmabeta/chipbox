#ifndef CHIPBOX_ADLIBEMU_OPL2_H
#define CHIPBOX_ADLIBEMU_OPL2_H

#include "mamedef.h"
#include "opl2.h"

typedef void (*ADL_UPDATEHANDLER)(void *param);

void *adlib_OPL2_init(UINT32 clock, UINT32 samplerate,
                      ADL_UPDATEHANDLER UpdateHandler, void *param);

void adlib_OPL2_stop(void *chip);

void adlib_OPL2_reset(void *chip);

void adlib_OPL2_writeIO(void *chip, UINT32 addr, UINT8 val);

void adlib_OPL2_getsample(void *chip, INT32 **sndptr, INT32 numsamples);

UINT32 adlib_OPL2_reg_read(void *chip, UINT32 port);

void adlib_OPL2_write_index(void *chip, UINT32 port, UINT8 val);

void adlib_OPL2_set_mute_mask(void *chip, UINT32 MuteMask);


#endif //CHIPBOX_ADLIBEMU_OPL2_H
