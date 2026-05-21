plugins {
    id("io.github.arve0.1password.properties") ${PLUGIN_VERSION_DECLARATION}
}

val MY_PROP = project.property("MY_PROP")
val MY_PROP_1P = onePassword.property("MY_PROP")

tasks.register("printProp") {
    doLast {
        println("MY_PROP=$MY_PROP")
    }
}

tasks.register("printProp1Password") {
    doLast {
        println("MY_PROP=${MY_PROP_1P.get()}")
    }
}

tasks.register("compareProp") {
    doLast {
        if (MY_PROP == MY_PROP_1P.get()) {
            println("MY_PROP is the same with project.property and onePassword.property")
        } else {
            println("MY_PROP != MY_PROP_1P: '$MY_PROP' != '${MY_PROP_1P.get()}'")
        }
    }
}

