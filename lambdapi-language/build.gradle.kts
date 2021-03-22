import org.gradle.api.internal.HasConvention
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = project.properties["group"].toString()
version = project.properties["version"].toString()

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61")
    }
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://dl.bintray.com/jannis/kotlin-pretty")
}

plugins {
    application
    java
    idea
    antlr
    kotlin("jvm") version "1.4.21"
    kotlin("kapt") version "1.4.21"
}

val compiler: Configuration by configurations.creating
val graalVersion = "20.2.0"

dependencies {
    implementation(kotlin("stdlib"))

    antlr("org.antlr:antlr4:4.8-1")
    api("org.antlr:antlr4-runtime:4.8-1")
    implementation("guru.nidi:graphviz-java:0.18.1")
    implementation("kotlin-pretty:kotlin-pretty:0.5.2")
    implementation("me.tomassetti:kllvm:0.1.0")

    testImplementation(platform("org.junit:junit-bom:5.7.0"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    arrayOf("asm", "asm-tree", "asm-commons").forEach { implementation("org.ow2.asm:$it:7.1") }
    implementation("org.fusesource.jansi:jansi:1.18")

    compiler("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.compiler:compiler:$graalVersion")
    implementation("org.graalvm.sdk:graal-sdk:$graalVersion")
    implementation("org.graalvm.sdk:launcher-common:$graalVersion")
    implementation("org.graalvm.truffle:truffle-api:$graalVersion")
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.1")
    testImplementation("org.graalvm.compiler:compiler:$graalVersion")
    kapt("org.graalvm.truffle:truffle-api:$graalVersion")
    kapt("org.graalvm.truffle:truffle-dsl-processor:$graalVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
    modularity.inferModulePath.set(true)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "9"
}

fun <R> SourceSet.kotlin(f: KotlinSourceSet.() -> R): R =
    ((this as HasConvention).convention.getPlugin(KotlinSourceSet::class.java)).f()

val SourceSet.kotlin: SourceDirectorySet get() = kotlin { kotlin }

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages")
}

sourceSets {
    main {
        java.srcDir("src/main")
        java.srcDir("build/generated-src/antlr/main")
        kotlin.srcDir("src/main")
    }
    test {
        kotlin.srcDirs("test")
    }
    val bench by creating {
        dependencies {
            "kaptBench"("org.openjdk.jmh:jmh-generator-annprocess:1.22")
        }
        java.srcDir("bench")
        kotlin.srcDir("bench")
        kotlin {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.openjdk.jmh:jmh-core:1.22")
            }
        }
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
        compileClasspath += configurations.runtimeClasspath.get()
        runtimeClasspath += configurations.runtimeClasspath.get()
    }
}

application {
    mainClassName = "lambdapi.Launcher"
    applicationDefaultJvmArgs = listOf(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+EnableJVMCI",
        "--module-path=${compiler.asPath}",
        "--upgrade-module-path=${compiler.asPath}",
        "-Dtruffle.class.path.append=@LAMBDAPI_APP_HOME@/lib/lambdapi-${project.version}.jar"
    )
}

var rootBuildDir = project.buildDir

val graalArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "--module-path=${compiler.asPath}",
    "--upgrade-module-path=${compiler.asPath}",
//  "-XX:-UseJVMCIClassLoader",
    "-Dgraalvm.locatorDisabled=true",
    "-Dtruffle.class.path.append=build/libs/lambdapi-${project.version}.jar",
    "--add-opens=jdk.internal.vm.compiler/org.graalvm.compiler.truffle.runtime=ALL-UNNAMED",
    "--add-opens=org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED",

    "-Dgraal.Dump=Truffle",
    "-Dgraal.PrintGraph=Network",
    "-Dgraal.CompilationFailureAction=ExitVM",
    "-Dgraal.TraceTruffleCompilation=true",
//  "-Dgraal.TraceTruffleSplitting=true",
//  "-Dgraal.TruffleTraceSplittingSummary=true",
    "-Dgraal.TraceTruffleAssumptions=true",
    "-Dgraal.TraceTruffleTransferToInterpreter=true",
    // limit size of graphs for easier visualization
    "-Dgraal.TruffleMaximumRecursiveInlining=0",
//  "-Dgraal.LoopPeeling=false",
    "-Xss32m"
)

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    dependsOn("jar")
    jvmArgs = graalArgs
}

tasks.register("bench", JavaExec::class) {
    dependsOn("benchClasses", "jar")
//  dependsOn(sourceSets["bench"].getJarTaskName())
    classpath = sourceSets["bench"].runtimeClasspath + sourceSets["bench"].compileClasspath
    main = "org.openjdk.jmh.Main"
    jvmArgs = graalArgs
}

tasks.getByName<Jar>("jar") {
    exclude("jre/**")
    exclude("META-INF/symlinks")
    exclude("META-INF/permissions")
    archiveBaseName.set("lambdapi")
    manifest {
        attributes["Main-Class"] = "lambdapi.Launcher"
        attributes["Class-Path"] =
            configurations.runtimeClasspath.get().files.joinToString(separator = " ") { it.absolutePath }
    }
}
