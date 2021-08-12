package net.sigmabeta.chipbox.models

fun RawTrack.toTrack(id: Long) = Track(
    id,
    path,
    title,
    null,
    null,
    length
)

fun RawGame.toGame(
    id: Long,
    artists: List<Artist>,
    tracks: List<Track>
) = Game(
    id,
    title,
    photoUrl,
    artists,
    tracks
)

//fun RawTrack.toTrackEntity(): Track {
//
//}

