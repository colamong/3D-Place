plugins {
    `java-library`
}
dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(libs.nimbus.jose.jwt)

    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
    
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.oauth2.resource.server)
    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)
}