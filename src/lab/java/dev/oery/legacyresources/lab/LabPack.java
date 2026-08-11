package dev.oery.legacyresources.lab;

import dev.oery.legacyresources.LegacyResources;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * One resource pack in the lab's corpus, already wrapped in the mod's conversion.
 * <p>
 * {@link #texture} is the only way derivation inputs are ever fetched, and it deliberately goes
 * through {@link PackResources#getResource} on the converted pack rather than reading the zip: what a
 * derivation sees in the lab is then exactly what it will see in game, including inputs the pack has
 * no file for at all (the compass frames and the redstone dust textures are synthesized by the
 * conversion itself).
 */
final class LabPack {
	/** Every texture path a derivation names is relative to this and has no extension. */
	private static final String TEXTURE_PREFIX = "textures/";

	private final String id;
	private final String name;
	private final PackResources resources;
	/**
	 * Decoded source textures, keyed by the derivation-facing path. Sources are re-fetched on every
	 * slider movement across the whole corpus, and some of them are cropped out of multi-megabyte HD
	 * sheets, so decoding them once per pack is the difference between a snappy and an unusable page.
	 */
	private final Map<String, Optional<BufferedImage>> textureCache = new ConcurrentHashMap<>();

	LabPack(String id, String name, PackResources resources) {
		this.id = id;
		this.name = name;
		this.resources = resources;
	}

	String id() {
		return id;
	}

	String name() {
		return name;
	}

	/**
	 * @param path modern texture path relative to {@code textures/}, without extension
	 * @return the converted pack's texture, or empty if this pack has nothing that maps to it
	 */
	Optional<BufferedImage> texture(String path) {
		return textureCache.computeIfAbsent(path, this::load);
	}

	boolean resolves(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", path);
		return resources.getResource(PackType.CLIENT_RESOURCES, id) != null;
	}

	private Optional<BufferedImage> load(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("minecraft", TEXTURE_PREFIX + path + ".png");
		IoSupplier<InputStream> supplier = resources.getResource(PackType.CLIENT_RESOURCES, id);
		if (supplier == null) {
			return Optional.empty();
		}
		try (InputStream in = supplier.get()) {
			return Optional.ofNullable(ImageIO.read(in));
		} catch (IOException e) {
			LegacyResources.LOGGER.warn("Lab: failed to read {} from {}", id, name, e);
			return Optional.empty();
		}
	}

	/**
	 * Every resource path the converted pack announces for {@code directory}.
	 * <p>
	 * Exists to check the half of the conversion {@link #texture} cannot see. Atlas sprites are
	 * discovered by enumerating directories, never by asking for identifiers one at a time, so a
	 * texture that {@code getResource} answers correctly but {@code listResources} never names is
	 * invisible to the game - it renders as vanilla's art with nothing in the log to say why. That
	 * exact mistake has already been made twice in this mod's history (see the comments in
	 * {@code LegacyPackResources.listResources}), so the lab tests for it.
	 */
	Set<String> listedPaths(String directory) {
		Set<String> paths = new HashSet<>();
		resources.listResources(PackType.CLIENT_RESOURCES, "minecraft", directory, (id, supplier) -> paths.add(id.getPath()));
		return paths;
	}

	/** The pack's own resolution, taken from whichever source texture it does have, or {@code null}. */
	@Nullable Integer resolution(Iterable<String> candidatePaths) {
		for (String path : candidatePaths) {
			Optional<BufferedImage> image = texture(path);
			if (image.isPresent()) {
				return image.get().getWidth();
			}
		}
		return null;
	}

	void close() {
		resources.close();
	}
}
