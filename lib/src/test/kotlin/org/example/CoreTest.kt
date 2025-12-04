package org.example

import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    private val currentDir = Paths.get("").toAbsolutePath()
    private val testEnv = currentDir.resolve("src/test/resources/test.env")
    private val appEnv = currentDir.resolve("src/test/resources/app.env")

    @Test
    fun pathExistsTest() {
        assertTrue { testEnv.exists() }
    }

    @Test
    fun iterateLinesTest() {
        assertEquals(11, testEnv.iterateLines().toList().count())
    }

    @Test
    fun iterateEnvPairsTest() {
        assertEquals(5, testEnv.iterateEnvPairs().toList().count())

        assertEquals(mapOf(
            "test1" to "123",
            "test2" to "321",
            "test3" to "456",
            "test4" to "789",
            "test5" to "999=111",
        ), testEnv.loadEnvMap())
    }

    @Test
    fun loadConfigTest() {
        val cfg = appEnv.loadEnvAs<AppConfig>()
        println(cfg)
    }
}

data class DatabaseConfig(
    val host: String = "localhost",
    val port: Int = 3306,
    val username: String,
    val password: String,
    val database: String,
)

data class AppConfig(
    val db: DatabaseConfig,
    val debug: Boolean = false,
)
