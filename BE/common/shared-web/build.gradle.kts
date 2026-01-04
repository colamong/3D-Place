plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.validation)
    
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)

    compileOnly(libs.micrometer.tracing)
    
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
}