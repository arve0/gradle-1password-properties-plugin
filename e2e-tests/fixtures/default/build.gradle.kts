plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

tasks.register("printToken") {
    val token = project.property("TOKEN") as org.gradle.api.provider.Provider<*>
    doLast {
        println("TOKEN=${token.get()}")
    }
}
