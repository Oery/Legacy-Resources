# Legacy entity model oracle

This development harness compares final submitted model-space quads from the official launcher-installed Minecraft 1.8.9 client with the current production models. It is headless: it replaces the fixed-function matrix stack and `ModelRenderer` submission before any display list or native OpenGL call.

It never downloads Minecraft. Capture requires these local files:

- `~/.minecraft/versions/1.8.9/1.8.9.jar`
- `~/.minecraft/versions/1.8.9/1.8.9.json`
- every applicable artifact named by that JSON under `~/.minecraft/libraries`
- launcher Java 8 under `~/.minecraft/runtime/jre-legacy`, or `-PlegacyOracle.java8=/path/to/java`

The client and libraries are SHA-1 checked against the version JSON. The client must additionally match the pinned official hash in `symbols-1.8.9.properties`. Unknown or modified inputs are rejected.

The committed catalog currently covers ocelots, wolves, the horse family, cats, dogs, chickens, pigs, cows, rabbits, and bats. Every ageable family has adult and baby controls plus animated/head-pose cases. Bat captures cover flying and resting poses, including the legacy renderer-level scale and vertical transform. The pre-oracle bat implementation has been removed; bat comparison intentionally starts from Minecraft's modern model and remains red until a clean oracle-derived production plan replaces it. Dog collar geometry and pig saddle geometry are captured as separate passes. Cats and dogs deliberately have their own scenario families even though Minecraft 1.8.9 renders them through the ocelot and wolf models.

## Agent loop

```text
./gradlew captureLegacyEntityOracle -PlegacyEntity=ocelot
./gradlew compareLegacyEntityModels -PlegacyEntity=ocelot -PlegacyScenario=ocelot-baby-walk
# edit production model/render plan
./gradlew compareLegacyEntityModels -PlegacyEntity=ocelot -PlegacyScenario=ocelot-baby-walk
./gradlew compareLegacyEntityModels -PlegacyEntity=ocelot
./gradlew verifyLegacyEntityModels
```

Replace `ocelot` with `cat`, `dog`, `chicken`, `pig`, `cow`, `rabbit`, or `bat` for a targeted loop. For example:

```text
./gradlew captureLegacyEntityOracle -PlegacyEntity=rabbit
./gradlew compareLegacyEntityModels -PlegacyEntity=rabbit -PlegacyScenario=rabbit-baby-jump
./gradlew explainLegacyEntityFailure -PlegacyEntity=rabbit -PlegacyScenario=rabbit-baby-jump
```

`compareLegacyEntityModels` never invokes capture. Missing snapshots produce a command that captures the requested scenario. `explainLegacyEntityFailure` prints the highest-scoring mismatch, complete inputs, nearest legacy draw, modern part path, worst vertex, and exact rerun command.

Local traces live under `build/legacy-entity-oracle/1.8.9/`; reports live under `build/reports/legacy-entity-oracle/`. Both are development outputs. Captures include the verified jar hash and are refused if it differs from the pinned client.

## Contracts

The committed scenario catalog rejects unknown or missing fields. Both adapters account for every declared input; an unconsumed input fails capture or comparison. Traces preserve double values, normalize negative zero, reject non-finite values, and contain ordered passes, texture dimensions, positions, normalized UVs, winding, normals, visibility metadata, and optional diagnostic identities.

Opaque/cutout quads are paired with deterministic minimum-cost assignment. Ordered passes retain draw order. Cyclic vertex rotation is allowed; reversed winding is not. Position, UV, and normal tolerances are `1e-5`, `1e-6`, and `1e-5`. Score bands make pass/topology failures dominate UV/winding failures, which dominate geometry error.

Run `./gradlew verifyLegacyEntityOracleHarness` without any Minecraft installation to test comparator mutations and deterministic trace/report serialization. `verifyLegacyEntityModels` intentionally remains outside `check` while the pilot models have known mismatches.
