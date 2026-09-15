// Root build file. Intentionally minimal: plugin versions are declared per-module
// so that the engine module never resolves the Android Gradle Plugin.
tasks.register("threadInfo") {
    group = "thread"
    description = "Prints which modules are participating in this build."
    doLast {
        println("Thread modules: " + subprojects.joinToString(", ") { it.path })
    }
}
