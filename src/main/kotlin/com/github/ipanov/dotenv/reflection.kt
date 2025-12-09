package com.github.ipanov.dotenv

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
        val ann = param.findAnnotation<EnvVal>()
            ?: cls.memberProperties.find { it.name == param.name }?.findAnnotation<EnvVal>()
        val custom = ann?.name

        return when {
            custom != null && custom.isNotEmpty() -> KeyInfo(custom, custom)
            custom != null -> {
                val base = param.name!!.uppercase()
                KeyInfo(base, currentPrefix)
            }
            currentPrefix.isEmpty() -> {
                val base = param.name!!.uppercase()
                KeyInfo(base, base)
            }
            else -> {
                val base = param.name!!.uppercase()
                val fullKey = "${currentPrefix}_$base"
                KeyInfo(fullKey, fullKey)
            }
        }
    }

    fun collectKeys(cls: KClass<*>, prefix: String) {
        val ctor = cls.primaryConstructor ?: return
        for (param in ctor.parameters) {
            val info = keyInfo(param, cls, prefix)
            if (param.type.jvmErasure.isData) {
                collectKeys(param.type.jvmErasure, info.nestedPrefix)
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

            if (param.type.jvmErasure.isData) {
                args[param] = build(param.type.jvmErasure, info.nestedPrefix)
            } else if (info.key in foundValues) {
                args[param] = foundValues[info.key]!!.toType(param.type)
            }
        }

        return ctor.callBy(args)
    }

    // 1. Сначала собираем ключи
    collectKeys(kClass, "")

    // 2. Затем читаем файл (теперь neededKeys заполнен)
    iterateEnvPairs(onWarning).forEach { (k, v) ->
        if (k in neededKeys && v.isNotEmpty()) {
            foundValues[k] = v
        }
    }

    // 3. Создаём объект
    return build(kClass, "") as T
}

private fun String.toType(type: KType): Any {
    val classifier = type.withNullability(false).classifier!!
    return when (classifier) {
        Char::class -> single()
        String::class -> this
        Int::class -> toInt()
        Long::class -> toLong()
        Double::class -> toDouble()
        Float::class -> toFloat()
        Boolean::class -> toBooleanSoft() ?: throw IllegalArgumentException("Unsupported boolean value: $this")
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
