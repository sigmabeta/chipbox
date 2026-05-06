package net.sigmabeta.chipbox.player.speaker.file

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.repository.Repository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class FileSpeakerTest {

    private val track: Track = mockk()

    private val repository: Repository = mockk()

    private val sampleRate = 48000

    private val generator = FakeGenerator(
        repository
    )

    private lateinit var underTest: FileSpeaker

    @BeforeEach
    fun setUp() {
        every { repository.getTrack(any()) } returns track
        every { track.id } returns ID_TRACK_ARBITRARY
        every { track.trackLengthMs } returns 10_000L
        every { track.path } returns "whatever lol"

        underTest = FileSpeaker(
            sampleRate,
            File(System.getProperty("user.home")),
            generator,
            Dispatchers.Default
        )
    }

    @Test
    fun `When a play command is issued, a valid WAV file is created`() = runBlocking {
        underTest.play(ID_TRACK_ARBITRARY)

        assert(underTest.bytesWritten != 0)
    }

    companion object {
        const val ID_TRACK_ARBITRARY = 1234L
    }
}