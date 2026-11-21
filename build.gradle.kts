import com.cleanroommc.gradle.api.task.mc.RunMinecraft
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    id("com.cleanroommc.cleanroomgradle")
    id("com.cleanroommc.conventions") version "1.7.0"
    id("me.champeau.jmh") version "0.7.4"
}

fun prop(name: String): String = property(name) as String

fun String.tokens(delimiters: Regex = Regex("\\s+")): List<String> =
    split(delimiters).filter { it.isNotBlank() }

group = prop("root_package")

extensions.configure<BasePluginExtension> {
    archivesName.set(prop("mod_name"))
}

val modAuthors = prop("mod_authors")
val minecraftUsername = prop("minecraft_username")
val extraJvmArgs = prop("extra_jvm_args").tokens()
val accessTransformerNames = prop("access_transformers").tokens()
val dependOnHei = prop("depend_on_hei").toBoolean()
val curseforgeProjectId = prop("curseforge_project_id")
val modrinthProjectId = prop("modrinth_project_id")

conventions {
    if (modAuthors.isNotEmpty()) {
        author.set(modAuthors)
    }
    mods {
        if (curseforgeProjectId.isNotEmpty()) {
            curseforge {
                projectId.set(curseforgeProjectId)
                if (dependOnHei) {
                    requires("had-enough-items")
                } else {
                    optional("had-enough-items")
                }
            }
        }
        if (modrinthProjectId.isNotEmpty()) {
            modrinth {
                projectId.set(modrinthProjectId)
                if (dependOnHei) {
                    requires("had-enough-items")
                } else {
                    optional("had-enough-items")
                }
            }
        }
    }
}

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "CurseMaven"
                url = uri("https://curse.cleanroommc.com")
            }
        }
        filter {
            includeGroup("curse.maven")
        }
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "CleanroomMC"
                url = uri("https://maven.cleanroommc.com")
            }
        }
        filter {
            includeGroup("mezz")
        }
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(cleanroom.userdev(prop("cleanroom_version")) {
        accessTransformers.from(accessTransformerNames.map { "src/main/resources/META-INF/$it" })
    })
    compileOnly("org.jetbrains:annotations:26.1.0")
    if (dependOnHei) {
        implementation("mezz:jei:4.37.3:dev")
    } else {
        runtimeOnly("mezz:jei:4.37.3:dev")
    }
}

cleanroom {
    runs {
        maybeCreate("client").configure(RunMinecraft::class.java) {
            username.set(minecraftUsername)
            jvmArgs(extraJvmArgs)
        }
        maybeCreate("server").configure(RunMinecraft::class.java) {
            jvmArgs(extraJvmArgs)
        }
    }
}

tokenEnvoy {
    set("MOD_VERSION", project.version.toString())
    set("MOD_AUTHORS", modAuthors.tokens(Regex(",")).joinToString("\", \"") { it.trim() })
    set(file("tokens.properties"))
}

jmh {
    jmhVersion.set(prop("jmh_version"))
    threads.set(1)
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("test-results/jmh/jmh-results.json"))
}

tasks.withType<Javadoc>().configureEach {
    val doclet = options as StandardJavadocDocletOptions
    doclet.addBooleanOption("Xdoclint:none", true)
}

tasks.jar {
    manifest {
        if (modAuthors.isNotEmpty()) {
            attributes(mapOf("Implementation-Vendor" to prop("mod_authors").split(",").first()))
        }
        if (accessTransformerNames.isNotEmpty()) {
            attributes(mapOf("FMLAT" to accessTransformerNames.joinToString(" ")))
        }
        val coremodPluginClass = prop("coremod_plugin_class")
        if (coremodPluginClass.isNotEmpty()) {
            attributes(mapOf("FMLCorePlugin" to coremodPluginClass))
            if (prop("coremod_includes_mod").toBoolean()) {
                attributes(
                    mapOf(
                        "FMLCorePluginContainsFMLMod" to true,
                        "ForceLoadAsMod" to true
                    )
                )
            }
        }
    }
}

tasks.withType<Checkstyle>().configureEach {
    enabled = prop("enable_checkstyle").toBoolean()
}

tasks.matching { it.name in listOf("clearSkiesCheck", "formatJavaCheck") }.configureEach {
    enabled = prop("enable_formatting").toBoolean()
}

// TODO: fundamental Foundation issue
tasks.withType<JavaExec>().configureEach {
    systemProperty("foundation.loadsall", "true")
}
