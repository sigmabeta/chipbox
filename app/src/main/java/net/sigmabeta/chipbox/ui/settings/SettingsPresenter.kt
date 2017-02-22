package net.sigmabeta.chipbox.ui.settings

import android.os.Bundle
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Settings
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPresenter @Inject constructor(val player: Player,
                                            val updater: UiUpdater,
                                            val settings: Settings) : ActivityPresenter<SettingsView>() {
    var voices: MutableList<Voice>? = null

    var tempo = 100

    /**
     * Public Methods
     */

    fun onItemClick(position: Int) {
        voices?.let {
            val newValue = !it[position].enabled
            it[position].enabled = newValue

            player.backend?.muteVoice(position, if (newValue) 0 else 1)
            view?.notifyChanged(position)
        }
    }

    fun onTempoChange(position: Int) {
        val newValue = indexToTempoValue(position)
        settings.tempo = newValue
        player.backend?.setTempo(newValue / 100.0)
    }

    /**
     * ActivityPresenter
     */

    override fun onReenter() = Unit

    override fun onReCreate(arguments: Bundle?, savedInstanceState: Bundle) = Unit

    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
        needsSetup = false
    }

    override fun teardown() {
        voices = null
        tempo = 100
    }

    override fun updateViewState() {
        updateHelper()

        val subscription = updater.asObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when (it) {
                        is TrackEvent -> updateHelper()
                        is PositionEvent -> { /* no-op */
                        }
                        is StateEvent -> { /* no-op */
                        }
                        is GameEvent -> { /* no-op */
                        }
                        else -> logWarning("[PlayerFragmentPresenter] Unhandled ${it}")
                    }
                }

        subscriptions.add(subscription)
    }

    override fun onClick(id: Int) = Unit

    /**
     * Private Methods
     */

    private fun updateHelper() {
        tempo = settings.tempo ?: 100
        voices = if (settings.voices == null) {
            settings.voices = player.backend?.getVoices()
            settings.voices
        } else {
            settings.voices
        }

        view?.setVoices(voices)
        view?.setDropdownValue(tempoValueToIndex(tempo))
    }

    private fun tempoValueToIndex(tempo: Int) = (tempo - 50) / 10

    private fun indexToTempoValue(index: Int) = (index * 10) + 50
}

