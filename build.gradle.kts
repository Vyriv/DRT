plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

val baseVersion = rootProject.property("mod_version") as String
val minecraftVersion = project.property("minecraft_version").toString()
version = "$baseVersion+$minecraftVersion"
group = project.property("maven_group") as String

base {
	archivesName.set("DungeonRunTracker")
}

repositories {
	mavenCentral()
	maven("https://maven.terraformersmc.com/releases/")
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

val nanoVgVersion = if (project.property("minecraft_version") == "26.3") "3.4.3" else "3.4.1"

dependencies {
	minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
	implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
	implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
	compileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
	implementation(include("org.lwjgl:lwjgl-nanovg:$nanoVgVersion")!!)
	runtimeOnly(include("org.lwjgl:lwjgl-nanovg:$nanoVgVersion:natives-windows")!!)
	runtimeOnly(include("org.lwjgl:lwjgl-nanovg:$nanoVgVersion:natives-linux")!!)
	runtimeOnly(include("org.lwjgl:lwjgl-nanovg:$nanoVgVersion:natives-macos")!!)
	runtimeOnly(include("org.lwjgl:lwjgl-nanovg:$nanoVgVersion:natives-macos-arm64")!!)
	testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
	named("test") {
		compileClasspath += sourceSets["client"].output
		runtimeClasspath += sourceSets["client"].output
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set((project.property("java_version") as String).toInt())
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

tasks.register<JavaExec>("benchmarkNameStyleMatcher") {
	group = "verification"
	description = "Runs the cosmetic name matcher microbenchmark."
	dependsOn("testClasses")
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("dev.vy.drt.client.cosmetics.NameStyleMatcherBenchmark")
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
		description = "Builds this version and copies release jars to dist/."
		dependsOn("build")
		from(named("jar"), named("sourcesJar"))
		into(rootProject.layout.projectDirectory.dir("dist"))
		exclude { details ->
			val name = details.file.name
			name.contains("-beta", ignoreCase = true) || name.startsWith("DRT_BETA-", ignoreCase = true)
		}
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}
