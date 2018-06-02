package net.sigmabeta.chipbox.ui.main

import net.sigmabeta.chipbox.backend.UiUpdater
import net.sigmabeta.chipbox.backend.player.Player
import net.sigmabeta.chipbox.backend.player.Playlist
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.ui.ChromePresenter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainPresenter @Inject constructor(player: Player,
                                        scanner: LibraryScanner,
                                        playlist: Playlist,
                                        updater: UiUpdater) : ChromePresenter<MainView>(player, scanner, playlist, updater)
