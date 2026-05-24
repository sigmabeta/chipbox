#ifndef CHIPBOX_VGMSTREAM_H
#define CHIPBOX_VGMSTREAM_H

#include <cstdint>

// subsong is 1-based; 0 means "default / first".
void loadFile(const char *path, int32_t subsong);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

// vgmstream's full supported-extension list, and the "common" subset (wav/ogg/mp3/...) that a
// chiptune player usually does NOT want vgmstream to claim. Used to derive the emulator/scanner
// extension gate from vgmstream itself rather than a hand-maintained list.
const char *const *get_supported_extensions(int *count);

const char *const *get_common_extensions(int *count);

#endif //CHIPBOX_VGMSTREAM_H
