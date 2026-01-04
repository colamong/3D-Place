plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    // =========================================================================
    // 공통 모듈
    // =========================================================================
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:shared-redis"))
    implementation(project(":common:config"))

    // =========================================================================
    // Contract 모듈
    // =========================================================================
    implementation(project(":contracts:user-contract"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    // Web Webflux Auth Controller
    implementation(libs.spring.boot.starter.webflux)

    // Security
    implementation(libs.spring.boot.starter.oauth2.client)

    implementation(libs.spring.boot.starter.data.redis.reactive)
    implementation(libs.spring.session.data.redis)

    //검증
    implementation(libs.spring.boot.starter.validation)
}
