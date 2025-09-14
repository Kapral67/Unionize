import org.jreleaser.model.Active

plugins {
    base
    alias(libs.plugins.jreleaser)
    `maven-publish`
}

allprojects {
    group = "com.maxkapral.annotations"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

val libraries = mutableListOf<String>()

subprojects {
    if ("test" in project.name) {
        return@subprojects
    }

    libraries.add(project.name)

    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
        withJavadocJar()
    }
}

fun TaskContainer.createFatJarTask(
    jarTaskName: String,
    classifier: String? = null
) = register<Jar>(jarTaskName) {
    archiveBaseName.set(project.name)
    archiveVersion.set(project.version.toString())
    classifier?.let { archiveClassifier.set(it) }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    libraries.forEach { projectPath ->
        val subproject = project(projectPath)
        val jarProvider = subproject.tasks.named<Jar>(jarTaskName)
        from (jarProvider.map { zipTree(it.archiveFile) })
    }
}

val jar by tasks.createFatJarTask("jar")
val sourcesJar by tasks.createFatJarTask("sourcesJar", "sources")
val javadocJar by tasks.createFatJarTask("javadocJar", "javadoc")

tasks.named("build") {
    dependsOn(jar, sourcesJar, javadocJar)
}

tasks.withType<AbstractPublishToMaven>() {
    dependsOn(jar, sourcesJar, javadocJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = project.name.lowercase()

            artifact(jar)
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name = project.name
                description = "Polymorphism for Unrelated Objects"
                url = "https://github.com/Kapral67/Unionize"
                inceptionYear = "2025"
                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://spdx.org/licenses/Apache-2.0.html"
                    }
                }
                developers {
                    developer {
                        id = "Kapral67"
                        name = "Max Kapral"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/Kapral67/Unionize.git"
                    developerConnection = "scm:git:ssh://git@github.com:Kapral67/Unionize.git"
                    url = "https://github.com/Kapral67/Unionize"
                }
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    val configs = mapOf("api" to true, "implementation" to false, "runtimeOnly" to false)
                    val dependencies = mutableMapOf<Dependency, Boolean>()

                    libraries.forEach libs@ { lib ->
                        val subproject = project(lib)

                        configs.forEach { cfg ->
                            val config = subproject.configurations.findByName(cfg.key) ?: return@libs

                            config.dependencies.forEach deps@ { dep ->
                                if (dep.group == subproject.group.toString() && dep.name in libraries) {
                                    return@deps
                                }

                                val isExistingCoordinateCompile = dependencies[dep];
                                if (isExistingCoordinateCompile == null || (cfg.value && !isExistingCoordinateCompile)) {
                                    dependencies[dep] = cfg.value
                                }
                            }
                        }
                    }

                    dependencies.forEach { dep ->
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dep.key.group)
                        dependencyNode.appendNode("artifactId", dep.key.name)
                        dependencyNode.appendNode("version", dep.key.version)
                        dependencyNode.appendNode("scope", if (dep.value) "compile" else "runtime")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
}

jreleaser {
    release {
        github {
            repoOwner.set("Kapral67")
            overwrite.set(true)
        }
    }
    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
        setMode("MEMORY")
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}

tasks.matching { it.name.startsWith("jreleaser") }.configureEach {
    dependsOn(tasks.named("publish"))
}
