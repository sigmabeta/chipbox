package net.sigmabeta.chipbox.backend


import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import net.sigmabeta.chipbox.model.events.PlaybackEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiUpdater @Inject constructor() {
    private val subject = PublishProcessor.create<PlaybackEvent>().toSerialized()

    fun send(event: PlaybackEvent) {
        subject.onNext(event)
    }

    fun asFlowable(): Flowable<PlaybackEvent> {
        return subject.onBackpressureDrop()
    }
}