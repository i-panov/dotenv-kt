package com.github.ipanov.dotenv

import java.math.*
import java.net.URI
import java.nio.file.Path
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

@Target(AnnotationTarget.PROPERTY)
annotation class EnvVal(val name: String = "")

inline fun <reified T : Any> Path.loadEnvAs(crossinline onWarning: (String) -> Unit = {}): T = loadEnvAs(T::class) {
    onWarning(it)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Path.loadEnvAs(kClass: KClass<T>, onWarning: (String) -> Unit = {}): T {
    val neededKeys = mutableSetOf<String>()
    val foundValues = mutableMapOf<String, String>()

    data class KeyInfo(val key: String, val nestedPrefix: String)

    fun keyInfo(param: KParameter, cls: KClass<*>, currentPrefix: String): KeyInfo {
        // Защита от NPE: имя параметра может быть null для параметров из Java-кода
        val paramName = param.name ?: throw IllegalArgumentException("Parameter name is null, cannot process. Check if the class is a pure Kotlin data class.")

        val ann = param.findAnnotation<EnvVal>()
            ?: cls.memberProperties.find { it.name == paramName }?.findAnnotation<EnvVal>()
        val custom = ann?.name

        return when {
            custom != null && custom.isNotEmpty() -> KeyInfo(custom, custom)
            custom != null -> {
                val base = paramName.uppercase()
                KeyInfo(base, currentPrefix)
            }
            currentPrefix.isEmpty() -> {
                val base = paramName.uppercase()
                KeyInfo(base, base)
            }
            else -> {
                val base = paramName.uppercase()
                val fullKey = "${currentPrefix}_$base"
                KeyInfo(fullKey, fullKey)
            }
        }
    }

    fun collectKeys(cls: KClass<*>, prefix: String, visited: Set<KClass<*>> = emptySet()) {
        // Защита от циклов
        if (cls in visited) throw IllegalArgumentException("Cyclic dependency: $cls")

        // Защита от NPE: primaryConstructor может быть null (например, для object)
        val ctor = cls.primaryConstructor ?: throw IllegalArgumentException("Class ${cls.simpleName} must have a primary constructor to be used with loadEnvAs.")

        for (param in ctor.parameters) {
            val info = keyInfo(param, cls, prefix)
            if (param.type.jvmErasure.isData) {
                collectKeys(param.type.jvmErasure, info.nestedPrefix, visited + cls)
            } else {
                neededKeys.add(info.key)
            }
        }
    }

    fun build(cls: KClass<*>, prefix: String): Any {
        val ctor = cls.primaryConstructor!!
        val args = mutableMapOf<KParameter, Any?>()

        for (param in ctor.parameters) {
            val info = keyInfo(param, cls, prefix)
            val valueFromEnv = foundValues[info.key]

            when {
                // Случай 1: Вложенный data class
                param.type.jvmErasure.isData -> {
                    args[param] = build(param.type.jvmErasure, info.nestedPrefix)
                }
                // Случай 2: Ключ найден в .env (даже если значение - пустая строка)
                valueFromEnv != null -> {
                    try {
                        args[param] = valueFromEnv.toType(param.type)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Failed to parse key '${info.key}' with value '${valueFromEnv}': ${e.message}", e)
                    }
                }
                // Случай 3: Ключ не найден, но параметр nullable
                param.type.isMarkedNullable -> {
                    args[param] = null
                }
                // Случай 4: Ключ не найден, параметр не nullable.
                // Ничего не делаем. callBy сам проверит наличие значения по умолчанию
                // или выбросит исключение, если его нет. Это желаемое поведение.
            }
        }

        return ctor.callBy(args)
    }

    // 1. Сначала собираем ключи
    collectKeys(kClass, "")

    // 2. Затем читаем файл
    iterateEnvPairs(onWarning).forEach { (k, v) ->
        if (k in neededKeys) {
            foundValues[k] = v
        }
    }

    // 3. Создаём объект
    return build(kClass, "") as T
}

private fun String.toType(type: KType): Any? {
    val classifier = type.withNullability(false).classifier!!

    if (classifier is KClass<*> && classifier.java.isEnum) {
        @Suppress("UNCHECKED_CAST")
        return java.lang.Enum.valueOf(classifier.java as Class<out Enum<*>>, this)
    }

    return when (classifier) {
        Char::class -> single()
        String::class -> this
        Byte::class -> toByte()
        Short::class -> toShort()
        Int::class -> toInt()
        Long::class -> toLong()
        Double::class -> toDouble()
        Float::class -> toFloat()
        Boolean::class -> toBooleanSoft() ?: throw IllegalArgumentException("Unsupported boolean value: $this")
        BigDecimal::class -> toBigDecimal()
        BigInteger::class -> toBigInteger()
        Path::class -> Path.of(this)
        kotlin.time.Duration::class -> kotlin.time.Duration.parse(this)
        java.time.Duration::class -> java.time.Duration.parse(this)
        URI::class -> URI.create(this)
        else -> throw IllegalArgumentException("Unsupported type: $classifier")
    }
}

fun String.toBooleanSoft(): Boolean? {
    return when (this.lowercase()) {
        in TRUE_VALUES -> true
        in FALSE_VALUES -> false
        else -> null
    }
}

private val TRUE_VALUES = setOf("true", "1", "yes", "y", "on")
private val FALSE_VALUES = setOf("false", "0", "no", "n", "off")
