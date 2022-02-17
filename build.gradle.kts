import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    application
}

application {
    group = "com.alphaalexcompany.friendbot"
    version = "1.0-SNAPSHOT"

    mainClass.set("MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/sdk-1.0.9.jar"))
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.apache.commons:commons-text:1.9")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.asynchttpclient:async-http-client:2.12.3")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("org.apache.httpcomponents:httpmime:4.5.13")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    implementation("org.apache.logging.log4j:log4j-api:2.17.0")

    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.3")
    implementation("org.litote.kmongo:kmongo-native:4.4.0")

    implementation("io.ktor:ktor-server-core:1.6.7")
    implementation("io.ktor:ktor-server-netty:1.6.7")
    implementation("io.ktor:ktor-auth:1.6.7")

    testImplementation(kotlin("test"))
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_16.toString()
    }

    create("stage") {
        dependsOn("installDist")
    }

    test {
        useTestNG()
    }
}
