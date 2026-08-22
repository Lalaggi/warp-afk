plugins {
    java
}

group = "com.warpmc.afk"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.purpurmc.org/snapshots") {
        name = "purpur"
    }
    mavenLocal()
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:26.1.2.local-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.name()
    options.release = 25
}

tasks.processResources {
    val props = mapOf<String, String>(
        "version" to project.version.toString(),
        "apiVersion" to (project.findProperty("apiVersion")?.toString() ?: "26.1.2")
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.register<Jar>("buildPluginJar") {
    archiveBaseName = "warp-afk"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(sourceSets.main.get().resources)
    destinationDirectory = file("build/libs")
    dependsOn(tasks.processResources, tasks.compileJava)
}

tasks.build {
    dependsOn("buildPluginJar")
}
