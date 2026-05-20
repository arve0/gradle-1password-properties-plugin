plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

tasks.register("printToken") {
    val op = project.extensions.getByType(io.github.arve0.onepassword.properties.OnePasswordExtension::class.java)
    val token = op.property("TOKEN")
    doLast {
        println("TOKEN=${token.get()}")
    }
}
