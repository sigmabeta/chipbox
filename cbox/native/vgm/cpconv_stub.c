// Minimal stub for libvgm's CPConv_* charset-conversion API.
//
// VGMPlayer always calls CPConv_Init / CPConv_Deinit / CPConv_StrConvert to
// decode UTF-16 GD3 tags into UTF-8 (vgmplayer.cpp lines 189, 206, 478). We
// don't consume those tags from native code — Kotlin VgmReader extracts the
// GD3 metadata independently on the JVM side — but the linker still needs
// these symbols. Conversion always reports failure here, which makes VGMPlayer
// leave the tag empty; that's fine.
//
// Compiled in lieu of StrUtils-CPConv_IConv.c, which requires iconv (not
// available in Android NDK at our min API).

#include "utils/StrUtils.h"

#include <stdlib.h>

UINT8 CPConv_Init(CPCONV** retCPC, const char* cpFrom, const char* cpTo)
{
    (void)cpFrom;
    (void)cpTo;
    *retCPC = NULL;
    return 0x01; // non-zero = failure
}

void CPConv_Deinit(CPCONV* cpc)
{
    (void)cpc;
}

UINT8 CPConv_StrConvert(CPCONV* cpc, size_t* outSize, char** outStr,
                        size_t inSize, const char* inStr)
{
    (void)cpc;
    (void)inSize;
    (void)inStr;
    if (outSize != NULL) *outSize = 0;
    if (outStr != NULL && *outStr == NULL) {
        // libvgm expects us to allocate when outStr starts as NULL.
        *outStr = (char*)malloc(1);
        if (*outStr != NULL) (*outStr)[0] = '\0';
    } else if (outStr != NULL && *outStr != NULL) {
        (*outStr)[0] = '\0';
    }
    return 0x01;
}
