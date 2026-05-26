package net.sigmabeta.chipbox.player.director

import net.sigmabeta.chipbox.models.Track

/**
 * A human-readable error emitted by [Director.errorEvents], paired with the track the error is
 * attributed to.
 *
 * The director resolves [track] at emit time from its own notion of "what's loading or playing"
 * (the setlist position the user just advanced to), so consumers get the right attribution even
 * when the speaker hasn't yet emitted a `TrackChange` for that track — e.g. when the generator
 * errors loading a track that never produced a single buffer. UI should prefer [track] over
 * whatever it last saw from [Director.metadataState] when prefixing the error.
 *
 * [track] is null only when the error is genuinely session-scoped (no active track yet) or the
 * track lookup itself failed (e.g. the "couldn't load track metadata" path). Don't render a
 * track prefix in that case.
 */
data class PlayerErrorEvent(
    val message: String,
    val track: Track?,
)
