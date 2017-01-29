package net.sigmabeta.chipbox.backend


import net.sigmabeta.chipbox.model.events.PlaybackEvent
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiUpdater @Inject constructor() {
    private val subject = SerializedSubject(PublishSubject.create<PlaybackEvent>())

    fun send(event: PlaybackEvent) {
        subject.onNext(event)
    }

    fun asObservable(): Observable<PlaybackEvent> {
        return subject
    }

    fun hasObservers(): Boolean {
        return subject.hasObservers()
    }
}