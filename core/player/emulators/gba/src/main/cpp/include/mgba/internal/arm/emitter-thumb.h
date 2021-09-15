/* Copyright (c) 2013-2014 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#ifndef EMITTER_THUMB_H
#define EMITTER_THUMB_H

#include "emitter-inlines.h"

#define DECLARE_INSTRUCTION_THUMB(EMITTER, NAME) \
    EMITTER ## NAME

#define DECLARE_INSTRUCTION_WITH_HIGH_THUMB(EMITTER, NAME) \
    DECLARE_INSTRUCTION_THUMB(EMITTER, NAME ## 00), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, NAME ## 01), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, NAME ## 10), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, NAME ## 11)

#define DECLARE_THUMB_EMITTER_BLOCK(EMITTER) \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LSL1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LSR1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ASR1))), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, ADD3)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, SUB3)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, ADD1)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, SUB1)), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, MOV1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, CMP1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ADD2))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, SUB2))), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, AND), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, EOR), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, LSL2), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, LSR2), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ASR2), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ADC), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, SBC), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ROR), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, TST), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, NEG), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, CMP2), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, CMN), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ORR), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, MUL), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, BIC), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, MVN), \
    DECLARE_INSTRUCTION_WITH_HIGH_THUMB(EMITTER, ADD4), \
    DECLARE_INSTRUCTION_WITH_HIGH_THUMB(EMITTER, CMP3), \
    DECLARE_INSTRUCTION_WITH_HIGH_THUMB(EMITTER, MOV3), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, BX), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, BX), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ILL), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ILL), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LDR3))), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, STR2)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, STRH2)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, STRB2)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, LDRSB)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, LDR2)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, LDRH2)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, LDRB2)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, LDRSH)), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, STR1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LDR1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, STRB1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LDRB1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, STRH1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LDRH1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, STR3))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LDR4))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ADD5))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ADD6))), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ADD7), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, ADD7), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, SUB4), \
    DECLARE_INSTRUCTION_THUMB(EMITTER, SUB4), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, PUSH)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, PUSHR)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_8(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, POP)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, POPR)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BKPT)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, STMIA))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, LDMIA))), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BEQ)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BNE)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BCS)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BCC)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BMI)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BPL)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BVS)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BVC)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BHI)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BLS)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BGE)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BLT)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BGT)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BLE)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL)), \
    DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, SWI)), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, B))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, ILL))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BL1))), \
    DO_8(DO_4(DECLARE_INSTRUCTION_THUMB(EMITTER, BL2))) \

#endif
