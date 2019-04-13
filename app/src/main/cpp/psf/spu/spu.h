#ifdef __cplusplus
extern "C" {
#endif

#include "../Misc.h"
#include "../types.h"

int SPUinit(void);

int SPUopen(void);

void SPUsetlength(s32 stop, s32 fade);

int SPUclose(void);

void SPUendflush(uint64_t *);

// External, called by SPU code.
void SPUirq(void);

int *get_muted_channel_array();

uint8_t *get_sound_buffer_ptr();

uint64_t get_sound_buffer_size();

void reset_sound_buffer_size();

int is_cpu_open();

#ifdef __cplusplus
}
#endif