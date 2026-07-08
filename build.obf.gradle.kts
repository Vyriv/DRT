plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
	archivesName.set("DungeonRunTracker")
}

repositories {
	mavenCentral()
}

loom {
	splitEnvironmentSourceSets()

	mods {
		create("drt") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
	"mappings"(loom.officialMojangMappings())
	"modImplementation"("net.fabricmc:fabric-loader:${project.property("loader_version")}")
	"modImplementation"("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set((project.property("java_version") as String).toInt())
}

java {
	val javaVersionNumber = project.property("java_version").toString().toInt()
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(javaVersionNumber))
	}
	val javaVersion = JavaVersion.toVersion(javaVersionNumber)
	sourceCompatibility = javaVersion
	targetCompatibility = javaVersion
	withSourcesJar()
}

tasks {
	processResources {
		val props = mapOf(
			"version" to project.version,
			"loader" to project.property("loader_dependency"),
			"minecraft" to project.property("minecraft_dependency"),
			"java" to project.property("java_dependency")
		)
		inputs.properties(props)

		filesMatching("fabric.mod.json") {
			expand(props)
		}
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds this version and copies jars to dist."
		dependsOn("build")
		from(named("remapJar"), named("remapSourcesJar"))
		into(rootProject.layout.projectDirectory.dir("dist"))
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}
