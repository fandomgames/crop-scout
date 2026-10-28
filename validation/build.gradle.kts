// Golden-tray validation tool: plain JVM module (no Android) that runs SAM
// over golden-photos/ from a Mac and writes golden-validation.json.
// It compiles a subset of :app's analyze sources directly (source sharing,
// not a binary dependency), so the tool always measures with the exact code
// the app ships. The frozen SAM files are included read-only — this module
// must never carry its own copies.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include(
            "com/farrah/sproutvision/analyze/MetaSamSegmenter.kt",
            "com/farrah/sproutvision/analyze/SamClient.kt",
            "com/farrah/sproutvision/analyze/SamMaskCodec.kt",
            "com/farrah/sproutvision/analyze/SamSegmenter.kt",
            "com/farrah/sproutvision/analyze/Segmentation.kt",
            "com/farrah/sproutvision/analyze/Segmenter.kt",
            "com/farrah/sproutvision/analyze/AnalysisResult.kt",
            "com/farrah/sproutvision/data/GrowthStage.kt",
            // This module's own sources (the include filter above would
            // otherwise drop them; the stub lives outside the app tree
            // so the real Android-bound MetaAnalyzer never compiles here).
            "validation/**",
            "stub/**"
        )
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

// Usage: META_API_KEY='...' ./gradlew :validation:validateGolden
// Overrides: -PgoldenPhotos=<dir> -PgoldenOut=<golden-validation.json path>
//   -PgoldenMasks=<mask PNG dir> (default validation/build/masks)
//   -PgoldenConcept=<plant concept> (default microgreens; A/B only —
//   write A/B runs to -PgoldenOut=/tmp/... so the committed JSON stays
//   on the default concept the app ships)
tasks.register<JavaExec>("validateGolden") {
    group = "verification"
    description = "Runs SAM 3.1 over golden-photos/ and writes golden-validation.json."
    mainClass.set("validation.GoldenValidationKt")
    classpath = sourceSets["main"].runtimeClasspath
    val photosDefault =
        project.layout.projectDirectory.dir("../golden-photos").asFile.absolutePath
    val outDefault = project.layout.projectDirectory
        .dir("../app/src/main/assets/golden-validation.json").asFile.absolutePath
    val masksDefault = project.layout.buildDirectory.dir("masks").get().asFile.absolutePath
    // Must match samConcept(growOut = false); duplicated because a
    // Gradle script cannot call app code (main() falls back to the real
    // default when run without this arg).
    val conceptDefault = "microgreens"
    args(
        (findProperty("goldenPhotos") as String?) ?: photosDefault,
        (findProperty("goldenOut") as String?) ?: outDefault,
        (findProperty("goldenMasks") as String?) ?: masksDefault,
        (findProperty("goldenConcept") as String?) ?: conceptDefault
    )
}
