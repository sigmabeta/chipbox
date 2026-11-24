package net.sigmabeta.chipbox.repository

import kotlin.random.Random as KotlinRandom

/**
 * The seed for a sampled query: the Home rows that show "a random selection" of games
 * ([Repository.getRecentlyAddedGames], [Repository.getUnplayedGames]).
 *
 * SQLite's `RANDOM()` cannot be seeded, so those queries order by a hash of each row id mixed
 * with this seed instead. A new seed per query ([Random]) keeps the rows random-looking from one
 * launch to the next. A [fixed] seed returns the same selection every time, which is what the
 * device benchmark needs: a Home that shows different games on every launch is a different
 * workload on every measured pass.
 *
 * Every value is in `[0, MAX_EXCLUSIVE)`, so the hash cannot overflow SQLite's 64-bit integers.
 */
fun interface SampleSeed {
    fun next(): Long

    companion object {
        /** Exclusive upper bound of a seed: 2^31, so `(id XOR seed) * multiplier` fits in 63 bits. */
        const val MAX_EXCLUSIVE: Long = 1L shl 31

        /** A fresh seed for every query: the shipped behaviour. */
        val Random: SampleSeed = SampleSeed { KotlinRandom.nextLong(0, MAX_EXCLUSIVE) }

        /** The same seed for every query, so the sampled rows are the same every launch. */
        fun fixed(seed: Long): SampleSeed {
            require(seed in 0 until MAX_EXCLUSIVE) { "seed must be in [0, $MAX_EXCLUSIVE), was $seed" }
            return SampleSeed { seed }
        }
    }
}
