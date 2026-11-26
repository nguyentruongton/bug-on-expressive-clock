import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.compose") version "1.13.0-rc01"
}

group = "app.expressiveclock"
version = "1.0.0"

val upstreamJavaDir = file("../app/src/main/java")
val upstreamResDir = file("../app/src/main/res")
val generatedResKotlinDir = layout.buildDirectory.dir("generated/desktopRes/kotlin")
val syncedUpstreamKotlinDir = layout.buildDirectory.dir("generated/upstream/kotlin")

val syncUpstreamSources by tasks.registering(Sync::class) {
    from(upstreamJavaDir)
    into(syncedUpstreamKotlinDir)

    // Layer 1: Hard Excludes (Android-only components)
    exclude("app/expressiveclock/MainActivity.kt")
    exclude("app/expressiveclock/ClockApplication.kt")
    exclude("app/expressiveclock/ActivityOrientationPolicy.kt")
    exclude("app/expressiveclock/LauncherName.kt")
    exclude("app/expressiveclock/alarm/**")
    exclude("app/expressiveclock/overlay/**")
    exclude("app/expressiveclock/widget/**")
    exclude("app/expressiveclock/quicksettings/**")
    exclude("app/expressiveclock/data/CredentialStorage.kt")
    exclude("app/expressiveclock/data/WidgetConfig.kt")
    exclude("app/expressiveclock/data/WidgetPreset.kt")
    exclude("app/expressiveclock/ui/screens/AppIconNameDialog.kt")
    exclude("app/expressiveclock/ui/screens/CalibrationProfileGuidance.kt")
    exclude("app/expressiveclock/ui/screens/OnboardingScreen.kt")
    exclude("app/expressiveclock/ui/sheets/SettingsSheets.kt")

    // Layer 1: Shadow Excludes (replaced in desktop/src/main/kotlin)
    exclude("app/expressiveclock/ui/ClockViewModel.kt")
    exclude("app/expressiveclock/data/ClockStore.kt")
    exclude("app/expressiveclock/ui/theme/Theme.kt")
    exclude("app/expressiveclock/ui/Glass.kt")
    exclude("app/expressiveclock/ui/screens/SettingsScreen.kt")
    exclude("app/expressiveclock/ui/TimeFormat.kt")
    exclude("app/expressiveclock/ui/responsive/AppWindowSizeInfo.kt")
    exclude("app/expressiveclock/ui/sheets/AlarmSoundPage.kt")
}

val generateDesktopResources by tasks.registering {
    val stringsXml = file("../app/src/main/res/values/strings.xml")
    val fontDir = file("../app/src/main/res/font")
    val rawDir = file("../app/src/main/res/raw")
    val drawableDir = file("../app/src/main/res/drawable")
    val drawableNodpiDir = file("../app/src/main/res/drawable-nodpi")
    val outDir = generatedResKotlinDir

    inputs.file(stringsXml).optional()
    inputs.dir(fontDir).optional()
    inputs.dir(rawDir).optional()
    inputs.dir(drawableDir).optional()
    inputs.dir(drawableNodpiDir).optional()
    outputs.dir(outDir)

    doLast {
        val outputFile = outDir.get().asFile.resolve("app/expressiveclock/R.kt")
        outputFile.parentFile.mkdirs()

        val stringEntries = linkedMapOf<String, String>()
        if (stringsXml.exists()) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXml)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                val value = node.textContent
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                stringEntries[name] = value
            }
        }

        fun collectDir(dir: File, prefix: String): LinkedHashMap<String, String> {
            val map = linkedMapOf<String, String>()
            if (dir.exists()) {
                dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    if (f.isFile) {
                        val key = f.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_]"), "_")
                        map.putIfAbsent(key, "$prefix/${f.name}")
                    }
                }
            }
            return map
        }

        val fontEntries = collectDir(fontDir, "font")
        val rawEntries = collectDir(rawDir, "raw")
        val drawableEntries = linkedMapOf<String, String>().apply {
            putAll(collectDir(drawableDir, "drawable"))
            putAll(collectDir(drawableNodpiDir, "drawable-nodpi"))
        }

        var nextId = 0x7f010000
        fun esc(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("$", "\\$")

        val sb = StringBuilder()
        sb.appendLine("package app.expressiveclock")
        sb.appendLine()
        sb.appendLine("@Suppress(\"ConstPropertyName\", \"unused\")")
        sb.appendLine("object R {")

        sb.appendLine("    object string {")
        val stringIdMap = linkedMapOf<Int, String>()
        stringEntries.forEach { (k, v) ->
            val id = nextId++
            stringIdMap[id] = v
            sb.appendLine("        const val $k: Int = $id")
        }
        sb.appendLine("    }")

        sb.appendLine("    object font {")
        val fontIdMap = linkedMapOf<Int, String>()
        fontEntries.forEach { (k, v) ->
            val id = nextId++
            fontIdMap[id] = v
            sb.appendLine("        const val $k: Int = $id")
        }
        sb.appendLine("    }")

        sb.appendLine("    object raw {")
        val rawIdMap = linkedMapOf<Int, String>()
        rawEntries.forEach { (k, v) ->
            val id = nextId++
            rawIdMap[id] = v
            sb.appendLine("        const val $k: Int = $id")
        }
        sb.appendLine("    }")

        sb.appendLine("    object drawable {")
        val drawableIdMap = linkedMapOf<Int, String>()
        drawableEntries.forEach { (k, v) ->
            val id = nextId++
            drawableIdMap[id] = v
            sb.appendLine("        const val $k: Int = $id")
        }
        sb.appendLine("    }")
        sb.appendLine("}")
        sb.appendLine()

        sb.appendLine("object DesktopResources {")
        sb.appendLine("    val strings: Map<Int, String> = mapOf(")
        stringIdMap.forEach { (id, v) -> sb.appendLine("        $id to \"${esc(v)}\",") }
        sb.appendLine("    )")
        sb.appendLine("    val fonts: Map<Int, String> = mapOf(")
        fontIdMap.forEach { (id, v) -> sb.appendLine("        $id to \"${esc(v)}\",") }
        sb.appendLine("    )")
        sb.appendLine("    val raws: Map<Int, String> = mapOf(")
        rawIdMap.forEach { (id, v) -> sb.appendLine("        $id to \"${esc(v)}\",") }
        sb.appendLine("    )")
        sb.appendLine("    val drawables: Map<Int, String> = mapOf(")
        drawableIdMap.forEach { (id, v) -> sb.appendLine("        $id to \"${esc(v)}\",") }
        sb.appendLine("    )")
        sb.appendLine("}")

        outputFile.writeText(sb.toString())
    }
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")
            srcDir(generatedResKotlinDir)
            srcDir(syncedUpstreamKotlinDir)
        }
        resources {
            srcDir("src/main/resources")
            srcDir(upstreamResDir)
            include("font/**", "raw/**", "drawable/**", "drawable-nodpi/**")
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateDesktopResources, syncUpstreamSources)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
}

dependencies {
    val cmpVersion = "1.13.0-rc01"
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.runtime:runtime:$cmpVersion")
    implementation("org.jetbrains.compose.foundation:foundation:$cmpVersion")
    implementation("org.jetbrains.compose.ui:ui:$cmpVersion")
    implementation("org.jetbrains.compose.material3:material3:$cmpVersion")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
    implementation(libs.androidx.graphics.shapes)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "app.expressiveclock.DesktopMainKt"
        jvmArgs(
            "-Xms32m",
            "-Xmx160m",
            "-XX:+UseSerialGC",
            "-Dskiko.vsync.enabled=true",
        )
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "expressive-clock"
            packageVersion = "1.0.0"
            includeAllModules = false
            modules("java.base", "java.desktop", "java.logging", "java.prefs", "jdk.unsupported")
            linux {
                iconFile.set(project.file("src/main/resources/icons/expressive-clock-512.png"))
            }
        }
    }
}

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    val buildDirPath = layout.buildDirectory.get().asFile.toPath().normalize()
    val stagingRoot = layout.buildDirectory.dir("tmp/runClasspath").get().asFile
    doFirst {
        if (stagingRoot.exists()) {
            stagingRoot.listFiles()?.forEach { dir ->
                val pid = dir.name.substringAfterLast('-', "").toLongOrNull()
                val alive = pid != null && ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
                if (!alive) dir.deleteRecursively()
            }
        }
        val runDir = File(stagingRoot, "${System.currentTimeMillis()}-${ProcessHandle.current().pid()}")
        runDir.mkdirs()
        val stagedFiles = classpath.files.map { entry ->
            if (entry.isFile && entry.extension == "jar" && entry.toPath().normalize().startsWith(buildDirPath)) {
                val target = File(runDir, entry.name)
                entry.copyTo(target, overwrite = true)
                target
            } else {
                entry
            }
        }
        classpath = files(stagedFiles)
    }
}
