import org.gradle.api.Action
import org.gradle.api.execution.TaskExecutionGraph

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ktlint) apply false
}

// 全模块统一启用 ktlint：核心/功能/游戏模块的格式检查与 :app 保持一致，
// 避免模块代码游离于代码风格约束之外（.editorconfig 为唯一规则来源）。
subprojects {
    apply(plugin = "io.github.usefulness.ktlint-gradle-plugin")
}

// 配置缓存开启时每次构建都会向 build/reports/configuration-cache 写入诊断报告，
// Gradle 不自动清理、也无开关可关，长期会无限膨胀（本项目曾累积 187MB）。
// 报告在任务图执行完毕、配置缓存存储阶段才写入，任务层面永远删不到它；
// 因此在每次真实构建配置完成时（whenReady，配置缓存兼容）直接删除上一次遗留的报告，
// 使该目录最多保留当前构建生成的 1 个条目，不再累积。
gradle.taskGraph.whenReady(
    object : Action<TaskExecutionGraph> {
        override fun execute(graph: TaskExecutionGraph) {
            layout.buildDirectory.dir("reports/configuration-cache").get().asFile.deleteRecursively()
        }
    },
)
