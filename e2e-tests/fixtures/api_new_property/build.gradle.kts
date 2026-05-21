plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

tasks.register("printToken") {
    val token = onePassword.property("TOKEN")
    doLast {
        println("TOKEN=${token.get()}")
    }
}
