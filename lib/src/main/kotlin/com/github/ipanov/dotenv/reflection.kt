package com.github.ipanov.dotenv

import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

@Target(AnnotationTarget.PROPERTY)
annotation class EnvVal(val name: String = "")

inline fun <reified T : Any> Path.loadEnvAs(): T = loadEnvAs(T::class)

@Suppress("UNCHECKED_CAST")
fun <T : Any> Path.loadEnvAs(kClass: KClass<T>): T {
    if (notExists()) throw IllegalArgumentException("File not found: $this")

    val neededKeys = mutableSetOf<String>()
    val foundValues = mutableMapOf<String, String>()

    // ЕДИНАЯ функция формирования ключа и префикса
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

    // 1. Сбор нужных ключей
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

    // 2. Ленивое чтение
    iterateEnvPairs().forEach { (k, v) ->
        if (k in neededKeys && v.isNotEmpty()) {
            foundValues[k] = v
        }
    }

    // 3. Создание объектов
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
            // иначе — дефолт или ошибка от Kotlin
        }

        return ctor.callBy(args)
    }

    collectKeys(kClass, "")
    return build(kClass, "") as T
}

private fun String.toType(type: KType): Any {
    val classifier = type.withNullability(false).classifier!!
    return when (classifier) {
        String::class -> this
        Int::class -> toInt()
        Long::class -> toLong()
        Double::class -> toDouble()
        Float::class -> toFloat()
        Boolean::class -> toBooleanStrict()
        else -> throw IllegalArgumentException("Unsupported type: $classifier")
    }
}
