package dev.oery.legacyresources.client.convert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;

/** Vanilla 1.8.9 model JSON used as the lower layer beneath an incomplete legacy resource pack. */
final class LegacyVanillaAssets {
	private static final String MODEL_PREFIX = "assets/";
	private static final String MODEL_MARKER = "/models/";
	private static final Path DEFAULT_JAR = Path.of(
		System.getProperty("user.home"), ".minecraft", "versions", "1.8.9", "1.8.9.jar"
	);
	private static final Object LOCK = new Object();

	private static volatile boolean sourceOverridden;
	private static volatile @Nullable PackResources source;
	private static volatile @Nullable Map<Identifier, byte[]> models;

	private LegacyVanillaAssets() {
	}

	static byte @Nullable [] read(Identifier location) {
		return modelMap().get(location);
	}

	static boolean has(Identifier location) {
		return modelMap().containsKey(location);
	}

	/** Lab-only deterministic source; {@code null} deliberately simulates an unavailable 1.8.9 jar. */
	static void useSource(@Nullable PackResources resources) {
		synchronized (LOCK) {
			source = resources;
			sourceOverridden = true;
			models = null;
		}
	}

	private static Map<Identifier, byte[]> modelMap() {
		Map<Identifier, byte[]> cached = models;
		if (cached != null) return cached;
		synchronized (LOCK) {
			cached = models;
			if (cached == null) {
				cached = Collections.unmodifiableMap(sourceOverridden ? load(source) : load(DEFAULT_JAR));
				models = cached;
			}
		}
		return cached;
	}

	private static Map<Identifier, byte[]> load(@Nullable PackResources resources) {
		if (resources == null) return Map.of();
		Map<Identifier, byte[]> loaded = new LinkedHashMap<>();
		for (String namespace : resources.getNamespaces(PackType.CLIENT_RESOURCES)) {
			resources.listResources(PackType.CLIENT_RESOURCES, namespace, "models", (id, supplier) -> {
				if (!id.getPath().endsWith(".json")) return;
				try (InputStream in = supplier.get()) {
					loaded.put(id, in.readAllBytes());
				} catch (IOException ignored) {
				}
			});
		}
		return loaded;
	}

	private static Map<Identifier, byte[]> load(Path jar) {
		Map<Identifier, byte[]> loaded = new LinkedHashMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || !name.startsWith(MODEL_PREFIX) || !name.endsWith(".json")) continue;
				int namespaceEnd = name.indexOf('/', MODEL_PREFIX.length());
				if (namespaceEnd < 0 || !name.startsWith(MODEL_MARKER, namespaceEnd)) continue;
				String namespace = name.substring(MODEL_PREFIX.length(), namespaceEnd);
				String path = name.substring(namespaceEnd + 1);
				Identifier id = Identifier.tryParse(namespace + ":" + path);
				if (id == null) continue;
				try (InputStream in = zip.getInputStream(entry)) {
					loaded.put(id, in.readAllBytes());
				}
			}
		} catch (IOException ignored) {
			return Map.of();
		}
		return loaded.containsKey(Identifier.fromNamespaceAndPath("minecraft", "models/item/stone.json"))
			? loaded : Map.of();
	}
}
