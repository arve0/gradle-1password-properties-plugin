plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

// The secret is resolved in doLast and written to a file — it never
// appears on stdout, so it does not end up in Gradle daemon logs.
tasks.register("useToken") {
    val token = onePassword.property("TOKEN")
    val outputFile = layout.buildDirectory.file("used-token.txt")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.also { it.parentFile.mkdirs() }.writeText(token.get())
    }
}
