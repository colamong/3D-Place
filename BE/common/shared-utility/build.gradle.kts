plugins {
    `java-library`
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    implementation(libs.slf4j.api)

    compileOnly(libs.jakarta.annotation.api)
}