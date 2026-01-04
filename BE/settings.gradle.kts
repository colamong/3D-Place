pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
    // NOTE: gradle/libs.versions.toml 은 Gradle이 자동으로 libs 카탈로그로 로드함
}

rootProject.name = "Colombus"

// =============================================================================
// Common Modules
// =============================================================================
include(
    "common:shared-domain",
    "common:shared-web",
    "common:shared-jooq",
    "common:shared-redis",
    "common:shared-kafka",
    "common:shared-security",
    "common:shared-utility",
    "common:config"
)

// =============================================================================
// Contract Modules
// =============================================================================
include(
    "contracts:user-contract",
    "contracts:clan-contract",
    "contracts:media-contract",
    "contracts:leaderboard-contract",
    "contracts:paint-contract",
)

// =============================================================================
// Service Modules
// 모든 서비스는 OTLP/Tracing 의존성이 자동으로 주입됨 (루트 build.gradle.kts 참조)
// =============================================================================
include(
    "services:bff",
    "services:auth",
    "services:media",
    "services:user",
    "services:clan",
    "services:leaderboard",
    "services:world",
    "services:ws",
    "services:paint",
    "services:snapshot",
    "services:report",
    "services:gateway"
)