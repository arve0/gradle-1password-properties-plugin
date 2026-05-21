plugins {
    id("io.github.arve0.1password.properties")
}

tasks.register("printTokenFromConvention") {
    val token = onePassword.property("TOKEN")
    doLast {
        println("TOKEN=${token.get()}")
    }
}
