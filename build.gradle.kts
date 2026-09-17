plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}

tasks.register("threadInfo") {
    group = "thread"
    description = "Prints which modules are participating in this build."
    doLast {
        println("Thread modules: " + subprojects.joinToString(", ") { it.path })
    }
}
