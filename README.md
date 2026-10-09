# ODM Gear & Titans (Fabric, Minecraft 1.20.1)

## Build the jar
Needs JDK 17+ and Gradle 8.6+ (or the Gradle bundled with IntelliJ IDEA).
    gradle build
Jar: build/libs/aotmod-1.0.0.jar

Fallback if the build setup gives you trouble:
1. Go to https://fabricmc.net/develop/template/ , pick Minecraft 1.20.1, mod id "aotmod".
2. Unzip it, delete its src/ folder, copy this project's src/ folder in, run its ./gradlew build.

## Install
PC: Fabric Loader 1.20.1 + Fabric API, put aotmod-1.0.0.jar in .minecraft/mods.
Aternos: Software = Fabric 1.20.1, add "Fabric API" in Mods, upload aotmod-1.0.0.jar, start the server.
Everyone needs the same jar (plus Fabric API) in their own mods folder to join.
If players get kicked for "flying", enable allow-flight in server properties.

## Play
Carry aotmod:odm_gear anywhere in your inventory, plus aotmod:odm_blade (craftable, or /give).
Hold R   fire both hooks (any block incl. leaves, or a titan)
Hold V   gas boost along your look direction
Sneak    reel in faster while hooked
Gas drains while boosting/reeling and refills on the ground.
Titans: Titan Spawn Egg or /summon aotmod:titan. Only blade hits to the nape (behind it, at neck height) kill it.
Ops: /aot trees <true|false>, /aot spawns <true|false>, /aot gas

## Tuning
Constants at the top of OdmManager.java. Titan stats in TitanEntity.createTitanAttributes().
