# Project Memory & Conventions

## Build Requirements

`./gradlew` fails with "Gradle requires JVM 17 or later ... currently configured to use JVM 8" unless `JAVA_HOME` points at a JDK 25. `flake.nix` provides one via `nix develop`; outside that shell, set it manually (a JDK 25 lives under `/nix/store/*openjdk-25*` on this machine).

**Before any gradle command in a fresh shell**, export `JAVA_HOME` to a JDK 25 and put its `bin` on `PATH` — or run inside `nix develop`.

## Package Names

The mod was renamed from `any-resource`/`dev.oery.anyresource` to `legacy-resources`/`dev.oery.legacyresources`. Current package root: `dev.oery.legacyresources`.

## Derivation Workflow

Deriving modern textures from a legacy pack's own art is an **ongoing series**, not a one-off — `TODO.md` tracks a queue of them (copper set, suspicious stew, path blocks, concrete powder, nuggets, breeze rods, recovery compass, soul fire/torch, etc.). Expect more requests like "set up a derivation for X".

### Adding a new texture derivation

1. Write a `Derivation` in `src/client/java/dev/oery/legacyresources/client/derive/` and add it to `Derivations.ALL` — that single line wires up both `LegacyPackResources` and the lab.
2. **`reference/1.8.9` and `reference/26.2` hold both eras' vanilla assets.** Diff them before writing any code: the transform is usually *learnable exactly* rather than something to eyeball. Vanilla's netherite is diamond palette-swapped; its 16 beds are one bed over a 7-stop ramp; bed UV rows are unchanged between eras. Reading models/blockstates out of `reference/` settles rotations and mirrors that would otherwise be guesswork.
3. Put every constant in `params()` as a `Param`, not inline, so the lab can sweep it.
4. **Fastest verify loop is not the lab.** The `derive/` package has no Minecraft dependency, so `javac` it standalone against the jspecify jar and run a `main` that derives from `reference/1.8.9` and scores against `reference/26.2` — seconds, versus a Gradle+Loom build. Point the same harness at the ~85 zips in `~/.minecraft/resourcepacks` to exercise the decline rules on real art.
5. Judge a recolour on real packs, not just the control: the failure that matters is a *partial* mask, which speckles. Prefer declining outright over a half-applied transform.
6. `./gradlew runLab` + `GET /api/verify?d=<id>` still matter for the serve/announce half, which the standalone harness cannot see. Paste tuned values back into the `Param` declarations.

### Lab reload caveat

A lab process started before the derivation existed reports it as serving nothing. The reloader recompiles `derive/` off disk, so `/api/derivations` lists the new one, but `/api/verify` goes through the real `LegacyPackResources`, whose `Derivations.ALL` is whatever was on the classpath at launch. A stale lab therefore shows 0 served for every pack, which looks exactly like a broken derivation. Restart it, or run a second on `-Plab.port=<n>` rather than killing one that is in use (`pkill -f LabServer` matches both).

## Conversion Verify Harness (models/blockstates)

For anything touching models/blockstates (as opposed to the texture derivations above), verify by running the **real** `LegacyPackResources` plus the **game's own loader** headlessly over the whole pack corpus, not by reasoning about the JSON:

- Dump the lab runtime classpath once with an init script (`allprojects { tasks.register('printLabCp') { doLast { println project.sourceSets.lab.runtimeClasspath.asPath } } }`, run as `./gradlew --offline -I <script> -q printLabCp`), then `javac`/`java` a scratch class against it. `--offline` works and a compile is ~1s.
- `SharedConstants.setVersion(DetectedVersion.BUILT_IN); Bootstrap.bootStrap();` then `LabPackAccess.openIfLegacy(new FilePackResources.FileResourcesSupplier(zip), info)` gets a converted pack; `LabPackAccess.convert(new PathPackResources(info, reference/1.8.9/assets))` gets vanilla 1.8.9 as a pack, which is the harshest input in the corpus (340 blockstates, 1595 models, all legacy-format).
- The metric that actually means "no pink cubes": parse each served blockstate with `BlockStateModelDispatcher.CODEC` and call `instantiate(block.getStateDefinition(), ...)`, then compare the returned map size against `getPossibleStates().size()`. Run it on the raw file too for a before/after.
- For sprites, compare model texture references against the set `listResources` announces for `textures/block`+`textures/item` (over **every** namespace, the mod adds its own) — not against file existence. Only announced names enter the atlas, and `getResource` answers for more names than listing announces.

**Why:** the failure mode is silent — a model or sprite the game cannot resolve renders as vanilla's art or a pink cube with nothing in the log, and reading the JSON cannot tell you which. Driving the real codecs turned "these look right" into 31 → 0 broken blockstates on PureBDcraft and 251 → 0 on vanilla 1.8.9's own tree.

**Keep the harness in the scratchpad (it is throwaway), but re-run it across all ~85 zips in `~/.minecraft/resourcepacks` before calling any conversion change done** — regressions show up as one pack, not as a compile error.
