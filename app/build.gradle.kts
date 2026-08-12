import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.ktlint)
}

// 彩蛋游戏可选编译：默认把 4 个小游戏全部编译进 full 版；
// 可用 -Pgames=jump,star,maze,farm 只编译其中若干（逗号分隔），-Pgames=none 不含任何游戏（等价 lite 入口）。
// 未选中的游戏对应源码集（src/gameXxx/kotlin）不会参与编译，仅编译空实现门面（src/gameXxxEmpty/kotlin），
// 由 main 的彩蛋入口引用，保证任意组合下 WeatherGameOverlay 都能编译通过。
val gameIds = listOf("jump", "star", "maze", "farm")

fun String.toGameDirName(): String = replaceFirstChar { it.uppercase() }

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
        versionCode = 42
        versionName = "1.5.8"
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

    kotlinOptions {
        jvmTarget = "17"
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

    sourceSets {
        getByName("full") {
            // 共享代码：跳跳/摘星/迷宫/农场任一共用（天气分类、小熊精灵、天空与降水特效、排行榜）
            if (enabledGames.isNotEmpty()) {
                kotlin.srcDir("src/gameCommon/kotlin")
            }
            for (id in gameIds) {
                // 选中的游戏编译真实实现，未选中的编译空实现门面
                val dir = if (id in enabledGames) "src/game${id.toGameDirName()}/kotlin" else "src/game${id.toGameDirName()}Empty/kotlin"
                kotlin.srcDir(dir)
            }
        }
        // 单元测试源集只编译选中游戏的测试，避免引用未编译的符号
        getByName("testFull") {
            for (id in gameIds) {
                if (id in enabledGames) {
                    kotlin.srcDir("src/game${id.toGameDirName()}Test/kotlin")
                }
            }
        }
    }
}

dependencies {
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
}
