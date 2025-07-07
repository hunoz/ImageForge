import java.nio.file.Path

description = "User API for the Dave service"

plugins {
    application
    kotlin("jvm") version "2.1.21"
    kotlin("kapt") version "2.1.21"
    id("com.ncorti.ktfmt.gradle") version "0.23.0"
    jacoco
    // Executes smithy-build process to generate server stubs
    id("software.amazon.smithy.gradle.smithy-base")
}

ktfmt { kotlinLangStyle() }

dependencies {
    val smithyVersion: String by project
    val smithyJavaVersion: String by project

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")

    // === Smithy / Model dependencies ===
    implementation(project(":user-api-model"))

    // === Code generators ===
    smithyBuild("software.amazon.smithy.java:server-codegen:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:client-codegen:$smithyJavaVersion")

    // === Smithy Server dependencies ===
    // Adds an HTTP server implementation based on netty
    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    // Adds the server implementation of the `RestJson1` protocol
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-core:${smithyJavaVersion}")
    implementation("software.amazon.smithy.java:auth-api:${smithyJavaVersion}")
    implementation("software.amazon.smithy.java:server-rpcv2-cbor:${smithyJavaVersion}")
    implementation("software.amazon.smithy.java:aws-lambda-endpoint:${smithyJavaVersion}")
    implementation("software.amazon.smithy.java:server-api:${smithyJavaVersion}")
    implementation("software.amazon.smithy.java:http-binding:$smithyJavaVersion")

    // === Server Impl Dependencies ===
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.3")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("com.google.dagger:dagger:2.56.2")
    kapt("com.google.dagger:dagger-compiler:2.56.2")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.nimbusds:nimbus-jose-jwt:10.3")
    implementation("io.pebbletemplates:pebble:3.2.4")
    implementation(platform("software.amazon.awssdk:bom:2.31.63"))
    implementation("software.amazon.awssdk:aws-crt-client:2.31.63")
    implementation("jakarta.validation:jakarta.validation-api:3.1.1")
    implementation("org.hibernate.validator:hibernate-validator:9.0.1.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")
    implementation("software.amazon.awssdk:dynamodb-enhanced:2.31.63")
    implementation("software.amazon.awssdk:iam:2.31.63")
    implementation("software.amazon.awssdk:ec2:2.31.63")
    implementation("software.amazon.awssdk:ssm:2.31.63")
    implementation("software.amazon.awssdk:sts:2.31.63")
    // Used for SSO-based AWS authentication
    implementation("software.amazon.awssdk:sso:2.31.63")
    implementation("software.amazon.awssdk:ssooidc:2.31.63")

    // === Test dependencies ===
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.hamcrest:hamcrest:3.0")
}

// Add generated source code to the compilation sourceSet
val serverPath: Provider<Path> =
    smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")

sourceSets {
    main {
        java { srcDirs(serverPath, "model", "src/main/smithy") }
        kotlin { srcDirs(serverPath, "model", "src/main/smithy") }
    }
    create("it") {
        compileClasspath +=
            main.get().output +
                configurations["testRuntimeClasspath"] +
                configurations["testCompileClasspath"]
        runtimeClasspath +=
            output + compileClasspath + test.get().runtimeClasspath + test.get().output
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask>().configureEach {
        dependsOn(smithyBuild)
    }

    withType<org.jetbrains.kotlin.gradle.internal.KaptTask>().configureEach {
        dependsOn(smithyBuild)
    }

    compileJava { dependsOn(smithyBuild) }

    compileKotlin { dependsOn(smithyBuild) }

    val zip by
        registering(Zip::class) {
            dependsOn("kaptKotlin", compileJava)
            archiveFileName.set("user-api.zip")
            from(compileKotlin)
            from(processResources)
            from(fileTree("build/classes/java/main")) // Include the generated Dagger classes
            into("lib") {
                from(jar)
                from(configurations.runtimeClasspath) {
                    exclude("sso*") // Exclude the AWS SSO packages, those are only needed locally
                }
            }
        }

    build { dependsOn(zip, test, check) }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required = true
            html.required = true
        }

        classDirectories.setFrom(
            files(
                classDirectories.files.map {
                    fileTree(it) {
                        exclude(
                            "dev/popaxe/dave/generated",
                            "dev/popaxe/dave/userapi/dagger",
                            "dev/popaxe/dave/userapi/Server*", // Ignore, cannot test entrypoint
                            "**/*log*.class",
                        )
                    }
                }
            )
        )
    }

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        violationRules { rule { limit { minimum = "0.9".toBigDecimal() } } }
    }

    check { dependsOn(jacocoTestCoverageVerification, ktfmtCheck) }

    test {
        finalizedBy(jacocoTestReport)
        useJUnitPlatform()
    }

    application { mainClass = "dev.popaxe.dave.userapi.ServerKt" }

    named<JavaExec>("run") {
        environment("APP_CONFIG_PATH", "${rootProject.projectDir}/.config.json")
    }
}

kotlin {
    java {
        compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") }

        sourceCompatibility = JavaVersion.VERSION_17
    }

    jvmToolchain(17)
}
