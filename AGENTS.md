# AGENTS.md

## Scope
- This repository is an Architectury multi-project mod with two active subprojects: `:common` and `:fabric` (`settings.gradle`).
- Treat `common/` and `fabric/` as the authoritative sources for the current build.
- If any legacy root-level sources exist, do not assume they are wired into the active Gradle build unless `build.gradle` or `settings.gradle` says so.

## Big-Picture Architecture
- Shared gameplay logic lives in `common/src/main/java/dev/albercl/conquestmod/common/`.
- Fabric bootstrap lives in `fabric/src/main/java/dev/albercl/conquestmod/fabric/ConquestModFabric.java` and calls `ConquestMod.init()` from `:common`.
- Client-only Fabric setup lives in `fabric/src/main/java/dev/albercl/conquestmod/fabric/client/ConquestModFabricClient.java`.
- Registration flow is explicit: `ConquestMod.init()` -> `ConquestPokeBalls.register()` -> `ConquestItems.register()`.
- Custom Poke Balls clone Cobblemon Cherish Ball behavior in `ConquestPokeBalls.createFromCherishBall(...)`.
- Cobblemon registry integration uses reflection against `PokeBalls.defaults` in `ConquestPokeBalls.registerInCobblemonRegistry(...)`; this is version-sensitive.

## Build, Run, and Debug Workflows
- Use Java 21 for local builds.
- Primary build command:
  - `./gradlew build`
- Useful module-scoped commands:
  - `./gradlew :common:build :fabric:build`
  - `./gradlew :fabric:runClient`
  - `./gradlew :fabric:runServer`
- Packaging is Fabric-specific shadow/remap:
  - `:fabric:shadowJar` -> `:fabric:remapJar` (`fabric/build.gradle`).
- If you need a clean verification pass, prefer `./gradlew clean build`.

## Project-Specific Conventions
- The active mod id is `conquest_mod` (`ConquestMod.MOD_ID` and `fabric/src/main/resources/fabric.mod.json`).
- Resource paths use `assets/conquest_mod/...` everywhere, not a hyphenated path.
- Keep IDs aligned across:
  - `common/src/main/java/dev/albercl/conquestmod/common/ConquestItems.java`
  - `common/src/main/java/dev/albercl/conquestmod/common/ConquestPokeBalls.java`
  - `common/src/main/resources/assets/conquest_mod/lang/en_us.json`
  - `common/src/main/resources/assets/conquest_mod/models/item/*.json`
- When adding shared gameplay behavior, implement it in `:common` first and then expose it through the Fabric entrypoints.
- Keep resource/model filenames and registry IDs in sync; each Poke Ball has both an item JSON and a model JSON in `common/src/main/resources/assets/conquest_mod/models/item/`.

## Integration Points and Risks
- External dependencies come from Fabric, Architectury, and Cobblemon (`build.gradle`, `gradle.properties`).
- Cobblemon registry writes are reflective and can break when Cobblemon internals change.
- `fabric.mod.json` currently references the active package names `dev.albercl.conquestmod.fabric...` and `dev.albercl.conquestmod.fabric.client...`; keep them aligned with source packages.
- There are no repo-local `src/test` trees in `:common` or `:fabric`; `test` tasks exist, but the project does not currently ship its own tests.

## Existing AI Instructions Scan
- Required glob scan (`**/{.github/copilot-instructions.md,AGENT.md,AGENTS.md,CLAUDE.md,.cursorrules,.windsurfrules,.clinerules,.cursor/rules/**,.windsurf/rules/**,.clinerules/**,README.md}`) returned no additional instruction files at generation time.

