#ifndef CHIPBOX_ADLIBEMU_OPL3_H
#define CHIPBOX_ADLIBEMU_OPL3_H

#include "mamedef.h"
#include "opl3.h"

void *adlib_OPL3_init(UINT32 clock, UINT32 samplerate,
                      ADL_UPDATEHANDLER UpdateHandler, void *param);

void adlib_OPL3_stop(void *chip);

void adlib_OPL3_reset(void *chip);

void adlib_OPL3_writeIO(void *chip, UINT32 addr, UINT8 val);

void adlib_OPL3_getsample(void *chip, INT32 **sndptr, INT32 numsamples);

UINT32 adlib_OPL3_reg_read(void *chip, UINT32 port);

void adlib_OPL3_write_index(void *chip, UINT32 port, UINT8 val);

void adlib_OPL3_set_mute_mask(void *chip, UINT32 MuteMask);

#endif //CHIPBOX_ADLIBEMU_OPL3_H
