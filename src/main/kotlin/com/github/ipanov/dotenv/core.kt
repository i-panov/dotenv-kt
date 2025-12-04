package com.github.ipanov.dotenv

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.inputStream

fun Path.iterateLines(): Sequence<String> = sequence {
    inputStream().bufferedReader().use { reader ->
        yieldAll(reader.lineSequence())
    }
}

fun Path.iterateEnvPairs(): Sequence<Pair<String, String>> {
    return iterateLines().mapNotNull { line ->
        val trimmedLine = line.trim()

        if (trimmedLine.isEmpty()) return@mapNotNull null

        if (trimmedLine.startsWith('#')) return@mapNotNull null

        val separatorIndex = trimmedLine.indexOf('=')

        if (separatorIndex == -1) return@mapNotNull null

        val key = trimmedLine.substring(0, separatorIndex).trim()

        val value = trimmedLine.substring(separatorIndex + 1).trim().let {
            if (it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it
        }

        key to value
    }
}

fun Path.loadEnvMap(): Map<String, String> = iterateEnvPairs().toMap()

fun getCurrentDir(): Path = Paths.get("").toAbsolutePath()
