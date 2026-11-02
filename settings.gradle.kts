pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/")
		maven("https://maven.kikugie.dev/snapshots")
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9"
}

rootProject.name = "DungeonRunTracker"

stonecutter.create(rootProject) {
	version("26.2").buildscript = "build.gradle.kts"
	version("26.3").buildscript = "build.gradle.kts"
	vcsVersion = "26.2"
}
