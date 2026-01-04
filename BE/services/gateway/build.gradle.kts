plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    // =========================================================================
    // 공통 모듈
    // =========================================================================
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:config"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    // Webflux Gateway Controller
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(platform(libs.spring.cloud.dependencies))

    implementation(libs.spring.boot.starter.data.redis)
}

tasks.test {
    useJUnitPlatform()
}