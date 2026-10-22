plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "con.themralex.astrayt"
    compileSdk = 36
    defaultConfig {
        applicationId = "con.themralex.astrayt"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").orElse("6").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.0.6").get()
        buildConfigField("String", "GITHUB_REPOSITORY", "\"${System.getenv("GITHUB_REPOSITORY") ?: "TheMrAleX/AstraYT"}\"")
        vectorDrawables.useSupportLibrary = true
    }
    val signingVars = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    if (signingVars.all { !System.getenv(it).isNullOrBlank() }) {
        signingConfigs.create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH"))
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.work)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.core)
    implementation(libs.splash)
    implementation(libs.profile)
    implementation(libs.palette)
    implementation(libs.paging)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.coil)
    implementation(libs.coil.network)
    implementation(libs.okhttp)
    implementation(libs.serialization)
    implementation(libs.coroutines)
    implementation(project(":extractor-patched"))
    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.junit)
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = variant.buildType == "release"
        if (variant.buildType == "debug") variant.enable = false
    }
}
