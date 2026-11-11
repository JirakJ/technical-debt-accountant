plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.detekt)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        pluginVerifier()
        zipSigner()
    }

    implementation(libs.gson)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly("junit:junit:4.13.2")
}

// Exclude IDE-provided libraries from plugin distribution
configurations {
    runtimeClasspath {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        vendor {
            name = "Jakub Jirák"
            url = "https://github.com/JirakJ/technical-debt-accountant"
        }
    }

    pluginVerification {
        ides {
            ide("IC", "2025.1.7", false)
            ide("IC", "2025.2.6.1", false)
            ide("IC", "2025.3.4", false)
            ide("IC", "2026.1", false)
        }
    }

    signing { }
    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

tasks {
    publishPlugin {
        token.set(System.getenv("PLUGIN_TOKEN"))
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "8.13"
    }
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}
