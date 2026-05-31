package net.sigmabeta.chipbox.player.common

/**
 * How long the playback stack waits for something to go right before assuming it won't — the one
 * "this is how long we're willing to put up with funny business" knob.
 *
 * Officially these are two different concerns:
 *  - the director's stall guard (the generator making no progress — no audio produced and, during
 *    a render-ahead wait, no advancing watermark), and
 *  - the generator's leading-silence abort (a track producing no *audible* audio at all).
 *
 * Practically it's the same patience, so they share one number and can't drift apart.
 */
const val STALL_TIMEOUT_MS = 5_000L
