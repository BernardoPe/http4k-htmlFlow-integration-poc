plugins {
    kotlin("jvm") version "2.1.20"
}

group = "com.github.xmlet"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(platform("org.http4k:http4k-bom:6.15.1.0"))
    implementation("org.http4k:http4k-template-core:6.15.1.0")

    implementation("com.github.xmlet:htmlflow:4.8-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}