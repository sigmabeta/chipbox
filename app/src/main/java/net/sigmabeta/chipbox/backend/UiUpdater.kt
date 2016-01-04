package net.sigmabeta.chipbox.backend


import net.sigmabeta.chipbox.model.events.PlaybackEvent
import rx.Observable
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject

class UiUpdater {
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