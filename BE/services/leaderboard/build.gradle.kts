plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    // =========================================================================
    // 공통 모듈
    // =========================================================================
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-kafka"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:config"))

    // =========================================================================
    // Contract 모듈
    // =========================================================================
    implementation(project(":contracts:leaderboard-contract"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    // Web MVC Leaderboard Controller
    implementation(libs.spring.boot.starter.web)

    // Kafka
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)
    implementation(libs.kafka.streams)
}