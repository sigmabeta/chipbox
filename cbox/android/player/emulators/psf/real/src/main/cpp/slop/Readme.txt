slopsf - PSX (PSF1/PSF2) audio emulation core - PS1/PS2

This core emulates the PS2 IOP, which can conveniently be dropped into
PS1 compatibility mode to play PS1 sound as well. The IOP BIOS / PS2 IOP kernel is
high-level emulated (HLE).

hle.c - HLE of the PS1 IOP BIOS (A0/B0/C0 vectors, exception/IRQ entry)
  plus the boot/trap seam shared with the PS2 path.

hle_ps2.c - HLE of the PS2 IOP kernel: ELF/module loader, FILEIO,
  threads/semaphores/event flags, SPU2 DMA, and the cooperative
  scheduler.

iop.c - IOP emulation (mostly just glue between other modules)

ioptimer.c - IOP timers (root counters), both the 16-bit PS1 style and
  32-bit PS2 style.

psx.c - top-level PS1/PS2 emulation.

r3000.c - R3000 core. all C, all slow (though with the wait loop
  detection, this hasn't been a big deal).

r3000asm.c - R3000 quick assembler as used in PSFLab.

r3000dis.c - R3000 disassembler (also as used in PSFLab).

spu.c - SPU1 or SPU2 emulation.

spucore.c - Emulates one SPU core (24 channels).  PS2 has a pair of these.

vfs.c - Virtual PSF2 filesystem stuff.
