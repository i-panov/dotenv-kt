package com.github.ipanov.dotenv

import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

fun InputStream.iterateLines(): Sequence<String> = sequence {
    bufferedReader().use { reader ->
        yieldAll(reader.lineSequence())
    }
}

fun Path.iterateLines(): Sequence<String> = sequence {
    if (notExists()) throw IllegalArgumentException("File not found: $this")
    if (!isRegularFile()) throw IllegalArgumentException("Not a file: $this")
    if (!isReadable()) throw IllegalArgumentException("File is not readable: $this")
    if (fileSize() == 0L) throw IllegalArgumentException("File is empty: $this")

    inputStream().use {
        yieldAll(it.iterateLines())
    }
}

/**
 * Валидация ключа: должен начинаться с буквы или подчёркивания,
 * дальше могут быть буквы, цифры и подчёркивания
 */
private fun isValidEnvKey(key: String): Boolean {
    if (key.isEmpty()) return false
    if (!key[0].isLetter() && key[0] != '_') return false
    return key.all { it.isLetterOrDigit() || it == '_' }
}

/**
 * Обработка экранированных символов внутри кавычек
 */
private fun processEscapes(value: String): String {
    val result = StringBuilder()
    var i = 0
    while (i < value.length) {
        if (value[i] == '\\' && i + 1 < value.length) {
            when (value[i + 1]) {
                'n' -> result.append('\n')
                't' -> result.append('\t')
                'r' -> result.append('\r')
                '\\' -> result.append('\\')
                '"' -> result.append('"')
                '\'' -> result.append('\'')
                else -> {
                    // Неизвестная escape-последовательность - оставляем как есть
                    result.append(value[i])
                    result.append(value[i + 1])
                }
            }
            i += 2
        } else {
            result.append(value[i])
            i++
        }
    }
    return result.toString()
}

// Состояния парсера
private enum class State { KEY, AFTER_KEY, VALUE_START, VALUE_UNQUOTED, VALUE_QUOTED, ESCAPED }

/**
 * Парсинг одной строки .env файла
 * Возвращает null если строка пустая, комментарий или невалидная
 */
private fun parseEnvLine(line: String, onWarning: (String) -> Unit = {}): Pair<String, String>? {
    val trimmed = line.trim()

    // Пустая строка или комментарий
    if (trimmed.isEmpty() || trimmed.startsWith('#')) {
        return null
    }

    var state = State.KEY
    val key = StringBuilder()
    val value = StringBuilder()
    var quoteChar: Char? = null
    var quotedAndClosed = false
    var i = 0

    mainLoop@ while (i < trimmed.length) {
        val ch = trimmed[i]

        when (state) {
            State.KEY -> {
                when {
                    ch == '=' -> state = State.VALUE_START
                    ch.isWhitespace() -> state = State.AFTER_KEY
                    else -> key.append(ch)
                }
            }

            State.AFTER_KEY -> {
                when {
                    ch == '=' -> state = State.VALUE_START
                    ch.isWhitespace() -> {} // пропускаем пробелы
                    else -> {
                        onWarning.invoke("Invalid character '$ch' after key in line: $line")
                        return null
                    }
                }
            }

            State.VALUE_START -> {
                when {
                    ch.isWhitespace() -> {} // пропускаем пробелы перед значением
                    ch == '"' || ch == '\'' -> {
                        quoteChar = ch
                        state = State.VALUE_QUOTED
                    }
                    ch == '#' -> break@mainLoop // комментарий сразу после =
                    else -> {
                        value.append(ch)
                        state = State.VALUE_UNQUOTED
                    }
                }
            }

            State.VALUE_UNQUOTED -> {
                when {
                    ch == '#' -> break@mainLoop // комментарий после значения
                    else -> value.append(ch)
                }
            }

            State.VALUE_QUOTED -> {
                when (ch) {
                    '\\' -> state = State.ESCAPED
                    quoteChar -> {
                        // Закрывающая кавычка - конец значения
                        quotedAndClosed = true
                        state = State.VALUE_UNQUOTED
                        // Всё что после неё до # или конца строки игнорируем
                        var j = i + 1
                        while (j < trimmed.length && trimmed[j].isWhitespace()) j++
                        if (j < trimmed.length && trimmed[j] != '#') {
                            onWarning.invoke("Unexpected characters after closing quote in line: $line")
                        }
                        break@mainLoop
                    }
                    else -> value.append(ch)
                }
            }

            State.ESCAPED -> {
                value.append('\\')
                value.append(ch)
                state = State.VALUE_QUOTED
            }
        }

        i++
    }

    // Проверяем что кавычки закрыты
    if (state == State.VALUE_QUOTED || state == State.ESCAPED) {
        onWarning.invoke("Unclosed quote in line: $line")
        return null
    }

    val keyStr = key.toString().trim()

    // Валидация ключа
    if (!isValidEnvKey(keyStr)) {
        onWarning.invoke("Invalid key '$keyStr' in line: $line")
        return null
    }

    // Обработка значения
    val valueStr = if (quotedAndClosed) {
        // Внутри кавычек обрабатываем escape-последовательности
        processEscapes(value.toString())
    } else {
        // Без кавычек просто trim справа (слева уже не добавляли пробелы)
        value.toString().trimEnd()
    }

    return keyStr to valueStr
}

fun Path.iterateEnvPairs(onWarning: (String) -> Unit = {}): Sequence<Pair<String, String>> {
    return iterateLines().mapNotNull { line ->
        parseEnvLine(line, onWarning)
    }
}

fun Path.loadEnvMap(onWarning: (String) -> Unit = {}): Map<String, String> {
    return iterateEnvPairs(onWarning).toMap()
}

fun getCurrentDir(): Path = Paths.get("").toAbsolutePath()
