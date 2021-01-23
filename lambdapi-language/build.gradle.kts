import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    kotlin("kapt") version "1.4.21"
    `java-library`
    java
}

repositories {
  jcenter()
  mavenCentral()
}

val graalVersion = "20.2.0"
val compiler: Configuration by configurations.creating

dependencies {
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.1")
    kapt("com.github.h0tk3y.betterParse:better-parse:0.4.1")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))

    compiler("org.graalvm.compiler:compiler:$graalVersion")
    api("org.graalvm.compiler:compiler:$graalVersion")
    api("org.graalvm.sdk:graal-sdk:$graalVersion")
    api("org.graalvm.sdk:launcher-common:$graalVersion")
    api("org.graalvm.truffle:truffle-api:$graalVersion")
    testApi("org.graalvm.truffle:truffle-api:$graalVersion")
    testApi("org.graalvm.compiler:compiler:$graalVersion")
    kapt("org.graalvm.truffle:truffle-api:$graalVersion")
    kapt("org.graalvm.truffle:truffle-dsl-processor:$graalVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
    modularity.inferModulePath.set(true)
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "9"
}
kotlin.sourceSets["main"].apply { kotlin.srcDir("src") }
sourceSets["main"].apply { java.srcDir("src") }
sourceSets["test"].apply { java.srcDir("test") }
