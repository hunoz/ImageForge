description = "Models for the User API"

plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-jar").version("1.3.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    val smithyVersion: String by project
    val smithyJavaVersion: String by project

    // === Smithy / Model dependencies ===
    api("software.amazon.smithy:smithy-model:$smithyVersion")
    api("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    api("software.amazon.smithy:smithy-validation-model:$smithyVersion")
    api("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    implementation(project(":shared-models"))
}

tasks {
    compileJava {
        dependsOn(smithyBuild)
    }
}

java.sourceSets["main"].java {
    srcDirs("model", "src/main/smithy")
}
