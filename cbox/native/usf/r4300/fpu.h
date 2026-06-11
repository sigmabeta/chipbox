/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *   Mupen64plus - fpu.h                                                   *
 *   Mupen64Plus homepage: http://code.google.com/p/mupen64plus/           *
 *   Copyright (C) 2010 Ari64                                              *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.          *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */

#ifndef M64P_R4300_FPU_H
#define M64P_R4300_FPU_H

#include <math.h>

#include "usf/usf.h"

#include "usf/usf_internal.h"

#include "r4300.h"

#ifdef _MSC_VER
  #define M64P_FPU_INLINE static __inline
  #include <float.h>
  typedef enum { FE_TONEAREST = 0, FE_TOWARDZERO, FE_UPWARD, FE_DOWNWARD } eRoundType;
  static void fesetround(eRoundType RoundType)
  {
    static const unsigned int msRound[4] = { _RC_NEAR, _RC_CHOP, _RC_UP, _RC_DOWN };
    unsigned int oldX87, oldSSE2;
    __control87_2(msRound[RoundType], _MCW_RC, &oldX87, &oldSSE2);
  }
  static __inline double round(double x) { return floor(x + 0.5); }
  static __inline float roundf(float x) { return (float) floor(x + 0.5); }
  static __inline double trunc(double x) { return (double) (int) x; }
  static __inline float truncf(float x) { return (float) (int) x; }
  #define isnan _isnan
#else
  #define M64P_FPU_INLINE static inline
  #include <fenv.h>
#endif


M64P_FPU_INLINE void set_rounding(usf_state_t * state)
{
  switch(state->rounding_mode) {
  case 0x33F:
    fesetround(FE_TONEAREST);
    break;
  case 0xF3F:
    fesetround(FE_TOWARDZERO);
    break;
  case 0xB3F:
    fesetround(FE_UPWARD);
    break;
  case 0x73F:
    fesetround(FE_DOWNWARD);
    break;
  }
}

/* N64 FPU convert-to-integer (CVT/TRUNC/ROUND/CEIL/FLOOR.W/.L). These were implemented
 * with a bare C cast, whose result for NaN/Inf/out-of-range inputs is architecture-
 * dependent: x86 SSE (cvttss2si) yields the "integer indefinite" INT_MIN/INT64_MIN, while
 * arm64 yields 0 for NaN and saturates to INT_MAX on positive overflow. The USF rips were
 * all validated against x86, so games that hit an invalid conversion (e.g. Yoshi's Story's
 * audio driver) decoded to silence on arm64. Do the out-of-range handling explicitly so
 * every architecture reproduces the x86 "integer indefinite" reference. */
static inline int cvt_to_w(double x)
{
    if (x >= 2147483648.0 || x < -2147483648.0 || x != x)
        return -2147483647 - 1; /* INT_MIN, x86 integer-indefinite */
    return (int) x;
}
static inline long long cvt_to_l(double x)
{
    if (x >= 9223372036854775808.0 || x < -9223372036854775808.0 || x != x)
        return -9223372036854775807LL - 1; /* INT64_MIN */
    return (long long) x;
}

M64P_FPU_INLINE void cvt_s_w(usf_state_t * state, int *source,float *dest)
{
  set_rounding(state);
  *dest = (float) *source;
}
M64P_FPU_INLINE void cvt_d_w(usf_state_t * state, int *source,double *dest)
{
  (void)state;
  *dest = (double) *source;
}
M64P_FPU_INLINE void cvt_s_l(usf_state_t * state, long long *source,float *dest)
{
  set_rounding(state);
  *dest = (float) *source;
}
M64P_FPU_INLINE void cvt_d_l(usf_state_t * state, long long *source,double *dest)
{
  set_rounding(state);
  *dest = (double) *source;
}
M64P_FPU_INLINE void cvt_d_s(usf_state_t * state, float *source,double *dest)
{
  (void)state;
  *dest = (double) *source;
}
M64P_FPU_INLINE void cvt_s_d(usf_state_t * state, double *source,float *dest)
{
  set_rounding(state);
  *dest = (float) *source;
}

M64P_FPU_INLINE void round_l_s(float *source,long long *dest)
{
  *dest = cvt_to_l(roundf(*source));
}
M64P_FPU_INLINE void round_w_s(float *source,int *dest)
{
  *dest = cvt_to_w(roundf(*source));
}
M64P_FPU_INLINE void trunc_l_s(float *source,long long *dest)
{
  *dest = cvt_to_l(truncf(*source));
}
M64P_FPU_INLINE void trunc_w_s(float *source,int *dest)
{
  *dest = cvt_to_w(truncf(*source));
}
M64P_FPU_INLINE void ceil_l_s(float *source,long long *dest)
{
  *dest = cvt_to_l(ceilf(*source));
}
M64P_FPU_INLINE void ceil_w_s(float *source,int *dest)
{
  *dest = cvt_to_w(ceilf(*source));
}
M64P_FPU_INLINE void floor_l_s(float *source,long long *dest)
{
  *dest = cvt_to_l(floorf(*source));
}
M64P_FPU_INLINE void floor_w_s(float *source,int *dest)
{
  *dest = cvt_to_w(floorf(*source));
}

M64P_FPU_INLINE void round_l_d(double *source,long long *dest)
{
  *dest = cvt_to_l(round(*source));
}
M64P_FPU_INLINE void round_w_d(double *source,int *dest)
{
  *dest = cvt_to_w(round(*source));
}
M64P_FPU_INLINE void trunc_l_d(double *source,long long *dest)
{
  *dest = cvt_to_l(trunc(*source));
}
M64P_FPU_INLINE void trunc_w_d(double *source,int *dest)
{
  *dest = cvt_to_w(trunc(*source));
}
M64P_FPU_INLINE void ceil_l_d(double *source,long long *dest)
{
  *dest = cvt_to_l(ceil(*source));
}
M64P_FPU_INLINE void ceil_w_d(double *source,int *dest)
{
  *dest = cvt_to_w(ceil(*source));
}
M64P_FPU_INLINE void floor_l_d(double *source,long long *dest)
{
  *dest = cvt_to_l(floor(*source));
}
M64P_FPU_INLINE void floor_w_d(double *source,int *dest)
{
  *dest = cvt_to_w(floor(*source));
}

M64P_FPU_INLINE void cvt_w_s(usf_state_t * state, float *source,int *dest)
{
  switch(state->FCR31&3)
  {
    case 0: round_w_s(source,dest);return;
    case 1: trunc_w_s(source,dest);return;
    case 2: ceil_w_s(source,dest);return;
    case 3: floor_w_s(source,dest);return;
  }
}
M64P_FPU_INLINE void cvt_w_d(usf_state_t * state, double *source,int *dest)
{
  switch(state->FCR31&3)
  {
    case 0: round_w_d(source,dest);return;
    case 1: trunc_w_d(source,dest);return;
    case 2: ceil_w_d(source,dest);return;
    case 3: floor_w_d(source,dest);return;
  }
}
M64P_FPU_INLINE void cvt_l_s(usf_state_t * state, float *source,long long *dest)
{
  switch(state->FCR31&3)
  {
    case 0: round_l_s(source,dest);return;
    case 1: trunc_l_s(source,dest);return;
    case 2: ceil_l_s(source,dest);return;
    case 3: floor_l_s(source,dest);return;
  }
}
M64P_FPU_INLINE void cvt_l_d(usf_state_t * state, double *source,long long *dest)
{
  switch(state->FCR31&3)
  {
    case 0: round_l_d(source,dest);return;
    case 1: trunc_l_d(source,dest);return;
    case 2: ceil_l_d(source,dest);return;
    case 3: floor_l_d(source,dest);return;
  }
}

M64P_FPU_INLINE void c_f_s(usf_state_t * state)
{
  state->FCR31 &= ~0x800000;
}
M64P_FPU_INLINE void c_un_s(usf_state_t * state, float *source,float *target)
{
  state->FCR31=(isnan(*source) || isnan(*target)) ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
                          
M64P_FPU_INLINE void c_eq_s(usf_state_t * state, float *source,float *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31&=~0x800000;return;}
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ueq_s(usf_state_t * state, float *source,float *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31|=0x800000;return;}
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_olt_s(usf_state_t * state, float *source,float *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31&=~0x800000;return;}
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ult_s(usf_state_t * state, float *source,float *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31|=0x800000;return;}
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_ole_s(usf_state_t * state, float *source,float *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31&=~0x800000;return;}
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ule_s(usf_state_t * state, float *source,float *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31|=0x800000;return;}
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_sf_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31&=~0x800000;
}
M64P_FPU_INLINE void c_ngle_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31&=~0x800000;
}

M64P_FPU_INLINE void c_seq_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ngl_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_lt_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_nge_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_le_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ngt_s(usf_state_t * state, float *source,float *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_f_d(usf_state_t * state)
{
  state->FCR31 &= ~0x800000;
}
M64P_FPU_INLINE void c_un_d(usf_state_t * state, double *source,double *target)
{
  state->FCR31=(isnan(*source) || isnan(*target)) ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
                          
M64P_FPU_INLINE void c_eq_d(usf_state_t * state, double *source,double *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31&=~0x800000;return;}
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ueq_d(usf_state_t * state, double *source,double *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31|=0x800000;return;}
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_olt_d(usf_state_t * state, double *source,double *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31&=~0x800000;return;}
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ult_d(usf_state_t * state, double *source,double *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31|=0x800000;return;}
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_ole_d(usf_state_t * state, double *source,double *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31&=~0x800000;return;}
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ule_d(usf_state_t * state, double *source,double *target)
{
  if (isnan(*source) || isnan(*target)) {state->FCR31|=0x800000;return;}
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_sf_d(usf_state_t * state, double *source,double *target)
{
  (void)source; (void)target;
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31&=~0x800000;
}
M64P_FPU_INLINE void c_ngle_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31&=~0x800000;
}

M64P_FPU_INLINE void c_seq_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ngl_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source==*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_lt_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_nge_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}

M64P_FPU_INLINE void c_le_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}
M64P_FPU_INLINE void c_ngt_d(usf_state_t * state, double *source,double *target)
{
  //if (isnan(*source) || isnan(*target)) // FIXME - exception
  state->FCR31 = *source<=*target ? state->FCR31|0x800000 : state->FCR31&~0x800000;
}


M64P_FPU_INLINE void add_s(usf_state_t * state, float *source1,float *source2,float *target)
{
  set_rounding(state);
  *target=(*source1)+(*source2);
}
M64P_FPU_INLINE void sub_s(usf_state_t * state, float *source1,float *source2,float *target)
{
  set_rounding(state);
  *target=(*source1)-(*source2);
}
M64P_FPU_INLINE void mul_s(usf_state_t * state, float *source1,float *source2,float *target)
{
  set_rounding(state);
  *target=(*source1)*(*source2);
}
M64P_FPU_INLINE void div_s(usf_state_t * state, float *source1,float *source2,float *target)
{
  set_rounding(state);
  *target=(*source1)/(*source2);
}
M64P_FPU_INLINE void sqrt_s(usf_state_t * state, float *source,float *target)
{
  set_rounding(state);
  *target=sqrtf(*source);
}
M64P_FPU_INLINE void abs_s(usf_state_t * state, float *source,float *target)
{
  (void)state;
  *target=fabsf(*source);
}
M64P_FPU_INLINE void mov_s(usf_state_t * state, float *source,float *target)
{
  (void)state;
  *target=*source;
}
M64P_FPU_INLINE void neg_s(usf_state_t * state, float *source,float *target)
{
  (void)state;
  *target=-(*source);
}
M64P_FPU_INLINE void add_d(usf_state_t * state, double *source1,double *source2,double *target)
{
  set_rounding(state);
  *target=(*source1)+(*source2);
}
M64P_FPU_INLINE void sub_d(usf_state_t * state, double *source1,double *source2,double *target)
{
  set_rounding(state);
  *target=(*source1)-(*source2);
}
M64P_FPU_INLINE void mul_d(usf_state_t * state, double *source1,double *source2,double *target)
{
  set_rounding(state);
  *target=(*source1)*(*source2);
}
M64P_FPU_INLINE void div_d(usf_state_t * state, double *source1,double *source2,double *target)
{
  set_rounding(state);
  *target=(*source1)/(*source2);
}
M64P_FPU_INLINE void sqrt_d(usf_state_t * state, double *source,double *target)
{
  set_rounding(state);
  *target=sqrt(*source);
}
M64P_FPU_INLINE void abs_d(usf_state_t * state, double *source,double *target)
{
  (void)state;
  *target=fabs(*source);
}
M64P_FPU_INLINE void mov_d(usf_state_t * state, double *source,double *target)
{
  (void)state;
  *target=*source;
}
M64P_FPU_INLINE void neg_d(usf_state_t * state, double *source,double *target)
{
  (void)state;
  *target=-(*source);
}

#endif /* M64P_R4300_FPU_H */
