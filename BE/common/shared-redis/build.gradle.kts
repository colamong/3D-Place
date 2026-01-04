plugins {
    `java-library`
}
dependencies {
    api(project(":common:shared-domain"))

    // Redis(reactive)
    // implementation(libs.spring.boot.starter.data.redis.reactive)

    compileOnly(libs.spring.boot.starter.data.redis)
    compileOnly(libs.spring.boot.starter.data.redis.reactive)

    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
    
    //test
    testImplementation(libs.testcontainers.redis)
}
