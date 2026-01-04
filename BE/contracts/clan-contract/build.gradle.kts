plugins {
    `java-library`
}

dependencies {
    api(project(":common:shared-domain"))

    api(libs.spring.boot.starter.validation)
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    //compileOnly(libs.spring.boot.starter.web)

    // Lombok
    // compileOnly(libs.lombok)
    // annotationProcessor(libs.lombok)
}
