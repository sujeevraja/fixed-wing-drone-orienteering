import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin application project to get you started.
 */

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    id("org.jetbrains.kotlin.jvm").version("1.3.40")

    // Apply the application plugin to add support for building a CLI application.
    application

    // Documentation plugin
    id("org.jetbrains.dokka").version("0.9.18")
}

repositories {
    // Use jcenter for resolving your dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

dependencies {
    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Kotlin logging with slf4j API and log4j logger
    implementation("io.github.microutils:kotlin-logging:1.6.24")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.9.1")
    implementation("org.apache.logging.log4j:log4j-api:2.9.1")
    implementation("org.apache.logging.log4j:log4j-core:2.9.1")

    // Use clikt (command line parser for kotlin) library
    implementation("com.github.ajalt:clikt:2.0.0")

    // use JGraphT library
    implementation("org.jgrapht:jgrapht-core:1.3.0")

    // YAML serialization
    implementation("com.charleskorn.kaml:kaml:0.11.0")

    compileClasspath("org.junit.platform:junit-platform-gradle-plugin:1.0.0-M3")

    val cplexJarPath: String by project
    compile(files(cplexJarPath))

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")


    // Use the Kotlin JUnit integration.
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
//    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

}

tasks {
    dokka {
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
        includeNonPublic = true
        noStdlibLink = true
    }

    register<Delete>("cleanLogs") {
        delete(fileTree("logs") {
            include("*.log")
        })
    }
}

application {
    // Define the main class for the application.
    mainClassName = "orienteering.main.MainKt"

    val cplexLibPath : String by project
    applicationDefaultJvmArgs = listOf(
            "-Xms32m",
            "-Xmx22g",
            "-Djava.library.path=$cplexLibPath")
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}


tasks.withType<Test> {
    this.testLogging {
        this.showStandardStreams = true
    }

    addTestListener(object : TestListener {
        override fun beforeTest(p0: TestDescriptor?) = Unit
        override fun beforeSuite(p0: TestDescriptor?) = Unit
        override fun afterTest(desc: TestDescriptor, result: TestResult) = Unit
        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
//            printResults(desc, result)
        }
    })
    useJUnitPlatform()
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

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
