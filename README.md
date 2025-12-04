[![](https://jitpack.io/v/i-panov/dotenv-kt.svg)](https://jitpack.io/#i-panov/dotenv-kt)

# dotenv-kt

Простая и легковесная библиотека на Kotlin для загрузки переменных окружения из `.env`-файлов с поддержкой автоматического сопоставления с объектами через рефлексию.

## Возможности

- Парсинг `.env`-файлов в `Map<String, String>`.
- Преобразование переменных окружения в Kotlin-объекты (data class) с поддержкой вложенных структур.
- Поддержка типов: `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`.
- Кастомные имена переменных через аннотацию `@EnvVal`.
- Игнорирование комментариев и пустых строк.

## Установка

### JitPack

1. Добавьте JitPack в список репозиториев в `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```

2. Добавьте зависимость в `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.i-panov:dotenv-kt:1.0.0")
}
```

> Замените `1.0.0` на нужную версию (посмотреть можно на [JitPack](https://jitpack.io/#i-panov/dotenv-kt)).

## Использование

### Пример `.env`-файла

```env
DB_HOST=localhost
DB_PORT=5432
DB_USERNAME=admin
DB_PASSWORD=secret
DB_DATABASE=myapp
DEBUG=true
```

### Определение конфигурации

```kotlin
data class DatabaseConfig(
    val host: String,
    val port: Int = 5432,
    val username: String,
    val password: String,
    val database: String
)

data class AppConfig(
    val db: DatabaseConfig,
    val debug: Boolean = false
)
```

### Загрузка конфигурации

```kotlin
import com.github.ipanov.dotenv.loadEnvAs
import java.nio.file.Path

val config = Path.of(".env").loadEnvAs<AppConfig>()
println(config)
```

### Кастомные имена переменных

```kotlin
@EnvVal("API_KEY")
val apiKey: String
```

## Лицензия

Этот проект распространяется под лицензией Apache 2.0. Подробнее — в файле [LICENSE](LICENSE).