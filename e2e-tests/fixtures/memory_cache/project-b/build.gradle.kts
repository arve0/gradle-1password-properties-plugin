plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

// .get() called during configuration phase
val tokenValue = onePassword.property("TOKEN").get()

tasks.register("printToken") {
    doLast {
        println("TOKEN=$tokenValue")
    }
}
