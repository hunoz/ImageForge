import org.gradle.kotlin.dsl.provideDelegate

/**
 * Example combining client and server into a single end-to-end example.
 */
rootProject.name = "Dave"

pluginManagement {
    plugins {
        id("software.amazon.smithy.gradle.smithy-base").version("1.3.0")
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":shared-models")
include(":user-api")
include(":user-api-model")
