import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

abstract class FetchNeedle : DefaultTask() {
    @get:Input abstract val rev: Property<String>

    @get:Input abstract val pins: MapProperty<String, String>

    @get:OutputDirectory abstract val dir: DirectoryProperty

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @TaskAction
    fun fetch() {
        val out = dir.get().asFile.also { it.mkdirs() }
        pins.get().forEach { (name, want) ->
            val f = File(out, name)
            if (f.isFile && sha(f) == want) return@forEach
            val tmp = File(out, "$name.part")
            val c = URI("https://huggingface.co/Cactus-Compute/needle3/resolve/${rev.get()}/android-arm64/$name").toURL().openConnection()
            c.connectTimeout = 30_000
            c.readTimeout = 60_000
            c.getInputStream().use { i -> tmp.outputStream().use { i.copyTo(it) } }
            if (sha(tmp) != want) {
                tmp.delete()
                throw GradleException("$name does not match its pinned SHA-256")
            }
            f.delete()
            tmp.renameTo(f)
        }
    }
}

abstract class FetchLlama : DefaultTask() {
    @get:Input abstract val urls: MapProperty<String, String>

    @get:Input abstract val pins: MapProperty<String, String>

    @get:OutputDirectory abstract val dir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    @get:Inject abstract val arc: ArchiveOperations

    private fun sha(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i ->
            val b = ByteArray(1 shl 16)
            while (true) {
                val n = i.read(b)
                if (n < 0) break
                d.update(b, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    @TaskAction
    fun fetch() {
        val out = dir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        urls.get().forEach { (name, url) ->
            val tgz = File(temporaryDir, "$name.tar.gz")
            val c = URI(url).also { check(it.scheme == "https") }.toURL().openConnection()
            c.connectTimeout = 30_000
            c.readTimeout = 60_000
            c.getInputStream().use { i -> tgz.outputStream().use { i.copyTo(it) } }
            if (sha(tgz) != pins.get().getValue(name)) {
                tgz.delete()
                throw GradleException("$name does not match its pinned SHA-256")
            }
            fs.copy {
                from(arc.tarTree(arc.gzip(tgz)))
                into(File(out, name))
                includeEmptyDirs = false
                eachFile {
                    val s = relativePath.segments.filter { it.isNotEmpty() }
                    if (s.any { it == ".." || it == "." }) throw GradleException("$name holds an unsafe path")
                    relativePath = RelativePath(true, *s.drop(1).toTypedArray())
                }
            }
            tgz.delete()
        }
    }
}

val needleDir = layout.buildDirectory.dir("needle")
val fetchNeedle = tasks.register<FetchNeedle>("fetchNeedle") {
    rev = "27c0a9a5b3ca835e0b7dbeaccf555df03dac493d"
    pins = mapOf(
        "libneedle.a" to "b8e73952054686f68e4319dcf69ad72a3de57faab73b73d9a67898f2c9d66110",
        "needle.h" to "3aa713942528d944598458cecb4a262f2cc49349bec63355f91df0b159964e55",
    )
    dir = needleDir
}
val llamaDir = layout.buildDirectory.dir("llama")
val fetchLlama = tasks.register<FetchLlama>("fetchLlama") {
    urls = mapOf(
        "llama.cpp" to "https://github.com/ggml-org/llama.cpp/archive/refs/tags/b11306.tar.gz",
        "kleidiai" to "https://github.com/ARM-software/kleidiai/releases/download/v1.24.0/kleidiai-v1.24.0-src.tar.gz",
    )
    pins = mapOf(
        "llama.cpp" to "9423090c8c8543d1f8ee79057ce140cd601c7156bb6157b76a2c88051979900f",
        "kleidiai" to "9348b969e042d8890a54b01a463dbe71f5a4c074b5329e9c26a85ef3b68aa19b",
    )
    dir = llamaDir
}
tasks.matching { t -> listOf("generateJsonModel", "configureCMake", "buildCMake", "externalNativeBuild").any(t.name::startsWith) }.configureEach { dependsOn(fetchNeedle, fetchLlama) }

val ver = System.getenv("COMPANION_VERSION") ?: "0.1.0"
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "app.companion"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "app.companion"
        minSdk = 34
        targetSdk = 35
        versionCode = ver.split(".").map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }
        versionName = ver
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DNEEDLE_DIR=${needleDir.get().asFile}",
                    "-DLLAMA_DIR=${llamaDir.get().asFile}/llama.cpp",
                    "-DKLEIDIAI_DIR=${llamaDir.get().asFile}/kleidiai",
                    "-DANDROID_STL=c++_static",
                )
            }
        }
        buildConfigField("String", "GMAIL_CLIENT_ID", "\"${local.getProperty("gmail.webClientId", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = (local.getProperty("release.store") ?: System.getenv("COMPANION_KEYSTORE"))?.let(::file)
            storePassword = local.getProperty("release.password") ?: System.getenv("COMPANION_KEY_PASSWORD")
            keyAlias = "companion"
            keyPassword = local.getProperty("release.password") ?: System.getenv("COMPANION_KEY_PASSWORD")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources.excludes += listOf("META-INF/**/LICENSE*", "META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.fragment)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.work)
    implementation(libs.glance)
    implementation(libs.biometric)
    implementation(libs.window)
    implementation(libs.coroutines)
    implementation(libs.play.auth)
    implementation(libs.litert)
}
