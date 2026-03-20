import org.gradle.plugin.compatibility.compatibility

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.0"
}

group = "io.github.arve0"
version = providers.gradleProperty("releaseVersion").orElse("dev-SNAPSHOT").get()

val githubPackagesUser = providers.gradleProperty("GITHUB_USER")
    .orElse(providers.gradleProperty("githubUser"))
    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
    .orElse(providers.environmentVariable("GITHUB_USER"))

val githubPackagesToken = providers.gradleProperty("GITHUB_TOKEN")
    .orElse(providers.gradleProperty("githubToken"))
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val functionalTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    add("functionalTestImplementation", gradleTestKit())
}

gradlePlugin {
    website = "https://github.com/arve0/gradle-1password-properties-plugin"
    vcsUrl = "https://github.com/arve0/gradle-1password-properties-plugin"
    plugins {
        register("1PasswordProperties") {
            id = "io.github.arve0.1password.properties"
            displayName = "1Password gradle properties with op:// paths"
            description = "A plugin that resolves op:// paths in gradle properties with 1Password CLI"
            tags = listOf("1password", "secrets")
            implementationClass = "io.github.arve0.onepassword.properties.OnePasswordGradlePropertiesPlugin"

            compatibility { // extension added by the Compatibility plugin
                features {
                    configurationCache = true // or false if the feature isn't supported
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests with Gradle TestKit."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    val runningInIde = providers.systemProperty("idea.active").map { true }.orElse(false)
    systemProperty("test.ide", runningInIde.get().toString())
    testLogging {
        if (!runningInIde.get()) {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }
}

tasks.check {
    dependsOn(functionalTestTask)
}

configure<org.gradle.api.publish.PublishingExtension> {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/arve0/gradle-1password-properties-plugin")
            credentials {
                username = githubPackagesUser.orNull
                password = githubPackagesToken.orNull
            }
        }
    }
}

