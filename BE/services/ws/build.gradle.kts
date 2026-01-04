plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-kafka"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:config"))

    //웹 / 보안(리소스 서버)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // WebSocket/STOMP
    implementation(libs.spring.boot.starter.websocket)

    // lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Kafka
    implementation(libs.spring.kafka)

    //test
    testImplementation(libs.testcontainers.kafka)
}
