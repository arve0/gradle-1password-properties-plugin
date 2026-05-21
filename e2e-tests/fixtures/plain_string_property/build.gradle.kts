plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

val MY_PROP = project.property("MY_PROP")

tasks.register("printProp") {
    doLast {
        println("MY_PROP=$MY_PROP")
    }
}
