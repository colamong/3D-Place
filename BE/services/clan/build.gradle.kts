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
    implementation(project(":contracts:clan-contract"))

    // =========================================================================
    // Spring Boot Starters
    // =========================================================================
    // Web WebFlux Clan Controller
    implementation(libs.spring.boot.starter.webflux)

    // Validation
    implementation(libs.spring.boot.starter.validation)

    // DB
    implementation(libs.spring.boot.starter.data.r2dbc)
    // implementation(libs.spring.boot.starter.jooq)
    implementation(libs.jooq)
    implementation(libs.jooq.postgres.extensions)
    // implementation(libs.postgresql)
    implementation(libs.r2dbc.postgresql)

    // flyway
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // jOOQ 코드젠
    jooqGenerator(libs.jooq)
    jooqGenerator(libs.jooq.meta)
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.jooq.postgres.extensions)
    jooqGenerator(libs.postgresql)
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
    url = System.getenv("CLAN_DATABASE") ?: "jdbc:postgresql://localhost:5432/clandb"
    user = System.getenv("POSTGRESQL_USERNAME") ?: "postgres"
    password = System.getenv("POSTGRESQL_PASSWORD") ?: "P@ssw0rd"

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
                    url = System.getenv("CLAN_DATABASE") ?: "jdbc:postgresql://localhost:5432/clandb"
                    user = System.getenv("POSTGRESQL_USERNAME") ?: "postgres"
                    password = System.getenv("POSTGRESQL_PASSWORD") ?: "P@ssw0rd"
                }

                generator = org.jooq.meta.jaxb.Generator().apply {
                    name = "org.jooq.codegen.JavaGenerator"

                    database = org.jooq.meta.jaxb.Database().apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = "clan_info|clan_member|clan_join_request|clan_invite_ticket|outbox_event|outbox_publish_log"
                        recordTimestampFields = "updated_at"
                        forcedTypes = listOf(
                            // OffsetDateTime -> Instant for all TIMESTAMPTZ columns
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "java.time.Instant"
                                converter = "com.colombus.common.jooq.OffsetDateTimeToInstantConverter"
                                includeExpression = "public\\.(clan_info|clan_member|clan_join_request|clan_invite_ticket|outbox_event)\\..*_at"
                                includeTypes = "TIMESTAMPTZ"
                            },

                            // JSONB -> JsonNode (outbox_event.payload|headers)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.fasterxml.jackson.databind.JsonNode"
                                converter = "com.colombus.common.jooq.JsonNodeConverter"
                                includeExpression = "public\\.clan_info\\.metadata"
                                includeTypes = "JSONB"
                            },

                            // Enum (clan_join_policy_enum)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.clan.model.type.ClanJoinPolicy"
                                binding  = "com.colombus.clan.model.jooq.binding.ClanJoinPolicyBinding"
                                // isEnumConverter = true
                                includeTypes = "clan_join_policy_enum"
                            },

                            // Enum (clan_member_role_enum)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.clan.model.type.ClanMemberRole"
                                binding  = "com.colombus.clan.model.jooq.binding.ClanMemberRoleBinding"
                                // isEnumConverter = true
                                includeTypes = "clan_member_role_enum"
                            },

                            // Enum (clan_member_status_enum)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.clan.model.type.ClanMemberStatus"
                                binding  = "com.colombus.clan.model.jooq.binding.ClanMemberStatusBinding"
                                // isEnumConverter = true
                                includeTypes = "clan_member_status_enum"
                            },

                            // Enum (clan_member_status_enum)
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "com.colombus.clan.model.type.ClanJoinRequestStatus"
                                binding  = "com.colombus.clan.model.jooq.binding.ClanJoinRequestStatusBinding"
                                // isEnumConverter = true
                                includeTypes = "clan_join_request_status_enum"
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
                        packageName = "com.colombus.clan.jooq"
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
