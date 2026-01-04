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
    // Contract 모듈
    // =========================================================================
    implementation(project(":contracts:user-contract"))
    implementation(project(":contracts:clan-contract"))
    implementation(project(":contracts:media-contract"))
    implementation(project(":contracts:paint-contract"))
    implementation(project(":contracts:leaderboard-contract"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    implementation(libs.spring.boot.starter.webflux)

    // Validation
    implementation(libs.spring.boot.starter.validation)
}
