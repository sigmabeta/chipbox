package net.sigmabeta.chipbox.ui.settings

import android.os.Bundle
import net.sigmabeta.chipbox.backend.Player
import net.sigmabeta.chipbox.model.audio.Voice
import net.sigmabeta.chipbox.model.events.GameEvent
import net.sigmabeta.chipbox.model.events.PositionEvent
import net.sigmabeta.chipbox.model.events.StateEvent
import net.sigmabeta.chipbox.model.events.TrackEvent
import net.sigmabeta.chipbox.ui.ActivityPresenter
import net.sigmabeta.chipbox.ui.BaseView
import net.sigmabeta.chipbox.util.logWarning
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPresenter @Inject constructor(val player: Player) : ActivityPresenter() {
    var view: SettingsView? = null

    var voices: MutableList<Voice>? = null

    var tempo = 100

    /**
     * Public Methods
     */

    fun onItemClick(position: Long) {
        voices?.let {
            val newValue = !it[position.toInt()].enabled
            it[position.toInt()].enabled = newValue
            view?.notifyChanged(position.toInt())
        }
    }

    fun onTempoChange(position: Int) {
        player.tempo = indexToTempoValue(position)
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

    override fun setup(arguments: Bundle?) = Unit

    override fun teardown() {
        voices = null
        tempo = 100
    }

    override fun updateViewState() {
        updateHelper()

        val subscription = player.updater.asObservable()
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

    override fun getView(): BaseView? = view

    override fun setView(view: BaseView) {
        if (view is SettingsView) this.view = view
    }

    override fun clearView() {
        view = null
    }

    /**
     * Private Methods
     */

    private fun updateHelper() {
        tempo = player.tempo ?: 100
        voices = player.voices

        view?.setVoices(voices)
        view?.setDropdownValue(tempoValueToIndex(tempo))
    }

    private fun tempoValueToIndex(tempo: Int) = (tempo - 50) / 10

    private fun indexToTempoValue(index: Int) = (index * 10) + 50
}

