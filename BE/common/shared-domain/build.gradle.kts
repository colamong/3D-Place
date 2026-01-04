plugins {
    `java-library`
}

dependencies {
    //compileOnly는 다른 서비스에 전파 X
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("jakarta.validation:jakarta.validation-api:3.0.2")
    compileOnly("jakarta.annotation:jakarta.annotation-api:3.0.0")

    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
}