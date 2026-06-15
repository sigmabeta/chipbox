package net.sigmabeta.chipbox.repository.memory

import kotlinx.coroutines.CoroutineDispatcher
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.ioDispatcher
import kotlin.random.Random

/**
 * A [MemoryRepository] pre-populated at construction with deterministic pseudo-random data — a
 * whole fake library in one line, for previews and UI tests that need a populated repository
 * rather than a hand-seeded one.
 *
 * Determinism: the same [seed] and the same sizes produce the same library every run (the only
 * randomness is the seeded PRNG). So results stay stable unless you change the seed, the sizes, or
 * the generation here. Change the seed to roll a fresh library.
 *
 * @param seed seeds the PRNG; identical seeds reproduce the same library.
 * @param games number of distinct games to create.
 * @param tracks total number of tracks, spread as evenly as possible across the games.
 * @param artists number of distinct artists; each track is credited to one of them, and every
 *   artist is used at least once (provided `tracks >= artists`).
 */
class RandomMemoryRepository(
    seed: Int = DEFAULT_SEED,
    games: Int = DEFAULT_GAMES,
    tracks: Int = DEFAULT_TRACKS,
    artists: Int = DEFAULT_ARTISTS,
    dispatcher: CoroutineDispatcher = ioDispatcher,
) : MemoryRepository(dispatcher) {

    init {
        generateLibrary(seed, games, tracks, artists).forEach(::addGame)
    }

    companion object {
        const val DEFAULT_SEED = 1234
        const val DEFAULT_GAMES = 10
        const val DEFAULT_TRACKS = 50
        const val DEFAULT_ARTISTS = 5
    }
}

// Small flavour pools — combined into "<a> <b>" so a modest pool yields plenty of distinct names.
private val ARTIST_FIRST = listOf(
    "Yuzo", "Koji", "Nobuo", "Hirokazu", "Manami", "Junko", "Michiko", "Hitoshi",
    "Motoi", "Tim", "Rob", "Jake", "Lena", "Marina", "Saki",
)
private val ARTIST_LAST = listOf(
    "Koshiro", "Kondo", "Uematsu", "Tanaka", "Matsuoka", "Follin", "Hubbard",
    "Galway", "Sakimoto", "Mitsuda", "Kikuta", "Naruke", "Ahlberg", "Shimomura",
)
private val GAME_ADJECTIVE = listOf(
    "Cosmic", "Neon", "Crystal", "Shadow", "Turbo", "Mega", "Hyper", "Astro",
    "Pixel", "Quantum", "Iron", "Golden", "Silent", "Final", "Lunar",
)
private val GAME_NOUN = listOf(
    "Quest", "Raider", "Fighter", "Legend", "Saga", "Runner", "Striker", "Odyssey",
    "Frontier", "Warrior", "Empire", "Galaxy", "Dungeon", "Circuit", "Phantom",
)
private val TRACK_WORD = listOf(
    "Stage", "Boss", "Title", "Ending", "Area", "Zone", "Theme", "Battle",
    "World", "Cave", "Sky", "Ocean", "Forest", "Castle", "Victory",
)

private const val TRACK_MIN_MS = 45_000L
private const val TRACK_MAX_MS = 240_000L

private fun generateLibrary(seed: Int, games: Int, tracks: Int, artists: Int): List<RawGame> {
    if (games <= 0) return emptyList()
    val random = Random(seed)

    val artistNames = distinctNames(random, artists, ARTIST_FIRST, ARTIST_LAST)
    val gameTitles = distinctNames(random, games, GAME_ADJECTIVE, GAME_NOUN)

    // Spread `tracks` across the games as evenly as possible (first `remainder` games get +1).
    val tracksPerGame = IntArray(games) { tracks / games }
    repeat(tracks % games) { tracksPerGame[it]++ }

    // Guarantee every artist is credited at least once: the first `artists` tracks (globally) cover
    // all artists in a shuffled order, then the rest are credited at random.
    val coverage = artistNames.indices.toMutableList().also { it.shuffle(random) }

    var trackOrdinal = 0
    return gameTitles.mapIndexed { gameIndex, gameTitle ->
        val rawTracks = (1..tracksPerGame[gameIndex]).map { trackNumber ->
            trackOrdinal++
            val artistIndex = when {
                artistNames.isEmpty() -> -1
                trackOrdinal <= coverage.size -> coverage[trackOrdinal - 1]
                else -> random.nextInt(artistNames.size)
            }
            RawTrack(
                path = "/$gameTitle/$trackOrdinal",
                source = "",
                // Ordinal keeps every track title unique (MemoryRepository keys tracks by title).
                title = "${TRACK_WORD[random.nextInt(TRACK_WORD.size)]} $trackOrdinal",
                artist = if (artistIndex < 0) "Unknown" else artistNames[artistIndex],
                game = gameTitle,
                length = random.nextLong(TRACK_MIN_MS, TRACK_MAX_MS),
                trackNumber = trackNumber,
                fadeLengthMs = 0L,
            )
        }
        RawGame(
            title = gameTitle,
            // A non-null synthetic cover URL (distinct per game → distinct generated gradient) so the
            // fake image loader actually renders cover art instead of the null-source placeholder.
            // (Artists get the same treatment in MemoryRepository.getOrAddArtistByName.)
            photoUrl = "random://game/$gameIndex",
            folderKey = "random/$gameIndex",
            folderSignature = seed.toString(),
            tracks = rawTracks,
        )
    }
}

/** `count` distinct "<a> <b>" names drawn from the pools in a seeded-shuffled order. */
private fun distinctNames(random: Random, count: Int, first: List<String>, second: List<String>): List<String> {
    if (count <= 0) return emptyList()
    val combos = ArrayList<String>(first.size * second.size)
    for (a in first) for (b in second) combos.add("$a $b")
    combos.shuffle(random)
    if (count <= combos.size) return combos.subList(0, count).toList()
    // Pool exhausted (very large request): pad deterministically with numbered extras.
    return combos + (combos.size until count).map { "Title ${it + 1}" }
}
