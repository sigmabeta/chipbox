package net.sigmabeta.chipbox.backend

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.PlaybackState
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.model.objects.AudioBuffer
import net.sigmabeta.chipbox.model.objects.AudioConfig
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.util.external.*
import net.sigmabeta.chipbox.util.logDebug
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import net.sigmabeta.chipbox.view.interfaces.BackendView
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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

    var position = 0L

    var playbackQueue: ArrayList<Track>? = null
    var playbackQueuePosition: Int? = null

    var playingTrack: Track? = null
        set (value) {

            teardown()

            if (value != null) {
                loadTrackNative(value,
                        audioConfig.sampleRate,
                        audioConfig.bufferSizeShorts.toLong())

                updater.send(TrackEvent(value))
            }

            if (state != PlaybackState.STATE_PLAYING) {
                audioTrack?.flush()
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
            if (isTrackOver()) {
                logVerbose("[Player] Track has ended.")

                if (!nextTrackAvailable()) {
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
            synchronized(playingTrack ?: break) {
                readNextSamples(audioBuffer.buffer)
            }

            val error = getLastError()

            if (error == null) {
                fullBuffers.put(audioBuffer)
            } else {
                logError("[Player] GME Error: ${error}")
                stop()
                break
            }
        }

        logVerbose("[Player] Clearing empty buffer queue...")

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

        val timeout = audioConfig.minimumLatency.toLong() * READ_AHEAD_BUFFER_SIZE

        while (state == PlaybackState.STATE_PLAYING) {
            var audioBuffer = fullBuffers.poll()

            if (audioBuffer == null) {
                logError("[Player] Buffer underrun.")
                stats.underrunCount += 1

                audioBuffer = fullBuffers.poll(timeout, TimeUnit.MILLISECONDS)

                if (audioBuffer == null) {
                    logError("[Player] Couldn't get a full buffer after ${timeout}ms; stopping...")
                    stop()
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
            logError("[Player] Received play command, but already PLAYING a track: ${playingTrack?.path}")
            return
        }

        val focusResult = requestAudioFocus()
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            val localTrack = playingTrack

            if (localTrack != null) {
                logVerbose("[Player] Playing track: ${localTrack.title}")
                state = PlaybackState.STATE_PLAYING

                if (backendView == null) {
                    PlayerService.start(context)
                } else {
                    backendView?.play()
                }

                // Start a thread for the playback loop.
                Thread { writerLoop() }.start()
                Thread { readerLoop() }.start()
            } else {
                logError("[Player] Received play command, but no Track selected.")
            }
        } else {
            logError("[Player] Unable to gain audio focus.")
        }
    }

    fun play(track: Track) {
        logVerbose("[Player] Playing new track: ${track.title}")

        playingTrack = track

        playbackQueue = null
        playbackQueuePosition = null

        play()
    }

    fun play(playbackQueue: ArrayList<Track>, position: Int) {
        if (position < playbackQueue.size) {
            logVerbose("[Player] Playing new playlist, starting from track ${position} of ${playbackQueue.size}.")

            this.playbackQueue = playbackQueue
            playbackQueuePosition = position

            playingTrack = playbackQueue.get(position)

            play()
        } else {
            logError("[Player] Tried to play new playlist, but invalid track number: ${position} of ${playbackQueue.size}")
        }
    }

    fun play(position: Int) {
        val queue = playbackQueue
        if (queue != null) {
            if (position < queue.size) {
                playbackQueuePosition = position
                playingTrack = queue.get(position)

                play()
            } else {
                logError("[Player] Cannot play track #${position} of ${queue.size}.")
            }
        } else {
            logError("[Player] Cannot play track #${position}: no playback queue exists.")
        }
    }

    fun nextTrackAvailable(): Boolean {
        val queue = playbackQueue
        var position = playbackQueuePosition

        return (queue != null && position != null && position < queue.size - 1)
    }

    fun getNextTrack() {
        val queue = playbackQueue
        var position = playbackQueuePosition

        if (queue != null && position != null && position < queue.size - 1) {
            position += 1

            playingTrack = queue.get(position)
            playbackQueuePosition = position

            logInfo("[Player] Loading track ${position} of ${queue.size}.")
            backendView?.skipToNext()
        }
    }

    fun skipToNext() {
        val queue = playbackQueue
        var position = playbackQueuePosition

        if (queue != null && position != null && position < queue.size - 1) {
            position += 1

            playingTrack = queue.get(position)
            playbackQueuePosition = position

            logInfo("[Player] Loading track ${position} of ${queue.size}.")
            backendView?.skipToNext()
        }
    }

    fun skipToPrev() {
        val queue = playbackQueue
        var position = playbackQueuePosition

        if (queue != null && position != null && position >= 0) {
            position -= 1

            playingTrack = queue.get(position)
            playbackQueuePosition = position

            logInfo("[Player] Loading track ${position} of ${queue.size}.")
            backendView?.skipToPrev()
        }
    }

    fun pause() {
        if (state != PlaybackState.STATE_PLAYING) {
            logError("[Player] Received pause command, but not currently PLAYING.")
            return
        }

        logVerbose("[Player] Pausing track: ${playingTrack?.title}")

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

        logVerbose("[Player] Stopping track: ${playingTrack?.title}")

        state = PlaybackState.STATE_STOPPED

        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null

        playingTrack = null

        teardown()

        audioManager.abandonAudioFocus(this)

        backendView?.stop()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                logVerbose("[Player] Focus lost. Stopping...")
                stop()
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

    private fun requestAudioFocus(): Int {
        return audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
    }

    private fun initializeAudioTrack(): Boolean {
        logVerbose("[Player] Initializing audio track.\n" +
                "[Player] Sample Rate: ${audioConfig.sampleRate}\n" +
                "[Player] Buffer size: ${audioConfig.bufferSizeBytes} bytes.")

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
                updater.send(PositionEvent(getMillisPlayed()))
            }

            override fun onMarkerReached(track: AudioTrack) { }
        })

        return true
    }

    private fun logProblems(bytesWritten: Int) {
        if (bytesWritten == audioConfig.bufferSizeShorts)
            return

        var error = when (bytesWritten) {
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
    }
}