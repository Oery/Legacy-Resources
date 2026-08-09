package dev.oery.legacyresources.client.convert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;

/**
 * Answers "does modern vanilla itself still ship this file?".
 * <p>
 * Needed to decide whether a converted blockstate/model is usable at all: it is only usable if every
 * model it points at exists somewhere, and "somewhere" is either the legacy pack itself or the
 * vanilla assets the pack is layered on top of. A legacy pack routinely references models vanilla
 * dropped in the flattening ({@code sandstone_normal}, {@code half_slab_stone}, {@code lever_off},
 * ...); pointing the game at those is exactly what produces missing-model cubes, so the conversion
 * has to be able to tell the two cases apart - see {@link BlockstateConverter}.
 * <p>
 * The answer is read straight out of {@link Minecraft#getVanillaPackResources()} - the built-in pack,
 * constructed as part of the client itself and therefore already available by the time any pack is
 * converted - rather than from a generated list of legacy-only names baked into this mod, which would
 * silently rot every time the targeted Minecraft version changes.
 */
final class ModernVanillaAssets {
	/**
	 * Vanilla's own assets never change within a run, so a miss is as permanent as a hit. Static
	 * because it describes the game, not any one pack, and every open legacy pack asks the same
	 * questions about the same handful of shared parent models.
	 */
	private static final Map<Identifier, Boolean> CACHE = new ConcurrentHashMap<>();

	private static volatile boolean sourceOverridden;
	private static volatile @Nullable PackResources source;

	private ModernVanillaAssets() {
	}

	static boolean has(Identifier location) {
		return CACHE.computeIfAbsent(location, loc -> {
			PackResources vanilla = source();
			return vanilla != null && vanilla.getResource(PackType.CLIENT_RESOURCES, loc) != null;
		});
	}

	/**
	 * Points the lookup at something other than the running client's own vanilla pack, for the
	 * derivation lab - which has no {@link Minecraft} instance at all and reads the targeted version's
	 * assets out of {@code reference/} instead. Passing {@code null} is meaningful: it says "there is
	 * no vanilla to consult", which is not the same as "not wired up yet" (that would fall back to
	 * touching {@link Minecraft}, whose class initializer has no business running headlessly).
	 */
	static void useSource(@Nullable PackResources resources) {
		source = resources;
		sourceOverridden = true;
		CACHE.clear();
	}

	private static @Nullable PackResources source() {
		if (sourceOverridden) {
			return source;
		}
		Minecraft client = Minecraft.getInstance();
		return client == null ? null : client.getVanillaPackResources();
	}
}
