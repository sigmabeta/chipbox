package net.sigmabeta.chipbox.backend

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.PlaybackState
import io.realm.Realm
import net.sigmabeta.chipbox.model.audio.AudioBuffer
import net.sigmabeta.chipbox.model.audio.AudioConfig
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.model.database.findFirstSync
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.util.external.*
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Player @Inject constructor(val audioConfig: AudioConfig,
                                 val audioManager: AudioManager,
                                 val context: Context): AudioManager.OnAudioFocusChangeListener {
    var backendView: BackendView? = null

    val updater = UiUpdater()

    var state = PlaybackState.STATE_STOPPED
        set (value) {
            field = value
            updater.send(StateEvent(value))
        }

    var tempo: Int? = 100
        set (value: Int?) {
            if (value != null) {
                field = value
                logInfo("[Player] Setting tempo to $value")
                setTempoNative(value / 100.0)
            } else {
                field = 100
            }
        }

    var voices: MutableList<Voice>? = null
        get () {
            if (field == null) {
                field = getVoicesWrapper()
            }
            return field
        }

    var playingShuffledTrack = false
    var shuffle = false
        set (value) {
            if (value == true) {
                generateRandomPositionArray()
            } else {
                playbackQueuePosition = shuffledPositionQueue?.get(playbackQueuePosition) ?: 0
                shuffledPositionQueue = null
            }
            field = value
        }

    var repeat = REPEAT_OFF

    var position = 0L

    var playbackQueue: MutableList<String?> = ArrayList<String?>(0)
    var playbackQueuePosition: Int = 0
    var actualPlaybackQueuePosition: Int = 0

    var shuffledPositionQueue: MutableList<Int>? = null

    var queuedSeekPosition: Int? = null

    var playbackTimePosition: Long = 0

    var queuedTrackId: String? = null

    var pausedTrackId: String? = null
    var playingTrackId: String? = null
        set (value) {
            teardown()

            if (value != null) {
                val realm = Realm.getDefaultInstance()
                val track = realm.findFirstSync(Track::class.java, value)

                if (track != null) {
                    playingGameId = track.game?.id

                    loadTrackNative(track,
                            audioConfig.sampleRate,
                            audioConfig.bufferSizeShorts.toLong())
                } else {
                    playingGameId = null
                }

                voices = null
                tempo = null

                updater.send(TrackEvent(value))
            } else {
                playingGameId = null
            }

            if (state != PlaybackState.STATE_PLAYING) {
                audioTrack?.flush()
            }

            field = value
        }

    var playingGameId: String? = null
        set (value) {
            if (field != value) {
                updater.send(GameEvent(value))
            }

            field = value
        }

    var fullBuffers = ArrayBlockingQueue<AudioBuffer>(READ_AHEAD_BUFFER_SIZE)
    var emptyBuffers = ArrayBlockingQueue<AudioBuffer>(READ_AHEAD_BUFFER_SIZE)

    var audioTrack: AudioTrack? = null

    var ducking = false
    var focusLossPaused = false

    val stats = StatsManager(audioConfig)

    fun readerLoop() {
        // Pre-seed the emptyQueue.
        while (true) {
            try {
                emptyBuffers.add(AudioBuffer(audioConfig.bufferSizeShorts))
            } catch (ex: IllegalStateException) {
                break
            }
        }

        val timeout = 1000L

        while (state == PlaybackState.STATE_PLAYING) {
            if (playingTrackId == null && pausedTrackId != null) {
                playingTrackId = pausedTrackId
                pausedTrackId = null
            }

            queuedTrackId?.let {
                playingShuffledTrack = shuffle
                playingTrackId = it
                queuedTrackId = null
            }

            queuedSeekPosition?.let {
                seekNative(it)
                playbackTimePosition = it.toLong()
                queuedSeekPosition = null
            }

            if (isTrackOver()) {
                logVerbose("[Player] Track has ended.")

                if (!isNextTrackAvailable()) {
                    logInfo("[Player] No more tracks to play.")
                    stop()
                    break
                } else {
                    getNextTrack()
                }
            }

            val audioBuffer = emptyBuffers.poll(timeout, TimeUnit.MILLISECONDS)

            if (audioBuffer == null) {
                logError("[Player] Couldn't get an empty AudioBuffer after ${timeout}ms.")
                stop()
                break
            }

            // Get the next samples from the native player.
            synchronized(playingTrackId ?: break) {
                readNextSamples(audioBuffer.buffer)
            }

            val error = getLastError()

            if (error == null) {
                // Check this so that we don't put one last buffer into the full queue after it's cleared.
                if (state == PlaybackState.STATE_PLAYING) {
                    fullBuffers.put(audioBuffer)
                }
            } else {
                logError("[Player] GME Error: ${error}")
                stop()
                break
            }
        }

        logVerbose("[Player] Clearing empty buffer queue...")

        playbackTimePosition = 0
        emptyBuffers.clear()

        logVerbose("[Player] Reader loop has ended.")
    }

    fun writerLoop() {
        logDebug("[Player] Starting writer loop.")

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        // If audioTrack is not null, we're likely resuming a paused track.
        if (audioTrack == null) {
            if (!initializeAudioTrack())
                return
        } else {
            logVerbose("[Player] AudioTrack already setup; resuming playback.")
        }

        var duckVolume = 1.0f
        var writerIndex = 0

        // Begin playback loop
        audioTrack?.play()

        val timeout = 5000L

        while (state == PlaybackState.STATE_PLAYING) {
            var audioBuffer = fullBuffers.poll()

            if (audioBuffer == null) {
                logError("[Player] Buffer underrun.")
                stats.underrunCount += 1

                audioBuffer = fullBuffers.poll(timeout, TimeUnit.MILLISECONDS)

                if (audioBuffer == null) {
                    logError("[Player] Couldn't get a full buffer after ${timeout}ms; stopping...")
                    state = PlaybackState.STATE_ERROR
                    break
                }
            }

            // Check if necessary to make volume adjustments
            if (ducking) {
                logDebug("[Player] Ducking behind other app...")

                if (duckVolume > 0.3f) {
                    duckVolume -= 0.4f
                    logVerbose("[Player] Lowering volume to $duckVolume...")
                }

                audioTrack?.setVolume(duckVolume)
            } else {
                if (duckVolume < 1.0f) {
                    duckVolume += 0.1f
                    logVerbose("[Player] Raising volume to $duckVolume...")

                    audioTrack?.setVolume(duckVolume)
                }
            }

            val bytesWritten = audioTrack?.write(audioBuffer.buffer, 0, audioConfig.bufferSizeShorts)
                    ?: ERROR_AUDIO_TRACK_NULL

            emptyBuffers.put(audioBuffer)

            logProblems(bytesWritten)

            writerIndex += 1
            if (writerIndex == READ_AHEAD_BUFFER_SIZE) {
                writerIndex = 0
            }
        }

        logVerbose("[Player] Clearing full buffer queue...")

        fullBuffers.clear()

        logVerbose("[Player] Writer loop has ended.")
    }

    fun play() {
        if (state == PlaybackState.STATE_PLAYING) {
            logError("[Player] Received play command, but already PLAYING a track: ${playingTrackId}")
            return
        }

        val focusResult = requestAudioFocus()
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            val localTrackId = queuedTrackId ?: pausedTrackId

            if (localTrackId != null) {
                logVerbose("[Player] Playing track: $localTrackId")
                state = PlaybackState.STATE_PLAYING

                if (backendView == null) {
                    PlayerService.start(context)
                } else {
                    backendView?.play()
                }

                // Start a thread for the playback loop.
                Thread({ writerLoop() }, "writer").start()
                Thread({ readerLoop() }, "reader").start()
            } else {
                logError("[Player] Received play command, but no Track selected.")
            }
        } else {
            logError("[Player] Unable to gain audio focus.")
        }
    }

    fun play(trackId: String) {
        logVerbose("[Player] Playing new track: ${trackId}")

        queuedTrackId = trackId

        playbackQueue = ArrayList<String?>(1)
        playbackQueue.add(trackId)
        playbackQueuePosition = 0

        play()
    }

    fun play(playbackQueue: MutableList<String?>, position: Int) {
        if (position < playbackQueue.size) {
            logVerbose("[Player] Playing new playlist, starting from track ${position} of ${playbackQueue.size}.")

            this.playbackQueue = playbackQueue
            playbackQueuePosition = position

            if (shuffle) {
                generateRandomPositionArray()
            }

            queuedTrackId = getTrackIdAt(position)

            play()
        } else {
            logError("[Player] Tried to play new playlist, but invalid track number: ${position} of ${playbackQueue.size}")
        }
    }

    fun play(position: Int) {
        if (position < playbackQueue.size) {
            playbackQueuePosition = position

            // TODO This is a bad design, see comment below
            // Don't use getTrackAt() here because we don't want shuffle to affect explicit user input
            val trackId = playbackQueue.get(position)
            queuedTrackId = trackId

            actualPlaybackQueuePosition = position

            play()
        } else {
            logError("[Player] Cannot play track #${playbackQueuePosition} of ${playbackQueue.size}.")
        }
    }

    fun isNextTrackAvailable() = playbackQueuePosition < playbackQueue.size - 1 || repeat != REPEAT_OFF

    fun getNextTrack() {
        if (repeat == REPEAT_ONE) {
            queuedTrackId = playingTrackId
            return
        } else if (playbackQueuePosition < playbackQueue.size - 1) {
            playbackQueuePosition += 1
        } else if (repeat == REPEAT_ALL) {
            playbackQueuePosition = 0
        } else {
            return
        }

        queuedTrackId = getTrackIdAt(playbackQueuePosition)

        logInfo("[Player] Loading track ${playbackQueuePosition} of ${playbackQueue.size}.")
        backendView?.skipToNext()
    }

    fun skipToNext() {
        if (playbackQueuePosition < playbackQueue.size - 1) {
            playbackQueuePosition += 1
        } else if (repeat == REPEAT_ALL) {
            playbackQueuePosition = 0
        } else {
            return
        }

        queuedTrackId = getTrackIdAt(playbackQueuePosition)

        logInfo("[Player] Loading track ${playbackQueuePosition} of ${playbackQueue.size}.")
        backendView?.skipToNext()

        if (state != PlaybackState.STATE_PLAYING) {
            play()
        }
    }

    fun skipToPrev() {
        if (playbackTimePosition > 3000) {
            seek(0)
        } else {
            if (playbackQueuePosition > 0) {
                playbackQueuePosition -= 1

                queuedTrackId = getTrackIdAt(playbackQueuePosition)

                logInfo("[Player] Loading track ${playbackQueuePosition} of ${playbackQueue.size}.")
                backendView?.skipToPrev()

                if (state != PlaybackState.STATE_PLAYING) {
                    play()
                }
            } else {
                seek(0)
            }
        }
    }

    fun pause() {
        if (state != PlaybackState.STATE_PLAYING) {
            logError("[Player] Received pause command, but not currently PLAYING.")
            return
        }

        logVerbose("[Player] Pausing track: ${playingTrackId}")

        pausedTrackId = playingTrackId

        state = PlaybackState.STATE_PAUSED

        audioTrack?.pause()
        backendView?.pause()

        logStats()
        stats.clear()
    }

    fun stop() {
        if (state == PlaybackState.STATE_STOPPED) {
            logError("[Player] Received stop command, but already STOPPED.")
            return
        }

        logVerbose("[Player] Stopping track: ${playingTrackId}")

        state = PlaybackState.STATE_STOPPED

        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null

        pausedTrackId = playingTrackId

        teardown()

        audioManager.abandonAudioFocus(this)

        backendView?.stop()
    }

    fun seek(progress: Int) {
        val realm = Realm.getDefaultInstance()
        val track = realm.findFirstSync(Track::class.java, playingTrackId ?: return)
        val length = track?.trackLength ?: 0
        val seekPosition = (length * progress / 100).toInt()
        queuedSeekPosition = seekPosition
    }

    fun onTrackMoved(originPos: Int, destPos: Int) {
        if (originPos == playbackQueuePosition) {
            playbackQueuePosition = destPos
        } else if (destPos == playbackQueuePosition) {
            playbackQueuePosition = originPos
        }
    }

    fun onTrackRemoved(position: Int) {
        if (position == playbackQueuePosition) {
            play(position)
        } else if (position < playbackQueuePosition) {
            playbackQueuePosition = playbackQueuePosition - 1
        }

    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                logVerbose("[Player] Focus lost. Pausing...")
                pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                logVerbose("[Player] Focus lost temporarily. Pausing...")

                if (state == PlaybackState.STATE_PLAYING) {
                    focusLossPaused = true
                }

                pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                logVerbose("[Player] Focus lost temporarily, but can duck. Lowering volume...")
                ducking = true
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                logVerbose("[Player] Focus gained. Resuming...")

                ducking = false

                if (focusLossPaused) {
                    play()
                    focusLossPaused = false
                }
            }
        }
    }

    private fun getTrackIdAt(position: Int): String? {
        val actualPosition = if (shuffle && !playingShuffledTrack) {
            shuffledPositionQueue?.get(0) ?: 0
        } else if (shuffle) {
            shuffledPositionQueue?.get(position) ?: 0
        } else {
            position
        }

        actualPlaybackQueuePosition = actualPosition
        return playbackQueue.get(actualPosition)
    }

    private fun generateRandomPositionArray() {
        // Creates an array where position 1's value is 1, 267's value is 267, etc
        val possiblePositions = Array(playbackQueue.size) { position -> position }.toMutableList()
        Collections.shuffle(possiblePositions)

        shuffledPositionQueue = possiblePositions
    }

    private fun requestAudioFocus(): Int {
        return audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
    }

    private fun initializeAudioTrack(): Boolean {
        logVerbose("[Player] Initializing audio track.\n" +
                "[Player] Sample Rate: ${audioConfig.sampleRate}Hz\n" +
                "[Player] Buffer size: ${audioConfig.bufferSizeBytes} bytes\n" +
                "[Player] Buffer length: ${audioConfig.minimumLatency * READ_AHEAD_BUFFER_SIZE} msec")

        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC,
                audioConfig.sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioConfig.bufferSizeBytes,
                AudioTrack.MODE_STREAM)

        if (audioTrack == null) {
            logError("[Player] Failed to initialize AudioTrack.")
            return false
        }

        // Get updates on playback position every second (one frame is equal to one sample).
        audioTrack?.setPositionNotificationPeriod(audioConfig.sampleRate)

        // Set a listener to update the UI's playback position.
        audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(track: AudioTrack) {
                val millisPlayed = getMillisPlayed()

                playbackTimePosition = millisPlayed
                updater.send(PositionEvent(millisPlayed))
            }

            override fun onMarkerReached(track: AudioTrack) { }
        })

        return true
    }

    private fun logProblems(bytesWritten: Int) {
        if (bytesWritten == audioConfig.bufferSizeShorts)
            return

        val error = when (bytesWritten) {
            AudioTrack.ERROR_INVALID_OPERATION -> "Invalid AudioTrack operation."
            AudioTrack.ERROR_BAD_VALUE -> "Invalid AudioTrack value."
            AudioTrack.ERROR -> "Unknown AudioTrack error."
            ERROR_AUDIO_TRACK_NULL -> "No audio track found."
            else -> "Wrote fewer bytes than expected: ${bytesWritten}"
        }

        logError("[Player] $error")
    }

    private fun logStats() {
        logInfo("[Player] Underruns since playback started: ${stats.underrunCount}")
    }

    companion object {
        val ERROR_AUDIO_TRACK_NULL = -100

        val READ_AHEAD_BUFFER_SIZE = 2

        val REPEAT_OFF = 0
        val REPEAT_ALL = 1
        val REPEAT_ONE = 2
        val REPEAT_INFINITE = 3
    }
}