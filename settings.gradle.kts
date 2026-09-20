pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "thread"

// The engine is pure Kotlin/JVM and always builds.
include(":engine")

// The Android app only participates in the build when an SDK is present,
// so the engine stays testable on machines without the Android toolchain.
val androidSdkAvailable =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkAvailable) {
    // The accessibility layer, Tier 1: observes any app, zero integration.
    include(":app")

    // Tier 2: what an app includes to report semantic events instead of being
    // inferred from a node tree. Kept deliberately tiny - see sdk/README.
    include(":sdk")

    // The two apps used in the demo. Both exist so the whole story can be told
    // without depending on a third-party app behaving a particular way on stage.
    include(":demo")
    include(":lookup")
    include(":worddemo")
} else {
    logger.lifecycle("[thread] Android SDK not found - skipping Android modules. Engine still builds and tests.")
}
