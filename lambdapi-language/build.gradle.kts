plugins {
    kotlin("jvm") version "1.4.21"
    kotlin("kapt") version "1.4.21"
    `java-library`
    idea
}

repositories {
  jcenter()
  mavenCentral()
}

val graalVersion = "20.2.0"
val compiler: Configuration by configurations.creating

dependencies {
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.1")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))

    compiler("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.sdk:graal-sdk:$graalVersion")
    implementation("org.graalvm.sdk:launcher-common:$graalVersion")
    implementation("org.graalvm.truffle:truffle-api:$graalVersion")
    testImplementation("org.graalvm.compiler:compiler:$graalVersion")
    kapt("org.graalvm.truffle:truffle-api:$graalVersion")
    kapt("org.graalvm.truffle:truffle-dsl-processor:$graalVersion")
}
