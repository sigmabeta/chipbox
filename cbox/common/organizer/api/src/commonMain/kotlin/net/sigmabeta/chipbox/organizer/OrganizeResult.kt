package net.sigmabeta.chipbox.organizer

/** Top-level destination bucket for games whose tracks are split across multiple folders. */
const val INVALID_CATEGORY: String = "Invalid Folders"

/** How many folders an organize run moved versus failed to move. */
data class OrganizeResult(val movedFolders: Int, val failedFolders: Int)
