package com.inayatechlab.filemanagerpro.vault

/**
 * Holds the single unlocked vault session of the app. Only one vault is
 * unlocked at a time; opening another (or calling [close]) wipes the previous
 * one from memory.
 */
object VaultSessionManager {

    var session: VaultEngine.Session? = null
        private set

    fun open(newSession: VaultEngine.Session) {
        close()
        session = newSession
    }

    /** Wipes key material + entry index of the active session. */
    fun close() {
        session?.close()
        session = null
    }

    val isUnlocked: Boolean get() = session != null
}
