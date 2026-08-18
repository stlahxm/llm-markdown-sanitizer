plugins {
    `java-library`
    `maven-publish`
}

group = "com.github.stlahxm"
version = "0.2.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Zero runtime dependencies is a hard project constraint -- fail the build
// if anything ever ends up on the main compile/runtime classpath.
tasks.register("checkNoRuntimeDependencies") {
    doLast {
        val runtimeDeps = configurations.getByName("runtimeClasspath").allDependencies
            .filterNot { it.group == null && it.name == "main" }
        if (runtimeDeps.isNotEmpty()) {
            throw GradleException(
                "llm-markdown-sanitizer-java must have zero runtime dependencies, found: " +
                    runtimeDeps.joinToString { "${it.group}:${it.name}:${it.version}" }
            )
        }
    }
}

tasks.check {
    dependsOn("checkNoRuntimeDependencies")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("llm-markdown-sanitizer-java")
                description.set("Fix broken markdown that LLMs generate -- tables, lists, emphasis, code fences. Zero dependencies, one method.")
                url.set("https://github.com/stlahxm/llm-markdown-sanitizer-java")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
