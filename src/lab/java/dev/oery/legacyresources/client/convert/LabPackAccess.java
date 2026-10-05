package dev.oery.legacyresources.client.convert;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import org.jspecify.annotations.Nullable;

/**
 * Lab-only access to the package-private pieces of the conversion the derivation lab needs.
 * <p>
 * This class lives in the {@code lab} source set but declares the {@code convert} package, purely so
 * it can reach {@link LegacyPackDetector}, {@link LegacyPackResources} and {@code ModernVanillaAssets}
 * without any of them having to become public API of the mod for a development harness's benefit. Nothing in the shipped
 * jar sees this file - the {@code lab} source set is excluded from it (see {@code build.gradle}).
 */
public final class LabPackAccess {
	private LabPackAccess() {
	}

	/**
	 * Opens a pack converted, or {@code null} if it isn't a legacy one.
	 * <p>
	 * Line for line what {@link LegacyResourcesSupplier#openMetadata}/{@link
	 * LegacyResourcesSupplier#openResources} do, and it has to be: the detection must run against the
	 * <em>unconverted</em> resources. A converted pack deliberately reports the current
	 * {@code pack_format} rather than its real one (that is how the game accepts it as compatible),
	 * so asking one whether it is legacy always answers no.
	 * <p>
	 * The metadata probe is closed before the resources are opened, then re-opened the game's own way
	 * by {@link Pack#readPackMetadata} - so {@code openResources} is handed the exact
	 * {@link Pack.Metadata} the game itself would pass it. 26.3 opens a pack as a stream of layers: a
	 * legacy pack declares no overlays, so the first layer is the pack and the tail is closed straight
	 * away, which keeps the ~85-zip corpus from leaking open zip handles.
	 */
	public static @Nullable PackResources openIfLegacy(Pack.ResourcesSupplier supplier, PackLocationInfo location) {
		PackMetadataResources metadata = supplier.openMetadata(location);
		boolean legacy;
		try {
			legacy = LegacyPackDetector.isLegacy(metadata);
		} finally {
			metadata.close();
		}
		if (!legacy) {
			return null;
		}
		PackFormat current = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
		Pack.Metadata packMetadata = Pack.readPackMetadata(location, supplier, current, PackType.CLIENT_RESOURCES);
		if (packMetadata == null) {
			return null;
		}
		List<PackResources> layers = supplier.openResources(location, packMetadata).toList();
		if (layers.isEmpty()) {
			return null;
		}
		layers.subList(1, layers.size()).forEach(PackResources::close);
		return new LegacyPackResources(layers.getFirst());
	}

	/**
	 * Converts {@code resources} whether or not it announces itself as legacy.
	 * <p>
	 * Only for the lab's vanilla control, which is 1.8.9's own {@code assets/} tree extracted out of
	 * the game jar and therefore has no {@code pack.mcmeta} to be detected by - but is, of course,
	 * the most legacy pack there is. Real packs go through {@link LegacyResourcesSupplier}, which
	 * gates on {@link #isLegacy}.
	 */
	public static PackResources convert(PackResources resources) {
		return new LegacyPackResources(resources);
	}

	/**
	 * Tells the conversion where the targeted version's own assets are, which in the lab is
	 * {@code reference/26.3/assets} rather than a running client's built-in pack (there is no client
	 * here at all). Must be called before any pack is converted, and with {@code null} when
	 * {@code reference/} has not been regenerated - see {@code ModernVanillaAssets.useSource}, which
	 * treats "no vanilla to consult" and "not wired up yet" as different states on purpose.
	 */
	public static void useModernVanillaAssets(@Nullable PackResources resources) {
		ModernVanillaAssets.useSource(resources);
	}

	/** Supplies vanilla 1.8.9 models to the optional legacy item-model fallback in headless checks. */
	public static void useLegacyVanillaAssets(@Nullable PackResources resources) {
		LegacyVanillaAssets.useSource(resources);
	}
}
