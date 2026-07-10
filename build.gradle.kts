plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "com.nimba"
version = "0.0.1-SNAPSHOT"

extra["springModulithVersion"] = "2.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    // Outbound e-mail for account invitations (set-password links).
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Object storage (MinIO/S3) for user avatars.
    implementation("io.minio:minio:8.5.14")
    // Word (.docx) generation for the trades / lettres de change document.
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Robust CSV parsing for the amortization-schedule upload pipeline (NIMBA-15).
    implementation("org.apache.commons:commons-csv:1.14.1")
    // OpenAPI contract + Swagger UI (interactive API docs at /swagger-ui.html).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Code coverage gate. The 70% line-coverage minimum applies to business logic
// (the domain/application code added per story); the application bootstrap and
// module marker packages carry no testable logic and are excluded so the rule
// stays meaningful rather than measuring framework glue.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.nimba.NimbaApplication",
                    "com.nimba.NimbaApplicationKt",
                    // Object-storage (MinIO) integration is I/O glue verified against a
                    // running MinIO, not unit tests — excluded like other framework glue.
                    "com.nimba.identity.internal.MinioConfig",
                    "com.nimba.identity.internal.MinioProperties",
                    "com.nimba.identity.internal.AvatarStorage",
                    "com.nimba.identity.internal.AvatarObject",
                    "com.nimba.identity.internal.ProfileAvatarService",
                    "com.nimba.identity.internal.AvatarController",
                    "com.nimba.identity.internal.OrganizationLogoStorage",
                    "com.nimba.identity.internal.OrganizationLogoService",
                    "com.nimba.identity.internal.OrganizationLogoObject",
                    "com.nimba.guarantee.internal.GuaranteeStorageProperties",
                    "com.nimba.guarantee.internal.GuaranteeAttachmentStorage",
                    "com.nimba.guarantee.internal.GuaranteeAttachmentService",
                )
            }
        }
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
