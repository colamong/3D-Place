plugins {
    `java-library`
}

dependencies {
    implementation(project(":common:shared-utility"))
    
    api(libs.jooq)
    compileOnly(libs.jooq.postgres.extensions)
    compileOnly(libs.postgresql)

    compileOnly(libs.jakarta.annotation.api)
    implementation(libs.jackson.databind)

    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.r2dbc.spi)
    implementation(libs.spring.boot.autoconfigure)
}