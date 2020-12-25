import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm") version "1.4.21"

    // Apply the application plugin to add support for building a CLI application.
    application

    // Documentation plugin
    id("org.jetbrains.dokka").version("0.10.0")
}

repositories {
    // Use jcenter for resolving your dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

dependencies {
    // Align versions of all Kotlin components. We don't need to specify an explicit version for
    // "kotlin-bom" as the Kotlin gradle plugin takes care of it based on the Kotlin version
    // specified in the "plugins" seciton.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // --- Dependencies managed by BOM (start) ---
    // We don't need to specify versions for these dependencies, as they come from the "kotlin-bom"
    // dependency. Check a specific release of "kotlin-bom" in the following page to get libraries
    // with version numbers managed by the BOM:
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-bom
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // --- Dependencies managed by BOM (end)   ---

    // coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    // Kotlin logging with slf4j API and log4j logger
    implementation("io.github.microutils:kotlin-logging:1.7.8")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.9.1")
    implementation("org.apache.logging.log4j:log4j-api:2.9.1")
    implementation("org.apache.logging.log4j:log4j-core:2.9.1")

    // Use clikt (command line parser for kotlin) library
    implementation("com.github.ajalt:clikt:3.0.1")

    // use JGraphT library
    implementation("org.jgrapht:jgrapht-core:1.5.0")

    val cplexJarPath: String by project
    implementation(files(cplexJarPath))

    // Use the Kotlin JUnit integration.
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")

    // Jackson library to work with JSON/YAML.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.0")
}

tasks {
    dokka {
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
        configuration {
            includeNonPublic = true
            noStdlibLink = true
        }
    }

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

    withType<JavaExec> {
        val cplexLibPath: String by project
        jvmArgs = listOf(
            "-Xms32m",
            "-Xmx22g",
            "-Djava.library.path=$cplexLibPath"
        )
    }

    withType<Test> {
        val cplexLibPath: String by project
        jvmArgs = listOf(
            "-Xms32m",
            "-Xmx22g",
            "-Djava.library.path=$cplexLibPath"
        )

        testLogging {
            showStandardStreams = true
        }

        addTestListener(object : TestListener {
            override fun beforeTest(p0: TestDescriptor?) = Unit
            override fun beforeSuite(p0: TestDescriptor?) = Unit
            override fun afterTest(desc: TestDescriptor, result: TestResult) = Unit
            override fun afterSuite(desc: TestDescriptor, result: TestResult) {
                // printResults(desc, result)
            }
        })
        useJUnitPlatform()
    }
}

fun printResults(desc: TestDescriptor, result: TestResult) {
    if (desc.parent != null) {
        val output = result.run {
            "Results: $resultType (" +
                    "$testCount tests, " +
                    "$successfulTestCount successes, " +
                    "$failedTestCount failures, " +
                    "$skippedTestCount skipped" +
                    ")"
        }
        val testResultLine = "|  $output  |"
        val repeatLength = testResultLine.length
        val separationLine = "-".repeat(repeatLength)
        println(separationLine)
        println(testResultLine)
        println(separationLine)
    }
}

application {
    // Define the main class for the application.
    mainClassName = "orienteering.main.MainKt"
}

