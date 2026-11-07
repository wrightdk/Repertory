import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// TMDB API key, first non-empty of: local.properties (git-ignored, dev override)
// -> TMDB_API_KEY environment variable -> gradle.properties (committed app key).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val tmdbApiKey: String = localProperties.getProperty("TMDB_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getenv("TMDB_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
    ?: providers.gradleProperty("TMDB_API_KEY").orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: ""

android {
    namespace = "io.github.wrightdk.repertory"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.wrightdk.repertory"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric needs merged resources/manifest for Room's in-memory database.
            isIncludeAndroidResources = true
            all {
                // TmdbLiveKeyTest hits the real API; only runs with -PtmdbLive.
                it.systemProperty("tmdb.live", providers.gradleProperty("tmdbLive").isPresent.toString())
                // Robolectric reaches into FileDescriptor internals, which newer JDKs lock down.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

room {
    // Exported schemas are committed so future migrations can be tested.
    schemaDirectory("$projectDir/schemas")
}

/**
 * Bundles the licence texts shown on About as assets, copied from the repo's
 * own LICENSE and third_party/ at build time so there's only one copy of each.
 */
abstract class CopyLicenceTexts : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appLicence: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val newsreaderLicence: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val plexLicence: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().dir("licences").asFile
        dir.deleteRecursively()
        dir.mkdirs()
        appLicence.get().asFile.copyTo(dir.resolve("gpl-3.0.txt"))
        newsreaderLicence.get().asFile.copyTo(dir.resolve("ofl-newsreader.txt"))
        plexLicence.get().asFile.copyTo(dir.resolve("ofl-ibm-plex-sans.txt"))
    }
}

val copyLicenceTexts = tasks.register<CopyLicenceTexts>("copyLicenceTexts") {
    val root = rootProject.layout.projectDirectory
    appLicence.set(root.file("LICENSE"))
    newsreaderLicence.set(root.file("third_party/fonts/newsreader/OFL.txt"))
    plexLicence.set(root.file("third_party/fonts/ibm-plex-sans/OFL.txt"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLicenceTexts, CopyLicenceTexts::outputDir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}