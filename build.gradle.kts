import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
}

group = "org.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.ajalt.clikt:clikt:3.3.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("net.lingala.zip4j:zip4j:2.9.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("org.jetbrains.tbec.MainKt")
}

distributions {
    main {
        contents {
            println(layout.projectDirectory.dir("licenses"))
            from(layout.projectDirectory.dir("licenses")) {
                into("licenses")
            }
        }
    }
}