plugins {
    alias(libs.plugins.kotlin.jvm)

    `java-library`

    `maven-publish`
}

group = "com.github.i-panov"
version = "1.0.0-SNAPSHOT"
description = "A simple and lightweight Kotlin library for working with .env files with automatic object mapping."


repositories {
    mavenCentral()
}

dependencies {
    // Эта зависимость используется внутри вашей библиотеки для рефлексии.
    // Потребители вашей библиотеки тоже должны будут ее добавить.
    // Поэтому `api` — более честный выбор, чем `implementation`.
    // Если вы хотите скрыть эту зависимость, используйте `implementation`.
    api(libs.kotlin.reflect)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("Kotlin Dotenv")
                description.set(project.description)
                url.set("https://github.com/i-panov/dotenv-kt")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("i-panov")
                        name.set("Your Name")
                        email.set("your.email@example.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/i-panov/dotenv-kt.git")
                    developerConnection.set("scm:git:ssh://github.com:i-panov/dotenv-kt.git")
                    url.set("https://github.com/i-panov/dotenv-kt/tree/main")
                }
            }
        }
    }

    repositories {
        maven {
            name = "localRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
