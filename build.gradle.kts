import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar
import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.tasks.JarJar
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.spongepowered.asm.gradle.plugins.MixinExtension

buildscript {
    dependencies {
        classpath("org.spongepowered:mixingradle:0.7.38")
    }
}

plugins {
    eclipse
    idea
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("net.minecraftforge.gradle") version "6.+"
    id("org.parchmentmc.librarian.forgegradle") version "1.+"
}

apply(plugin = "org.spongepowered.mixin")

evaluationDependsOnChildren()

val mod_version: String by project
val mod_group_id: String by project
val minecraft_version: String by project
val forge_version: String by project

val shadow: Configuration by configurations.creating

jarJar.enable()

group = mod_group_id
version = "${minecraft_version}_${mod_version}"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

configurations {
    minecraftLibrary { extendsFrom(shadow) }
}

configure<UserDevExtension> {
    val mappingsChannel: String by project
    val parchmentVersion: String? by project
    if (mappingsChannel == "official") mappings(mappingsChannel, minecraft_version)
    else mappings(mappingsChannel, "${parchmentVersion!!}-${minecraft_version}")

    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    copyIdeResources.set(true)

    runs {
        create("client") {
            workingDirectory (project.file("run"))

            jvmArg("-XX:+AllowEnhancedClassRedefinition")
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("forge.enabledGameTestNamespaces", "Inscribers")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${buildDir}/createSrgToMcp/output.srg")
            arg("-mixin.config=inscribers.mixins.json")

            mods {
                create("inscribers") {
                    sources(the<JavaPluginExtension>().sourceSets.getByName("main"))
                }
            }
        }

        create("server") {
            workingDirectory (project.file("run"))

            jvmArg("-XX:+AllowEnhancedClassRedefinition")
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("forge.enabledGameTestNamespaces", "Inscribers")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${buildDir}/createSrgToMcp/output.srg")
            arg("-mixin.config=inscribers.mixins.json")

            mods {
                create("inscribers") {
                    sources(the<JavaPluginExtension>().sourceSets.getByName("main"))
                }
            }
        }

        create("data") {
            workingDirectory (project.file("run"))

            jvmArg("-XX:+AllowEnhancedClassRedefinition")
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            args("--mod", "inscribers", "--all", "--output", file("src/generated/resources/"), "--existing", file("src/main/resources/"))

            mods {
                create("inscribers") {
                    sources(the<JavaPluginExtension>().sourceSets.getByName("main"))
                }
            }
        }
    }
}

configure<MixinExtension> {
    add(sourceSets.main.get(), "inscribers.refmap.json")
    config("inscribers.mixins.json")
}

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com")
    maven("https://modmaven.dev")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    minecraft("net.minecraftforge:forge:${minecraft_version}-${forge_version}")

    val jei_version: String by project
    val craftTweakerVersion: String by project
    val coroutinesVersion: String by project
    val serializationVersion: String by project
    val kffVersion: String by project

    jarJar(fg.deobf("thedarkcolour:kotlinforforge:${kffVersion}")) {
        jarJar.ranged(this, "[$kffVersion,)")
    }

    runtimeOnly(fg.deobf("thedarkcolour:kotlinforforge:${kffVersion}"))
    compileOnly(fg.deobf("com.blamejared.crafttweaker:CraftTweaker-${minecraft_version}:${craftTweakerVersion}"))
    compileOnly(fg.deobf("mezz.jei:jei-${minecraft_version}:${jei_version}:api"))
    runtimeOnly(fg.deobf("mezz.jei:jei-${minecraft_version}:${jei_version}"))

    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    implementation(kotlin("stdlib-jdk8"))
}

tasks {
    withType<Jar> {
        from(sourceSets.main.get().output)
        manifest {
            attributes(
                mapOf(
                    "Specification-Title" to "inscribers",
                    "Specification-Vendor" to "AlgorithmLX",
                    "Specification-Version" to "1",
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to version,
                    "Implementation-Timestamp" to ZonedDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")),
                    "MixinConfigs" to "inscribers.mixins.json"
                )
            )
        }
        finalizedBy("reobfJar")
    }

    withType<ReobfuscateJar> {
        jarJar
    }

    withType<JarJar> {
        from(provider { shadow.map(::zipTree).toTypedArray() })
        finalizedBy("reobfJarJar")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

fun DependencyHandler.kotlinx(module: String, version: String? = null): Any = "org.jetbrains.kotlinx:kotlinx-$module${version?.let { ":$version" } ?: ""}"
