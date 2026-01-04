plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-redis"))
    implementation(project(":common:shared-kafka"))
    implementation(project(":common:shared-security"))

    //웹
    implementation(libs.spring.boot.starter.webflux)

    //JPA / DB
    // implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)

    // Kafka
    implementation(libs.spring.kafka)

    // Redis(reactive)
    implementation(libs.spring.boot.starter.data.redis.reactive)

    //test
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.redis)

}
