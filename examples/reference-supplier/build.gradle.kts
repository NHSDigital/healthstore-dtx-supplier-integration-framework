import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("org.openapi.generator") version "7.24.0"
}

group = "uk.nhs.healthstore.dtx"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

val supplierServerGen = layout.buildDirectory.dir("generated/supplier-server").get()
val registrationsClientGen = layout.buildDirectory.dir("generated/registrations-client").get()

val generateSupplierServer by tasks.registering(GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set(layout.projectDirectory.file("../../specification/supplier-api.yaml").asFile.absolutePath)
    outputDir.set(supplierServerGen.asFile.absolutePath)
    apiPackage.set("uk.nhs.healthstore.dtx.referencesupplier.api")
    modelPackage.set("uk.nhs.healthstore.dtx.referencesupplier.api.model")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "documentationProvider" to "none",
            "openApiNullable" to "false",
        )
    )
}

val generateRegistrationsClient by tasks.registering(GenerateTask::class) {
    generatorName.set("java")
    library.set("restclient")
    inputSpec.set(layout.projectDirectory.file("../../specification/healthstore-api.yaml").asFile.absolutePath)
    outputDir.set(registrationsClientGen.asFile.absolutePath)
    apiPackage.set("uk.nhs.healthstore.dtx.referencesupplier.registrations.client")
    modelPackage.set("uk.nhs.healthstore.dtx.referencesupplier.registrations.model")
    invokerPackage.set("uk.nhs.healthstore.dtx.referencesupplier.registrations")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "useJakartaEe" to "true",
            "openApiNullable" to "false",
        )
    )
}

sourceSets {
    main {
        java {
            srcDir(supplierServerGen.dir("src/main/java"))
            srcDir(registrationsClientGen.dir("src/main/java"))
        }
    }
}

tasks.compileJava {
    dependsOn(generateSupplierServer, generateRegistrationsClient)
}
