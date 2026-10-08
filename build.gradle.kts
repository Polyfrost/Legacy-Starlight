plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT"
    id("ploceus") version "1.17.4"
}


fun prop(name: String) = property(name) as String

version = "${prop("mod_version")}+${prop("minecraft_version")}"
group = prop("maven_group")
base.archivesName = prop("mod_id")

ploceus {
    setIntermediaryGeneration(2)
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

repositories {
    mavenCentral()
    maven("https://maven.cloverclient.com/releases") {
        content { includeGroup("pl.tomgirl") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    mappings(ploceus.layeredMappings {
        mappings("net.ornithemc:feather-gen2:${prop("minecraft_version")}+build.${prop("feather_build")}:v2")
    })
    modImplementation("net.fabricmc:fabric-loader:${prop("loader_version")}")

    testImplementation("net.fabricmc:fabric-loader-junit:${prop("loader_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    runConfigs.all {
        preferGradleTask = true
        runDirectory = file("run")
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 8
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf(
            "mod_id" to prop("mod_id"),
            "mod_name" to prop("mod_name"),
            "mod_version" to prop("mod_version"),
            "minecraft_version" to prop("minecraft_version"),
            "loader_version" to prop("loader_version")
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
    }

    jar {
        from("LICENSE") { rename { "${it}_starlight" } }
    }
}
