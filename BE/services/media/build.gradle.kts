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
    implementation(project(":common:shared-kafka"))
    implementation(project(":common:shared-security"))
    implementation(project(":common:shared-web"))
    implementation(project(":common:config"))

    // =========================================================================
    // Contract 모듈
    // =========================================================================
    implementation(project(":contracts:media-contract"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    // Web MVC Media Controller
    implementation(libs.spring.boot.starter.web)

    // Validation
    implementation(libs.spring.boot.starter.validation)

    // AWS SDK
    // AWS SDK v2
    implementation(platform(libs.aws.bom))
    implementation(libs.awssdk.s3)
    implementation("software.amazon.awssdk:sso")
    implementation("software.amazon.awssdk:ssooidc")   
    implementation(libs.awssdk.sts)

    // 파일 & MIME
    implementation(libs.commons.io)
    implementation(libs.tika.core)
    implementation(libs.tika.parsers.standard)
    implementation(libs.twelvemonkeys.core)
    implementation(libs.twelvemonkeys.webp)
    implementation(libs.twelvemonkeys.jpeg)

    // DB
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.postgresql)

    implementation(libs.kafka.clients)

    // flyway
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // jOOQ 코드젠
    jooqGenerator(libs.jooq)
    jooqGenerator(libs.jooq.meta)
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.postgresql)

    //test
    testImplementation(libs.testcontainers.postgresql)
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
    url = System.getenv("MEDIA_DATABASE") ?: "jdbc:postgresql://localhost:5432/mediadb"
    user = System.getenv("POSTGRESQL_USERNAME") 
    password = System.getenv("POSTGRESQL_PASSWORD")

    locations = arrayOf("filesystem:src/main/resources/db/migration")
    schemas = arrayOf("public")
    cleanDisabled = false
}

// val generatedJooqSrcDir: Provider<Directory> = layout.buildDirectory.dir("generated-src/jooq")
val generatedJooqSrcDir = file("src/main/jooq")

jooq {
    configurations {
        create("main") {
            jooqConfiguration.apply {
                    logging = org.jooq.meta.jaxb.Logging.WARN

                    jdbc = org.jooq.meta.jaxb.Jdbc().apply {
                        driver = "org.postgresql.Driver"
                        url = System.getenv("MEDIA_DATABASE") ?: "jdbc:postgresql://localhost:5432/mediadb"
                        user = System.getenv("POSTGRESQL_USERNAME") ?: "postgres"
                        password = System.getenv("POSTGRESQL_PASSWORD") ?: "P@ssw0rd"
                    }

                    generator = org.jooq.meta.jaxb.Generator().apply {
                        name = "org.jooq.codegen.JavaGenerator"

                        database = org.jooq.meta.jaxb.Database().apply {
                            name = "org.jooq.meta.postgres.PostgresDatabase"
                            inputSchema = "public"
                            includes = "subject_registry|staging_upload_ticket|image_asset|asset_link|outbox_event|outbox_publish_log"
                            excludes = "subject_registry_(user|clan|default)"
                            recordTimestampFields = "updated_at"
                            forcedTypes = listOf(
                                // JSONB -> JsonNode (image_asset.exif)
                                org.jooq.meta.jaxb.ForcedType().apply {
                                    userType = "com.fasterxml.jackson.databind.JsonNode"
                                    converter = "com.colombus.common.jooq.JsonNodeConverter"
                                    includeExpression = "public\\.image_asset\\.exif"
                                    includeTypes = "JSONB"
                                },

                                // Enum (asset_purpose_enum)
                                org.jooq.meta.jaxb.ForcedType().apply {
                                    includeTypes = "asset_purpose_enum"
                                    userType = "com.colombus.common.kafka.subject.model.type.AssetPurpose"
                                    binding  = "com.colombus.media.jooq.binding.AssetPurposeBinding"
                                },

                                // Enum (subject_kind_enum)
                                org.jooq.meta.jaxb.ForcedType().apply {
                                    includeTypes = "subject_kind_enum"
                                    userType = "com.colombus.common.kafka.subject.model.type.SubjectKind"
                                    binding  = "com.colombus.media.jooq.binding.SubjectKindBinding"
                                },

                                // Enum (outbox_status)
                                org.jooq.meta.jaxb.ForcedType().apply {
                                    userType = "com.colombus.common.domain.outbox.model.type.OutboxStatus"
                                    isEnumConverter = true
                                    includeTypes = "outbox_status"
                                },

                                // JSONB -> JsonNode (outbox_event.payload|headers)
                                org.jooq.meta.jaxb.ForcedType().apply {
                                    userType = "com.fasterxml.jackson.databind.JsonNode"
                                    converter = "com.colombus.common.jooq.JsonNodeConverter"
                                    includeExpression = "public\\.outbox_event\\.(payload|headers)"
                                    includeTypes = "JSONB"
                                }
                            )
                        }

                        target = org.jooq.meta.jaxb.Target().apply {
                            packageName = "com.colombus.media.jooq"
                            directory = generatedJooqSrcDir.absolutePath
                            // directory = generatedJooqSrcDir.get().asFile.absolutePath
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

sourceSets {
    main {
        java {
            srcDir(generatedJooqSrcDir)
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