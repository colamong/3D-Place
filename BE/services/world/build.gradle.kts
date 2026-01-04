plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:shared-redis"))
    implementation(project(":common:config"))

    // Redis(reactive)
    implementation(libs.spring.boot.starter.data.redis.reactive)

    //웹
    implementation(libs.spring.boot.starter.web)

    // S3
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3:3.1.1")

    // caffeine
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // retry
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    implementation("me.paulschwarz:spring-dotenv:3.0.0")
}
