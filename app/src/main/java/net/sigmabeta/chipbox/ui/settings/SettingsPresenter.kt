package net.sigmabeta.chipbox.ui.settings

import android.os.Bundle
import io.reactivex.android.schedulers.AndroidSchedulers
import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Settings
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.UiState
import timber.log.Timber
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

            player.muteVoice(position, newValue)
            view?.notifyChanged(position)
        }
    }

    fun onTempoChange(position: Int) {
        val newValue = indexToTempoValue(position)
        player.setTempo(newValue)
    }

    /**
     * ActivityPresenter
     */

    override fun onReenter() = Unit



    override fun onTempDestroy() = Unit

    /**
     * BasePresenter
     */

    override fun setup(arguments: Bundle?) {
        state = UiState.READY
    }

    override fun teardown() {
        voices = null
        tempo = 100
    }

    override fun showReadyState() {
        updateHelper()

        val subscription = updater.asFlowable()
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
                        else -> Timber.w("Unhandled %s", it.toString())
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

        voices = settings.voices
        voices?.let {
            view?.setList(it)
        } ?: let {
            state = UiState.EMPTY
        }

        view?.setDropdownValue(tempoValueToIndex(tempo))
    }

    private fun tempoValueToIndex(tempo: Int) = (tempo - 50) / 10

    private fun indexToTempoValue(index: Int) = (index * 10) + 50
}

