plugins {
    `embedded-kotlin`
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.vespene)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.plugin)
}

gradlePlugin {
    plugins {
        register("io.openfeedback.plugins.lib.multiplatform") {
            id = "io.openfeedback.plugins.lib.multiplatform"
            implementationClass = "io.openfeedback.plugins.MultiplatformPlugin"
        }
        register("io.openfeedback.plugins.publishing") {
            id = "io.openfeedback.plugins.publishing"
            implementationClass = "io.openfeedback.plugins.PublishingPlugin"
        }
        register("io.openfeedback.plugins.app") {
            id = "io.openfeedback.plugins.app"
            implementationClass = "io.openfeedback.plugins.AppPlugin"
        }
    }
}
