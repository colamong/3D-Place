plugins {
    // 루트에서는 apply=false (각 모듈에서 명시 적용)
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.colombus"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    // Java 21 툴체인 & 모든 Java 모듈에 BOM 주입
    plugins.withType<JavaPlugin> {
        the<JavaPluginExtension>().toolchain.apply {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.AMAZON)
        }
        dependencies {
            // Boot BOM
            val bootBom = platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
            add("implementation", bootBom)
            add("compileOnly", bootBom)
            add("testImplementation", bootBom)
            
            // OpenTelemetry BOM (모든 모듈에 주입)
            val otelBom = platform(libs.opentelemetry.bom.get())
            add("implementation", otelBom)

            // Lombok (모든 자바 모듈 공통)
            add("compileOnly", libs.lombok.get())
            add("annotationProcessor", libs.lombok.get())
            add("testCompileOnly", libs.lombok.get())
            add("testAnnotationProcessor", libs.lombok.get())

            // SLF4J API (모든 자바 모듈 공통)
            add("implementation", libs.slf4j.api.get())
        }
    }
    //  테스트 공통
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "ALL-SYSTEM"))
}
}


// 모든 서비스(:services:*) 공통 의존성
configure(subprojects.filter { it.path.startsWith(":services:") }) {
    pluginManager.withPlugin("org.springframework.boot") {
        plugins.withType<JavaPlugin> {
            dependencies {
                // =====================================================
                // 공통 유틸리티
                // =====================================================
                add("implementation", project(":common:shared-utility"))

                // =====================================================
                // 공통 운영/모니터링
                // =====================================================
                add("implementation", libs.spring.boot.starter.actuator.get())
                
                // Prometheus Metrics
                add("implementation", libs.micrometer.prometheus.get())
                
                // OTLP Metrics Export
                add("implementation", libs.micrometer.registry.otlp.get())
                
                // OpenTelemetry Tracing
                add("implementation", "io.micrometer:micrometer-tracing")
                add("implementation", "io.micrometer:micrometer-tracing-bridge-otel")
                
                add("implementation", libs.opentelemetry.api.get())
                add("implementation", libs.opentelemetry.sdk.get())
                add("implementation", libs.opentelemetry.exporter.otlp.get())
                
                // OpenTelemetry Annotations (@WithSpan 등)
                add("implementation", libs.opentelemetry.instrumentation.annotations.get())

                // =====================================================
                // 공통 테스트
                // =====================================================
                add("testImplementation", libs.spring.boot.starter.test.get())
                add("testImplementation", libs.spring.boot.testcontainers.get())
                add("testImplementation", libs.testcontainers.junit.get())
            }
        }
        // 전 서비스 공통 JUnit5
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("FAILED", "SKIPPED")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

// Spring Cloud BOM
configure(listOf(
    project(":services:gateway"),
    project(":services:auth"),
    project(":services:bff"),
    project(":services:user"),
    project(":services:clan"),
    project(":services:media"),
    project(":services:world"),
    project(":services:ws")
)) {
    pluginManager.withPlugin("org.springframework.boot") {
        dependencies {
            add("implementation", platform("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.spring.cloud.get()}"))
        }
    }
}

