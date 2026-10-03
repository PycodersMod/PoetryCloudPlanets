import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import java.nio.charset.StandardCharsets
import java.util.Base64

plugins {
    java
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

fun prop(name: String): String = providers.gradleProperty(name).get()

val minecraftVersion = prop("minecraft_version")
val minecraftVersionRange = prop("minecraft_version_range")
val forgeVersion = prop("forge_version")
val forgeVersionRange = prop("forge_version_range")
val javaVersionRange = prop("java_version_range")
val modId = prop("mod_id")
val modName = prop("mod_name")
val modVersion = prop("mod_version")
val modGroupId = prop("mod_group_id")
val modAuthors = prop("mod_authors")
val modDescription = prop("mod_description")
val modLicense = prop("mod_license")
val pcpUsername = providers.gradleProperty("pcpUsername").orNull
val pcpRunDir = providers.gradleProperty("pcpRunDir").orNull
val pycodersRuntimeProjectId = providers.gradleProperty("pycodersRuntimeProjectId").orElse(rootProject.name).get()
val pycodersConfiguredRunDir = providers.gradleProperty("pycodersRuntimeDir").orNull?.let { file(it).canonicalFile }
val pycodersConfiguredRuntimeRoot = providers.gradleProperty("pycodersRuntimeRoot").orNull
    ?: providers.environmentVariable("MMTL_WORKSPACE_RUNTIME_ROOT").orNull
val pycodersConfiguredRootRunDir = pycodersConfiguredRuntimeRoot?.let { File(it, "legacy-import/$pycodersRuntimeProjectId/run").canonicalFile }
val pycodersDiscoveredRunDir = generateSequence(project.projectDir.canonicalFile) { it.parentFile }
    .map { File(it, "runtime/legacy-import/$pycodersRuntimeProjectId/run").canonicalFile }
    .firstOrNull { it.isDirectory }
val pycodersRunDir = pycodersConfiguredRunDir ?: pycodersConfiguredRootRunDir ?: pycodersDiscoveredRunDir ?: file("run").canonicalFile
fun decodeArgs(name: String): List<String> = providers.gradleProperty(name).orNull?.takeIf { it.isNotEmpty() }?.split('.')?.map { if (it == "_") "" else String(Base64.getDecoder().decode(it), StandardCharsets.UTF_8) } ?: emptyList()
val pycodersGameArgs = decodeArgs("pycodersGameArgsB64")
val pycodersJavaArgs = decodeArgs("pycodersJavaArgsB64")
val pycodersUsername = providers.gradleProperty("pycodersUsername").orElse(pcpUsername ?: "Dev").get()

group = modGroupId
version = modVersion

base {
    archivesName.set(modId)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")
}

minecraft {
    mappings("official", minecraftVersion)

    runs {
        create("client") {
            workingDirectory(pycodersRunDir)
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            args("--username", pycodersUsername)
            pycodersGameArgs.forEach { args(it) }
            pycodersJavaArgs.forEach { jvmArg(it) }
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("server") {
            workingDirectory(pycodersRunDir)
            pycodersGameArgs.forEach { args(it) }
            pycodersJavaArgs.forEach { jvmArg(it) }
            arg("nogui")
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create(modId) {
                    source(sourceSets.main.get())
                }
            }
        }

        create("data") {
            workingDirectory(pycodersRunDir)
            args(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources/"),
                "--existing", file("src/main/resources/")
            )
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

val resourceProperties = mapOf(
    "mod_id" to modId,
    "mod_name" to modName,
    "mod_version" to modVersion,
    "mod_authors" to modAuthors,
    "mod_description" to modDescription,
    "mod_license" to modLicense,
    "minecraft_version" to minecraftVersion,
    "minecraft_version_range" to minecraftVersionRange,
    "forge_version" to forgeVersion,
    "forge_version_range" to forgeVersionRange,
    "java_version_range" to javaVersionRange
)

tasks.named<Copy>("processResources") {
    inputs.properties(resourceProperties)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(resourceProperties)
    }
}
