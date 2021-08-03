import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

application {
    mainClass.set("orienteering.main.MainKt")
}

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm") version "1.5.21"

    // Apply the application plugin to add support for building a CLI application.
    application

    // Documentation plugin
    id("org.jetbrains.dokka") version "1.5.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components. We don't need to specify an explicit version for
    // "kotlin-bom" as the Kotlin gradle plugin takes care of it based on the Kotlin version
    // specified in the "plugins" section.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // --- Dependencies managed by BOM (start) ---
    // We don't need to specify versions for these dependencies, as they come from the "kotlin-bom"
    // dependency. Check a specific release of "kotlin-bom" in the following page to get libraries
    // with version numbers managed by the BOM:
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-bom
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // --- Dependencies managed by BOM (end)   ---

    // coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")

    // Kotlin logging with slf4j API and log4j logger
    implementation("io.github.microutils:kotlin-logging:1.7.8")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.9.1")
    implementation("org.apache.logging.log4j:log4j-api:2.9.1")
    implementation("org.apache.logging.log4j:log4j-core:2.9.1")

    // Use clikt (command line parser for kotlin) library
    implementation("com.github.ajalt:clikt:2.8.0")

    // use JGraphT library
    implementation("org.jgrapht:jgrapht-core:1.5.1")

    val cplexJarPath: String by project
    implementation(files(cplexJarPath))

    // Use the Kotlin JUnit integration.
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")

    // Jackson library to work with JSON/YAML.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0")
}

tasks {
    register<Delete>("cleanLogs") {
        delete(fileTree("logs") {
            include("*.db", "*.log", "*.lp", "*.yaml")
        })
    }

    register<Jar>("uberJar") {
        archiveFileName.set("uber.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        manifest {
            attributes("Main-Class" to "orienteering.main.MainKt")
        }

        val sourcesMain = sourceSets.main.get()
        sourcesMain.allSource.forEach { println("add from sources: ${it.name}") }
        from(sourceSets.main.get().output)

        dependsOn(configurations.runtimeClasspath)
        from(configurations.runtimeClasspath.get()
            .onEach { println("add from dependencies: ${it.name}") }
            .map { if (it.isDirectory) it else zipTree(it) })
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
        }
    }

    val cplexLibPath: String by project
    val args = listOf(
        "-Xms32m",
        "-Xmx22g",
        "-Djava.library.path=$cplexLibPath"
    )

    withType<JavaExec> {
        jvmArgs = args
    }

    withType<Test> {
        jvmArgs = args
        testLogging { showStandardStreams = true }
        useJUnitPlatform()
    }
}
