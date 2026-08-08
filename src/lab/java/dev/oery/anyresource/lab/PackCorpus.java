package dev.oery.anyresource.lab;

import dev.oery.anyresource.client.convert.LabPackAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.jspecify.annotations.Nullable;

/**
 * The corpus a derivation is judged against: every legacy pack in the resourcepacks folder, opened
 * through the mod's own conversion.
 * <p>
 * The point of tuning against all of them at once is that these packs disagree wildly - 16x to 512x,
 * vanilla-faithful to fully stylized - and a constant picked while looking at one of them routinely
 * looks wrong on the next twenty.
 */
final class PackCorpus {
	/** Where 1.8.9's own extracted {@code assets/} tree lives; see {@code reference/README.md}. */
	private static final String VANILLA_LEGACY_ASSETS = "reference/1.8.9/assets";
	/** Where the targeted modern version's extracted {@code assets/} tree lives. */
	private static final String VANILLA_MODERN_ASSETS = "reference/26.2/assets";

	/** Identifier of the synthetic pack made from vanilla 1.8.9's own assets; see {@link #vanillaControl}. */
	static final String CONTROL_ID = "__vanilla_1_8_9";

	private final List<LabPack> packs;
	private final List<Skipped> skipped;
	private final @Nullable LabPack control;
	private final @Nullable Path modernAssets;

	private PackCorpus(List<LabPack> packs, List<Skipped> skipped, @Nullable LabPack control, @Nullable Path modernAssets) {
		this.packs = packs;
		this.skipped = skipped;
		this.control = control;
		this.modernAssets = modernAssets;
	}

	/** A pack in the folder the lab will not show, and why - reported rather than silently dropped. */
	record Skipped(String name, String reason) {
	}

	static PackCorpus load(Path packsDirectory, Path projectDirectory) {
		List<LabPack> packs = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		try (Stream<Path> entries = Files.list(packsDirectory)) {
			List<Path> zips = entries
				.filter(path -> path.getFileName().toString().endsWith(".zip"))
				.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
				.toList();
			for (Path zip : zips) {
				Opened opened = open(zip);
				if (opened.pack() != null) {
					packs.add(opened.pack());
				} else {
					skipped.add(new Skipped(stripFormatting(zip.getFileName().toString()), opened.reason()));
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read resource pack folder " + packsDirectory, e);
		}
		Path modernAssets = existingDirectory(projectDirectory.resolve(VANILLA_MODERN_ASSETS));
		return new PackCorpus(List.copyOf(packs), List.copyOf(skipped), vanillaControl(projectDirectory), modernAssets);
	}

	/** Either an opened pack, or why it was left out. */
	private record Opened(@Nullable LabPack pack, String reason) {
	}

	/**
	 * Opens {@code zip} the way the game does: a vanilla {@code FilePackResources}, put through the
	 * same detect-then-convert step {@code LegacyResourcesSupplier} performs behind {@code PackMixin}.
	 * A pack that isn't legacy is left out - the mod would pass it through untouched, so there is
	 * nothing for a derivation to say about it.
	 * <p>
	 * A pack whose {@code pack.mcmeta} vanilla cannot read is left out too, and the parse failure is
	 * reported verbatim. That is not the lab being fussy: the same read fails in game, so such a pack
	 * never loads there either, and pretending otherwise here would tune derivations against a pack
	 * nobody can actually use. Several in this corpus are in that state - unescaped newlines inside
	 * the description string, mostly.
	 */
	private static Opened open(Path zip) {
		String name = zip.getFileName().toString();
		PackLocationInfo location = new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty());
		Pack.ResourcesSupplier supplier = new FilePackResources.FileResourcesSupplier(zip);
		try {
			PackResources resources = LabPackAccess.openIfLegacy(supplier, location);
			return resources == null
				? new Opened(null, "not a pre-flattening pack (pack_format 1-3)")
				: new Opened(new LabPack(name, stripFormatting(name), resources), "");
		} catch (RuntimeException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			return new Opened(null, "unreadable: " + cause.getMessage());
		}
	}

	/**
	 * Vanilla 1.8.9 itself, as a pack - the one input in the corpus whose correct output is known,
	 * since the modern version ships the very texture the derivation is trying to invent. Deriving
	 * from it and diffing against {@code reference/26.2} turns "does this look right" into a number.
	 * <p>
	 * Absent when {@code reference/} has not been regenerated (it is gitignored); the lab then simply
	 * runs without a control.
	 */
	private static @Nullable LabPack vanillaControl(Path projectDirectory) {
		Path assets = existingDirectory(projectDirectory.resolve(VANILLA_LEGACY_ASSETS));
		if (assets == null) {
			return null;
		}
		PackLocationInfo location =
			new PackLocationInfo(CONTROL_ID, Component.literal("vanilla 1.8.9"), PackSource.BUILT_IN, Optional.empty());
		// Forced rather than detected: this is a bare assets/ tree extracted from the game jar, so it
		// has no pack.mcmeta for LegacyPackDetector to read.
		return new LabPack(CONTROL_ID, "vanilla 1.8.9 (control)",
			LabPackAccess.convert(new PathPackResources(location, assets)));
	}

	private static @Nullable Path existingDirectory(Path path) {
		return Files.isDirectory(path) ? path : null;
	}

	/** Pack file names are full of legacy section-sign colour codes; they are noise in a UI label. */
	private static String stripFormatting(String name) {
		return name.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("\\.zip$", "").replaceAll("\\s+", " ").trim();
	}

	List<LabPack> packs() {
		return packs;
	}

	List<Skipped> skipped() {
		return skipped;
	}

	@Nullable LabPack control() {
		return control;
	}

	/** Root of the modern version's extracted assets, from which a derivation's target art is read. */
	@Nullable Path modernAssets() {
		return modernAssets;
	}
}
