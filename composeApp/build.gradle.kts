plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

group = "com.aritiq.calcnote"

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.primitive.adapter)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment.ktx)
        }

        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotlin.test)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.annotations.common)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.aritiq.calcnote"
    compileSdk = 36

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Aritiq-${versionName}-${buildType.name}.apk"
        }
    }

    defaultConfig {
        applicationId = "com.aritiq.calcnote"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("ARITIQ_KEYSTORE") ?: "missing.jks")
            storePassword = System.getenv("ARITIQ_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ARITIQ_KEY_ALIAS")
            keyPassword = System.getenv("ARITIQ_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            if (System.getenv("ARITIQ_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        named("main") {
            // SQLDelight .sq files live here; the plugin emits Kotlin into build.
            assets.srcDirs("src/androidMain/assets")
        }
    }
}

sqldelight {
    databases {
        create("AritiqDatabase") {
            packageName.set("com.aritiq.calcnote.data.db")
            srcDirs("src/commonMain/sqldelight")
        }
    }
}

