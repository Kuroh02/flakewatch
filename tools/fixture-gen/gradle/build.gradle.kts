plugins {
    java
    id("org.gradle.test-retry") version "1.6.2"
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
    retry {
        maxRetries.set(1)
        maxFailures.set(10)
    }
    ignoreFailures = true
}
