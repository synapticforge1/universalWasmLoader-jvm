plugins {
    kotlin("jvm") version "2.2.0"
    `java-library`
    // 0.29.0 called the deprecated StartParameter.isConfigurationCacheRequested (to be
    // removed in Gradle 10) from publishToMavenCentral; 0.37.0 does not (measured 2026-10-26).
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.jrmarcum"
version = "0.2.0"

repositories {
    mavenCentral()
}

// Chicory — pure-Java WASM runtime, no native code required
val chicoryVersion = "1.0.0"

dependencies {
    implementation("com.dylibso.chicory:runtime:$chicoryVersion")
    implementation("com.dylibso.chicory:wasm:$chicoryVersion")
    // Minimal WASI Preview 1 (SPEC §10.2) so I/O-using library modules can load.
    implementation("com.dylibso.chicory:wasi:$chicoryVersion")

    // Kotlin coroutines for wasmImport (async URL loading) and InstancePool
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// JDK 25 toolchain; Kotlin 2.2.0 max bytecode target is JVM_24.
// Requires JDK 24+ to run.
kotlin {
    jvmToolchain(25)

    // Compatibility gate, Rule 1: the public API as a committed snapshot (api/*.api),
    // from the Kotlin Gradle plugin's own ABI validation (the standalone
    // binary-compatibility-validator 0.18.1 cannot read JVM 24 class files).
    // `checkLegacyAbi` fails when the code and the snapshot disagree; `updateLegacyAbi`
    // re-baselines, in its own commit (cmem/compat-gate.md).
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "24"
    targetCompatibility = "24"
}

tasks.test {
    useJUnitPlatform()
}

// KGP 2.2's ABI validation does not hook `check` by itself (measured 2026-10-26: `check
// --dry-run` lists no checkLegacyAbi). Without this a stale api/ snapshot passes CI.
tasks.named("check") {
    dependsOn("checkLegacyAbi")
}

// Raises the project version in THIS file (the single source of truth), through the
// compatibility gate: it refuses a bump below the floor (cmem/compat-gate.md). Usage:
//   ./gradlew bump                 # patch:  0.1.2 -> 0.1.3  (default)
//   ./gradlew bump -Pkind=minor    # minor:  0.1.2 -> 0.2.0
//   ./gradlew bump -Pkind=major    # major:  0.1.2 -> 1.0.0
// Delegates to scripts/compat.main.kts so there is one bump implementation.
tasks.register("bump") {
    group = "versioning"
    description = "Raises the version in build.gradle.kts (patch|minor|major; patch default), gated."
    doLast {
        val kind = (project.findProperty("kind") as String?) ?: "patch"
        require(kind in listOf("patch", "minor", "major")) {
            "bump: unknown kind '$kind' — use -Pkind=patch|minor|major"
        }
        val script = file("scripts/compat.main.kts").absolutePath
        // `kotlin` is a .cmd/.bat launcher on Windows, so it goes through cmd there.
        val cmd = if (System.getProperty("os.name").lowercase().contains("win")) {
            listOf("cmd", "/c", "kotlin", script, "bump", kind)
        } else {
            listOf("kotlin", script, "bump", kind)
        }
        val exit = ProcessBuilder(cmd)
            .directory(projectDir)
            .inheritIO()
            .start()
            .waitFor()
        check(exit == 0) { "compat.main.kts bump failed (exit $exit)" }
    }
}

tasks.register("release") {
    group = "publishing"
    description = "Tags HEAD as v<version> and pushes the tag to origin to trigger CI publish."
    doLast {
        val tag = "v$version"
        for (cmd in listOf(
            listOf("git", "tag", "-f", tag),
            listOf("git", "push", "origin", "-f", "refs/tags/$tag")
        )) {
            val exit = ProcessBuilder(cmd).inheritIO().start().waitFor()
            check(exit == 0) { "'${cmd.joinToString(" ")}' failed (exit $exit)" }
        }
        println("Pushed $tag — CI publish workflow triggered.")
    }
}

mavenPublishing {
    // Central Portal (the plugin's only target since 0.34). automaticRelease: the upload
    // publishes itself once validated — no manual "Publish" click after a tag (owner, 2026-10-26).
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("io.github.jrmarcum", "universal-wasm-loader-jvm", version.toString())

    pom {
        name.set("Universal WASM Loader JVM")
        description.set("A lightweight WebAssembly loader for JVM languages, built on the pure-Java Chicory runtime.")
        url.set("https://github.com/synapticforge1/universalWasmLoader-jvm")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("jrmarcum")
                name.set("Jon Marcum")
                email.set("jrmarcum.se@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/synapticforge1/universalWasmLoader-jvm.git")
            developerConnection.set("scm:git:ssh://github.com/synapticforge1/universalWasmLoader-jvm.git")
            url.set("https://github.com/synapticforge1/universalWasmLoader-jvm")
        }
    }
}
