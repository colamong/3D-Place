plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-redis"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:shared-kafka"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:config"))

    implementation(project(":contracts:paint-contract"))

    // 웹
    implementation(libs.spring.boot.starter.webflux)

    // lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Kafka
    implementation(libs.spring.kafka)

    // Redis(reactive)
    implementation(libs.spring.boot.starter.data.redis.reactive)

    // S3
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3:3.1.1")

    // Caffeine
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // test
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.redis)
}
