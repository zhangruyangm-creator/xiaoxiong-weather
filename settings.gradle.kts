pluginManagement {
    repositories {
        // 官方源优先：GitHub Actions（海外）直连稳定；aliyun 仅作国内回退
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "XiaoxiongWeather"
include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":core:res-illustrations")
include(":core:test-fixtures")
include(":feature:weather")
include(":feature:settings")
include(":game:api")
include(":game:common")
include(":game:jump")
include(":game:star")
include(":game:maze")
include(":game:farm")
include(":game:home")
