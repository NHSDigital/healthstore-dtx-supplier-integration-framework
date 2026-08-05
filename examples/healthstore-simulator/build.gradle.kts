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

val registrationsServerGen = layout.buildDirectory.dir("generated/registrations-server").get()
val supplierClientGen = layout.buildDirectory.dir("generated/supplier-client").get()

val generateRegistrationsServer by tasks.registering(GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set(layout.projectDirectory.file("../../specification/healthstore-api.yaml").asFile.absolutePath)
    outputDir.set(registrationsServerGen.asFile.absolutePath)
    apiPackage.set("uk.nhs.healthstore.dtx.simulator.api")
    modelPackage.set("uk.nhs.healthstore.dtx.simulator.api.model")
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

val generateSupplierClient by tasks.registering(GenerateTask::class) {
    generatorName.set("java")
    library.set("restclient")
    inputSpec.set(layout.projectDirectory.file("../../specification/supplier-api.yaml").asFile.absolutePath)
    outputDir.set(supplierClientGen.asFile.absolutePath)
    apiPackage.set("uk.nhs.healthstore.dtx.simulator.supplier.client")
    modelPackage.set("uk.nhs.healthstore.dtx.simulator.supplier.model")
    invokerPackage.set("uk.nhs.healthstore.dtx.simulator.supplier")
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
            srcDir(registrationsServerGen.dir("src/main/java"))
            srcDir(supplierClientGen.dir("src/main/java"))
        }
    }
}

tasks.compileJava {
    dependsOn(generateRegistrationsServer, generateSupplierClient)
}
