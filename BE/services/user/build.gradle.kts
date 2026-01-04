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
    implementation(project(":contracts:user-contract"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    // Web MVC User Controller
    implementation(libs.spring.boot.starter.web)

    // Validation
    implementation(libs.spring.boot.starter.validation)

    // DB
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
    url = System.getenv("USER_DATABASE") ?: "jdbc:postgresql://localhost:5432/userdb"
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
                    url = System.getenv("USER_DATABASE") ?: "jdbc:postgresql://localhost:5432/userdb"
                    user = System.getenv("POSTGRESQL_USERNAME")
                    password = System.getenv("POSTGRESQL_PASSWORD")
                }

                generator = org.jooq.meta.jaxb.Generator().apply {
                    name = "org.jooq.codegen.JavaGenerator"

                    database = org.jooq.meta.jaxb.Database().apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = "user_account|user_identity|auth_event|nickname_series|outbox_event|outbox_publish_log"
                        recordTimestampFields = "updated_at"
                        forcedTypes = listOf(
                            // // INET -> String
                            // org.jooq.meta.jaxb.ForcedType().apply {
                            //     name = "java.lang.String"
                            //     includeTypes = "inet"
                            // },

                            // OffsetDateTime -> Instant for all TIMESTAMPTZ columns
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "java.time.Instant"
                                converter = "com.colombus.common.jooq.OffsetDateTimeToInstantConverter"
                                includeExpression = "public\\.(user_account|user_identity|auth_event|outbox_event)\\..*_at"
                                includeTypes = "TIMESTAMPTZ"
                            },

                            // JSONB -> JsonNode (user_account.metadata)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.fasterxml.jackson.databind.JsonNode"
                                converter = "com.colombus.common.jooq.JsonNodeConverter"
                                includeExpression = "public\\.user_account\\.metadata"
                                includeTypes = "JSONB"
                            },

                            // Enum (auth_provider)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.user.model.type.AuthProvider"
                                binding  = "com.colombus.user.model.jooq.binding.AuthProviderBinding"
                                includeTypes = "auth_provider"
                            },

                            // Enum (auth_event_kind)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.user.model.type.AuthEventKind"
                                binding  = "com.colombus.user.model.jooq.binding.AuthEventKindBinding"
                                includeTypes = "auth_event_kind"
                            },

                            // Enum (user_account.account_role)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.user.model.type.AccountRole"
                                binding  = "com.colombus.user.model.jooq.binding.AccountRoleBinding"
                                includeTypes = "account_role"
                            },

                            // Enum[] (account_role)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "java.util.List<com.colombus.user.model.type.AccountRole>"
                                converter = "com.colombus.user.model.jooq.converter.AccountRoleListConverter"
                                includeTypes = "_account_role"
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
                        packageName = "com.colombus.user.jooq"
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
