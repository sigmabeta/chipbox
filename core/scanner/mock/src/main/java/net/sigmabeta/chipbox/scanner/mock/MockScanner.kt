package net.sigmabeta.chipbox.scanner.mock

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import net.sigmabeta.chipbox.repository.mock.MockRepository
import net.sigmabeta.chipbox.scanner.GameFoundEvent
import net.sigmabeta.chipbox.scanner.Scanner

class MockScanner(
    private val mockRepository: MockRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Scanner {
    override fun scanEvents() = flow {
        val games = mockRepository.getAllGames()
        games.forEach {
            val event = GameFoundEvent(
                it.title,
                it.tracks?.size ?: 0,
                it.photoUrl ?: ""
            )

            emit(event)
        }
    }

}