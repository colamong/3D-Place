plugins {
    `java-library`
}

dependencies {
    api(project(":common:shared-domain"))
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    // Kafka
    api(libs.spring.kafka)

    //test
    testImplementation(libs.testcontainers.kafka)
}