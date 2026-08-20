import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// 彩蛋游戏可选编译：把 jump/star/maze/farm/home 依 -Pgames 动态接入（默认 all）。
// 与旧「源码集裁剪」不同：未选中的游戏模块不参与编译与打包，classpath 上不存在其字节码，
// 装配侧用 ServiceLoader（:game:api 的 loadGameEntryProviders()）发现已在 classpath 的入口，天然更彻底。
val gameIds = listOf("jump", "star", "maze", "farm", "home")

val gamesRaw = (project.findProperty("games") as String?)?.trim()?.lowercase()
val enabledGames: Set<String> =
    when {
        gamesRaw.isNullOrBlank() || gamesRaw == "all" -> gameIds.toSet()
        gamesRaw == "none" -> emptySet()
        else -> {
            val picked = gamesRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val unknown = picked - gameIds
            if (unknown.isNotEmpty()) {
                logger.warn("未知的游戏 id：${unknown.joinToString()}（可选：${gameIds.joinToString()}）")
            }
            (picked intersect gameIds).toSet()
        }
    }

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// 放入 app/google-services.json 后自动启用 Crashlytics
val googleServicesFile = file("google-services.json")
val enableFirebaseCrashlytics = googleServicesFile.exists()
if (enableFirebaseCrashlytics) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.xiaoxiong.weather"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xiaoxiong.weather"
        minSdk = 26
        targetSdk = 37
        versionCode = 57
        versionName = "1.7.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // 仅保留 ARM 架构：x86/x86_64 只服务于模拟器，2026 年已无实际设备使用
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // full：默认版，含小熊彩蛋游戏；lite：游戏编译为空实现，包里零痕迹
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            isDefault = true
        }
        create("lite") {
            dimension = "edition"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:settings"))
    // :game:api 是无实现的接口契约层（GameEntry/口令常量/装配函数），:app 需无条件引用
    // （MainActivity/WeatherScreen/WeatherGameOverlay 均引用 GameEntry 类型）。lite 包仍
    // 保留该接口层字节码（约 1-2KB），但不含任何游戏实现模块——见 MODULARIZATION.md。
    implementation(project(":game:api"))
    // 动态游戏依赖：-Pgames 未选中的游戏模块不编译不进包，装配由 ServiceLoader 发现。
    // 仅挂在 full flavor（fullImplementation）：lite 版零游戏依赖，符合「lite 零痕迹」语义。
    if (enabledGames.isNotEmpty()) {
        "fullImplementation"(project(":game:common"))
    }
    if ("jump" in enabledGames) {
        "fullImplementation"(project(":game:jump"))
    }
    if ("star" in enabledGames) {
        "fullImplementation"(project(":game:star"))
    }
    if ("maze" in enabledGames) {
        "fullImplementation"(project(":game:maze"))
    }
    if ("farm" in enabledGames) {
        "fullImplementation"(project(":game:farm"))
    }
    if ("home" in enabledGames) {
        "fullImplementation"(project(":game:home"))
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tinypinyin)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.play.services.location)
    "baselineProfile"(project(":baselineprofile"))
    if (enableFirebaseCrashlytics) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
        implementation(libs.firebase.analytics)
    }
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(project(":core:test-fixtures"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
}
