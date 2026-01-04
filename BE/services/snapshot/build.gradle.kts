plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.flyway)
    alias(libs.plugins.jooq)
}

dependencies {
    // =========================================================================
    // 공통 모듈
    // =========================================================================
    implementation(project(":common:shared-domain"))
    implementation(project(":common:shared-jooq"))
    implementation(project(":common:shared-redis"))
    implementation(project(":common:shared-kafka"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:config"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================

    // DB
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.jooq.postgres.extensions)
    implementation(libs.postgresql)

    // flyway
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // jOOQ 코드젠
    jooqGenerator(libs.jooq)
    jooqGenerator(libs.jooq.meta)
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.jooq.postgres.extensions)
    jooqGenerator(libs.postgresql)

    // Redis(reactive)/ redisson
    implementation(libs.spring.boot.starter.data.redis.reactive)
    implementation("org.redisson:redisson-spring-boot-starter:3.24.3")

    // S3
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3:3.1.1")
    implementation("io.minio:minio:8.5.10")

    // jgltf
    implementation("de.javagl:jgltf-model:2.0.3")
    implementation("de.javagl:jgltf-impl-v2:2.0.3")

    implementation("me.paulschwarz:spring-dotenv:3.0.0")

    //test
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.redis)

    // retry
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")


    // caffeine
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

configurations {
    create("flywayMigration")
}

dependencies {
    "flywayMigration"(libs.postgresql)
    "flywayMigration"(libs.flyway.database.postgresql)
}


buildscript {
    dependencies {
        classpath(libs.flyway.database.postgresql)
        classpath(libs.postgresql)
    }
}

flyway {
    url = System.getenv("SNAPSHOT_DATABASE") ?: "jdbc:postgresql://localhost:5433/testdb"
    user = System.getenv("POSTGRESQL_USERNAME")
    password = System.getenv("POSTGRESQL_PASSWORD")

    locations = arrayOf("filesystem:src/main/resources/db/migration")
    schemas = arrayOf("public")
    cleanDisabled = false
}

val generatedJooqSrcDir = file("src/main/jooq")

sourceSets {
    main {
        java {
            srcDir(generatedJooqSrcDir)
        }
    }
}

jooq {
    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN

                jdbc = org.jooq.meta.jaxb.Jdbc().apply {
                    driver = "org.postgresql.Driver"
                    url = System.getenv("SNAPSHOT_DATABASE") ?: "jdbc:postgresql://localhost:5433/testdb"
                    user = System.getenv("POSTGRESQL_USERNAME")
                    password = System.getenv("POSTGRESQL_PASSWORD")
                }

                generator = org.jooq.meta.jaxb.Generator().apply {
                    name = "org.jooq.codegen.JavaGenerator"

                    database = org.jooq.meta.jaxb.Database().apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        recordTimestampFields = "updated_at"
                        includes = "world|world_lod|chunk_index|chunk_snapshot|chunk_mesh|chunk_compaction_job"
                        forcedTypes = listOf(
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.snapshot.model.type.ArtifactKind"
                                binding = "com.colombus.snapshot.model.jooq.binding.ArtifactKindBinding"
                                includeTypes = "artifact_kind_enum"
                            },
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.snapshot.model.type.SnapshotKind"
                                binding = "com.colombus.snapshot.model.jooq.binding.SnapshotKindBinding"
                                includeTypes = "snapshot_kind_enum"
                            },
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.snapshot.model.type.CompactionStatus"
                                binding = "com.colombus.snapshot.model.jooq.binding.CompactionStatusBinding"
                                includeTypes = "compaction_status_enum"
                            },
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.snapshot.model.type.TombstoneReason"
                                binding = "com.colombus.snapshot.model.jooq.binding.TombstoneReasonBinding"
                                includeTypes = "tombstone_reason_enum"
                            },
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.snapshot.model.type.ColorSchema"
                                binding = "com.colombus.snapshot.model.jooq.binding.ColorSchemaBinding"
                                includeTypes = "color_schema_enum"
                            }
                        )
                    }

                    target = org.jooq.meta.jaxb.Target().apply {
                        packageName = "com.colombus.snapshot.jooq"
                        directory = generatedJooqSrcDir.absolutePath
                    }

                    generate = org.jooq.meta.jaxb.Generate().apply {
                        isPojos = false
                        isDaos = false
                        isDeprecated = false
                        isRecords = true
                    }
                }
            }
        }
    }
}

val skipJooq: Boolean =
    project.hasProperty("skipJooq") || (System.getenv("SKIP_JOOQ")?.toBoolean() == true)

if (!skipJooq) {
    tasks.named("generateJooq") {
        dependsOn("flywayMigrate")
    }

    tasks.named("compileJava") {
        dependsOn("generateJooq")
    }
}

tasks.withType<nu.studer.gradle.jooq.JooqGenerate>().configureEach {
    onlyIf { !skipJooq }
}

tasks.named("clean") {
    doLast {
        delete("build/generated-src/jooq")
    }
}