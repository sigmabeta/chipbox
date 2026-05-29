package net.sigmabeta.chipbox.server

import java.io.File

/**
 * Server runtime configuration sourced from environment variables. `CHIPBOX_LIBRARY_DIR` is the
 * only required value; everything else has a sensible default that works for `./gradlew :apps:server:run`
 * during development.
 */
data class ServerConfig(
    val libraryDir: File,
    val workDir: File,
    val dbPath: File,
    val port: Int,
    val bindHost: String,
    val corsAllowedOrigins: List<String>,
) {
    companion object {
        const val ENV_LIBRARY_DIR = "CHIPBOX_LIBRARY_DIR"
        const val ENV_WORK_DIR = "CHIPBOX_WORK_DIR"
        const val ENV_DB_PATH = "CHIPBOX_DB_PATH"
        const val ENV_PORT = "CHIPBOX_PORT"
        const val ENV_BIND_HOST = "CHIPBOX_BIND_HOST"
        const val ENV_CORS = "CHIPBOX_CORS_ALLOWED_ORIGINS"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_BIND_HOST = "0.0.0.0"
        const val DEFAULT_WORK_SUBDIR = ".chipbox-server"
        const val DEFAULT_DB_FILE = "library.sqlite"

        /**
         * Reads config from `System.getenv()`. Fails fast (exit code 64) with a one-line message if
         * `CHIPBOX_LIBRARY_DIR` is missing or doesn't point at a real directory — this is the only
         * value the operator has to set for the server to do anything useful.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val libraryDirRaw = env[ENV_LIBRARY_DIR]?.takeIf { it.isNotBlank() }
                ?: failFast("$ENV_LIBRARY_DIR is required (absolute path to the music library to scan).")
            val libraryDir = File(libraryDirRaw)
            if (!libraryDir.isDirectory) {
                failFast("$ENV_LIBRARY_DIR=$libraryDirRaw is not an existing directory.")
            }

            val workDirRaw = env[ENV_WORK_DIR]?.takeIf { it.isNotBlank() }
            val workDir = workDirRaw?.let(::File)
                ?: File(System.getProperty("user.dir"), DEFAULT_WORK_SUBDIR)
            workDir.mkdirs()

            val dbPath = env[ENV_DB_PATH]?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(workDir, DEFAULT_DB_FILE)

            val port = env[ENV_PORT]?.toIntOrNull() ?: DEFAULT_PORT
            val bindHost = env[ENV_BIND_HOST]?.takeIf { it.isNotBlank() } ?: DEFAULT_BIND_HOST

            val corsAllowedOrigins = env[ENV_CORS]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            return ServerConfig(
                libraryDir = libraryDir,
                workDir = workDir,
                dbPath = dbPath,
                port = port,
                bindHost = bindHost,
                corsAllowedOrigins = corsAllowedOrigins,
            )
        }

        private fun failFast(message: String): Nothing {
            System.err.println("chipbox-server: $message")
            System.err.println(
                "Required env: $ENV_LIBRARY_DIR. " +
                    "Optional: $ENV_PORT (default $DEFAULT_PORT), $ENV_BIND_HOST (default $DEFAULT_BIND_HOST), " +
                    "$ENV_WORK_DIR, $ENV_DB_PATH, $ENV_CORS.",
            )
            kotlin.system.exitProcess(EX_USAGE)
        }

        private const val EX_USAGE = 64
    }
}
