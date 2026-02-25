rootProject.plugins.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
    tasks.named<KotlinCompile>(compileTaskName).compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.2.1")
            debug.set(true)
        }
    }
}