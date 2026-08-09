package dev.oery.legacyresources.lab;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.jspecify.annotations.Nullable;

/**
 * The derivation lab: a local page for tuning a texture derivation against every legacy pack at once.
 * <p>
 * Run it with {@code ./gradlew runLab} and open {@code http://localhost:8642}. Pick a derivation, and
 * the page shows the modern texture being recreated next to that derivation's output on all ~79
 * pre-flattening packs in {@code ~/.minecraft/resourcepacks}, with a slider for each constant the
 * derivation declares. Move a slider and every pack re-renders.
 * <p>
 * Two properties are worth preserving if this is ever extended. First, it drives the <em>shipping</em>
 * derivation classes, recompiled in place ({@link DerivationReloader}) and reached through the real
 * pack conversion ({@link PackCorpus}) - so a constant that looks right here is right in game, with no
 * second implementation to keep in sync. Second, nothing in this source set is in the mod jar; see the
 * {@code lab} source set in {@code build.gradle}.
 */
public final class LabServer {
	private static final int DEFAULT_PORT = 8642;
	private static final String WEB_DIRECTORY = "src/lab/resources/web";
	/** Modern assets are laid out {@code <root>/assets/minecraft/textures/...}; see {@code reference/README.md}. */
	private static final String MODERN_TEXTURE_PREFIX = "assets/minecraft/textures/";
	/** {@code pack} value asking for vanilla's own modern art rather than any pack's. */
	private static final String TARGET_PACK = "__target";

	private static final Gson GSON = new Gson();

	private final Path projectDirectory;
	private final PackCorpus corpus;
	private final DerivationReloader reloader;

	private LabServer(Path projectDirectory, PackCorpus corpus, DerivationReloader reloader) {
		this.projectDirectory = projectDirectory;
		this.corpus = corpus;
		this.reloader = reloader;
	}

	public static void main(String[] args) throws IOException {
		bootstrapMinecraft();
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path packs = Path.of(System.getProperty(
			"lab.packs", System.getProperty("user.home") + "/.minecraft/resourcepacks"
		));
		int port = Integer.getInteger("lab.port", DEFAULT_PORT);

		System.out.println("Opening packs from " + packs + " ...");
		long started = System.currentTimeMillis();
		PackCorpus corpus = PackCorpus.load(packs, project);
		System.out.printf(
			"%d legacy packs (%d skipped) in %dms%n",
			corpus.packs().size(), corpus.skipped().size(), System.currentTimeMillis() - started
		);
		if (corpus.control() == null) {
			System.out.println("No vanilla control: " + project.resolve("reference/1.8.9/assets")
				+ " is missing, see reference/README.md");
		}

		LabServer lab = new LabServer(project, corpus, new DerivationReloader(project));
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		server.createContext("/api/derivations", lab::handleDerivations);
		server.createContext("/api/render", lab::handleRender);
		server.createContext("/api/texture", lab::handleTexture);
		server.createContext("/api/verify", lab::handleVerify);
		server.createContext("/", lab::handleStatic);
		// A small pool, not a single thread: the browser opens the page and its assets at once, and a
		// serial executor makes that visibly stutter.
		server.setExecutor(Executors.newFixedThreadPool(4));
		server.start();
		System.out.println("Derivation lab on http://localhost:" + port);
	}

	/**
	 * Brings up the parts of Minecraft the conversion touches outside a running game.
	 * <p>
	 * Reading a pack's {@code pack.mcmeta} goes through the vanilla {@code PackMetadataSection} codec,
	 * whose description field is a {@code Component} - and decoding one of those reaches
	 * {@code BuiltInRegistries}, which throws "Not bootstrapped" if the registries were never
	 * populated. This is the whole cost of running the real conversion code outside the game, and it
	 * is two lines.
	 */
	private static void bootstrapMinecraft() {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
	}

	// ---------------------------------------------------------------- routes

	private void handleDerivations(HttpExchange exchange) throws IOException {
		DerivationReloader.Reload reload = reloader.current();
		JsonObject response = new JsonObject();
		response.add("derivations", describeAll(reload.derivations()));
		response.add("errors", GSON.toJsonTree(reload.errors()));
		response.addProperty("note", reload.note());
		respondJson(exchange, response);
	}

	/**
	 * Renders one derivation across the whole corpus in a single response.
	 * <p>
	 * One request rather than one per pack: at 79 packs times four outputs, per-image requests spend
	 * more time in round trips than in rendering, and a slider drag issues a new batch every 120ms.
	 */
	private void handleRender(HttpExchange exchange) throws IOException {
		Map<String, String> query = query(exchange.getRequestURI());
		DerivationReloader.Reload reload = reloader.current();
		DerivationHandle derivation = find(reload.derivations(), query.get("d"));
		if (derivation == null) {
			JsonObject response = new JsonObject();
			response.add("errors", GSON.toJsonTree(
				reload.errors().isEmpty() ? List.of("Unknown derivation: " + query.get("d")) : reload.errors()
			));
			respondJson(exchange, response);
			return;
		}
		Map<String, Double> overrides = overrides(derivation, query);
		long started = System.currentTimeMillis();

		JsonObject response = new JsonObject();
		response.addProperty("id", derivation.id());
		response.add("params", describeParams(derivation));
		response.add("sources", GSON.toJsonTree(derivation.sources()));
		response.add("outputs", GSON.toJsonTree(derivation.outputs()));
		response.add("errors", GSON.toJsonTree(reload.errors()));
		response.addProperty("note", reload.note());
		response.add("target", targetImages(derivation));
		response.add("skipped", skipped());

		LabPack control = corpus.control();
		if (control != null) {
			response.add("control", renderPack(control, derivation, overrides, true));
		}
		// Packs are independent and there are eighty of them, several at 512px; in parallel a slider
		// drag stays responsive.
		JsonArray packs = new JsonArray();
		corpus.packs().parallelStream()
			.map(pack -> renderPack(pack, derivation, overrides, false))
			.forEachOrdered(packs::add);
		response.add("packs", packs);
		response.addProperty("elapsedMs", System.currentTimeMillis() - started);
		respondJson(exchange, response);
	}

	/** One image at its real resolution, for the click-through view the grid's previews can't give. */
	private void handleTexture(HttpExchange exchange) throws IOException {
		Map<String, String> query = query(exchange.getRequestURI());
		String path = query.getOrDefault("path", "");
		String packId = query.getOrDefault("pack", "");
		BufferedImage image = null;

		if (TARGET_PACK.equals(packId)) {
			image = modernTexture(path);
		} else {
			LabPack pack = findPack(packId);
			if (pack != null) {
				String derivationId = query.get("d");
				if (derivationId == null) {
					image = pack.texture(path).orElse(null);
				} else {
					DerivationHandle derivation = find(reloader.current().derivations(), derivationId);
					if (derivation != null) {
						image = derive(pack, derivation, overrides(derivation, query)).get(path);
					}
				}
			}
		}
		if (image == null) {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
			return;
		}
		byte[] png = LabImages.png(image);
		exchange.getResponseHeaders().set("Content-Type", "image/png");
		exchange.sendResponseHeaders(200, png.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(png);
		}
	}

	/**
	 * Checks that a derivation's outputs survive the trip through the real pack, for every pack -
	 * i.e. that they would actually appear in game, not merely in this page.
	 * <p>
	 * The page renders derivations by calling them directly, which proves the image is right but says
	 * nothing about whether {@code LegacyPackResources} serves it. Two independent things have to
	 * hold, and the second is the one that silently fails: {@code getResource} must answer for the
	 * texture, <em>and</em> {@code listResources} must name it, or atlas discovery never sees it.
	 * <p>
	 * <b>This route alone does not hot reload.</b> It goes through the real {@code LegacyPackResources},
	 * which links against the derivation classes on the lab's own classpath, not the recompiled ones
	 * {@link DerivationReloader} hands the renderer. So a derivation added or renamed since the lab
	 * started reads as "served by 0 packs" here until the lab is restarted - which is a stale answer,
	 * not a failure. Everything else on the page does reflect the edit immediately.
	 */
	private void handleVerify(HttpExchange exchange) throws IOException {
		Map<String, String> query = query(exchange.getRequestURI());
		DerivationHandle derivation = find(reloader.current().derivations(), query.get("d"));
		JsonObject response = new JsonObject();
		if (derivation == null) {
			response.addProperty("error", "Unknown derivation: " + query.get("d"));
			respondJson(exchange, response);
			return;
		}
		JsonArray results = new JsonArray();
		for (LabPack pack : corpus.packs()) {
			Set<String> listedBlocks = pack.listedPaths("textures/block");
			Set<String> listedItems = pack.listedPaths("textures/item");
			JsonObject row = new JsonObject();
			row.addProperty("name", pack.name());
			JsonArray served = new JsonArray();
			JsonArray unlisted = new JsonArray();
			for (String path : derivation.outputs()) {
				if (pack.texture(path).isEmpty()) {
					continue;
				}
				served.add(path);
				// Only atlas sprites need listing; entity/equipment textures are loaded by path.
				boolean needsListing = path.startsWith("block/") || path.startsWith("item/");
				Set<String> listed = path.startsWith("block/") ? listedBlocks : listedItems;
				if (needsListing && !listed.contains("textures/" + path + ".png")) {
					unlisted.add(path);
				}
			}
			row.add("served", served);
			row.add("unlisted", unlisted);
			results.add(row);
		}
		response.add("packs", results);
		respondJson(exchange, response);
	}

	/** Serves the page straight off disk, so editing it needs no rebuild either. */
	private void handleStatic(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String name = path.equals("/") ? "index.html" : path.substring(1);
		Path file = projectDirectory.resolve(WEB_DIRECTORY).resolve(name).normalize();
		if (!file.startsWith(projectDirectory.resolve(WEB_DIRECTORY)) || !Files.isRegularFile(file)) {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
			return;
		}
		byte[] body = Files.readAllBytes(file);
		exchange.getResponseHeaders().set("Content-Type", contentType(name));
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	// ---------------------------------------------------------------- rendering

	private JsonObject renderPack(LabPack pack, DerivationHandle derivation, Map<String, Double> overrides, boolean control) {
		JsonObject json = new JsonObject();
		json.addProperty("id", pack.id());
		json.addProperty("name", pack.name());
		Integer resolution = pack.resolution(derivation.sources());
		json.addProperty("resolution", resolution);

		JsonObject sources = new JsonObject();
		JsonArray missing = new JsonArray();
		for (String path : derivation.sources()) {
			Optional<BufferedImage> image = pack.texture(path);
			if (image.isPresent()) {
				sources.addProperty(path, LabImages.dataUrl(image.get()));
			} else {
				missing.add(path);
			}
		}
		json.add("sources", sources);
		json.add("missing", missing);

		JsonObject outputs = new JsonObject();
		try {
			Map<String, BufferedImage> derived = derive(pack, derivation, overrides);
			derived.forEach((path, image) -> outputs.addProperty(path, LabImages.dataUrl(image)));
			if (control) {
				json.add("scores", controlScores(derivation, derived));
			}
		} catch (DerivationHandle.DerivationFailure e) {
			// Surfaced per pack rather than thrown: a derivation that only breaks on, say, non-square
			// sources should still render the other seventy-eight.
			json.addProperty("error", String.valueOf(e.getCause()));
		}
		json.add("outputs", outputs);
		return json;
	}

	private Map<String, BufferedImage> derive(LabPack pack, DerivationHandle derivation, Map<String, Double> overrides) {
		Map<String, BufferedImage> sources = new LinkedHashMap<>();
		for (String path : derivation.sources()) {
			pack.texture(path).ifPresent(image -> sources.put(path, image));
		}
		return derivation.derive(sources, overrides);
	}

	/**
	 * How far the control's derived output lands from the modern version's real texture, per output.
	 * The only objective signal available - every other pack in the corpus is being invented for.
	 */
	private JsonObject controlScores(DerivationHandle derivation, Map<String, BufferedImage> derived) {
		JsonObject scores = new JsonObject();
		for (String path : derivation.outputs()) {
			BufferedImage target = modernTexture(path);
			BufferedImage output = derived.get(path);
			if (target != null && output != null) {
				double difference = LabImages.difference(output, target);
				if (difference >= 0) {
					scores.addProperty(path, difference);
				}
			}
		}
		return scores;
	}

	private JsonObject targetImages(DerivationHandle derivation) {
		JsonObject target = new JsonObject();
		for (String path : derivation.outputs()) {
			BufferedImage image = modernTexture(path);
			if (image != null) {
				target.addProperty(path, LabImages.dataUrl(image));
			}
		}
		return target;
	}

	/** The modern version's own texture - what the derivation is trying to stand in for. */
	private @Nullable BufferedImage modernTexture(String path) {
		Path assets = corpus.modernAssets();
		if (assets == null) {
			return null;
		}
		Path file = assets.resolve(MODERN_TEXTURE_PREFIX + path + ".png");
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			return ImageIO.read(file.toFile());
		} catch (IOException e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- plumbing

	private JsonArray describeAll(List<DerivationHandle> derivations) {
		JsonArray array = new JsonArray();
		for (DerivationHandle derivation : derivations) {
			JsonObject json = new JsonObject();
			json.addProperty("id", derivation.id());
			json.add("sources", GSON.toJsonTree(derivation.sources()));
			json.add("outputs", GSON.toJsonTree(derivation.outputs()));
			json.add("params", describeParams(derivation));
			array.add(json);
		}
		return array;
	}

	private static JsonArray describeParams(DerivationHandle derivation) {
		JsonArray array = new JsonArray();
		for (DerivationHandle.ParamSpec param : derivation.params()) {
			JsonObject json = new JsonObject();
			json.addProperty("name", param.name());
			json.addProperty("min", param.min());
			json.addProperty("max", param.max());
			json.addProperty("default", param.defaultValue());
			json.addProperty("step", param.step());
			json.addProperty("integer", param.isInteger());
			array.add(json);
		}
		return array;
	}

	private JsonArray skipped() {
		JsonArray array = new JsonArray();
		for (PackCorpus.Skipped entry : corpus.skipped()) {
			JsonObject json = new JsonObject();
			json.addProperty("name", entry.name());
			json.addProperty("reason", entry.reason());
			array.add(json);
		}
		return array;
	}

	/** Slider values, taken only for params the derivation actually declares. */
	private static Map<String, Double> overrides(DerivationHandle derivation, Map<String, String> query) {
		Map<String, Double> overrides = new HashMap<>();
		for (DerivationHandle.ParamSpec param : derivation.params()) {
			String value = query.get("p." + param.name());
			if (value == null) {
				continue;
			}
			try {
				overrides.put(param.name(), Double.parseDouble(value));
			} catch (NumberFormatException e) {
				// Leave it at the declared default rather than failing the whole render.
			}
		}
		return Map.copyOf(overrides);
	}

	private static @Nullable DerivationHandle find(List<DerivationHandle> derivations, @Nullable String id) {
		return derivations.stream().filter(derivation -> derivation.id().equals(id)).findFirst().orElse(null);
	}

	private @Nullable LabPack findPack(String id) {
		LabPack control = corpus.control();
		if (control != null && control.id().equals(id)) {
			return control;
		}
		return corpus.packs().stream().filter(pack -> pack.id().equals(id)).findFirst().orElse(null);
	}

	private static Map<String, String> query(URI uri) {
		Map<String, String> values = new LinkedHashMap<>();
		String query = uri.getRawQuery();
		if (query == null) {
			return values;
		}
		for (String pair : query.split("&")) {
			int equals = pair.indexOf('=');
			if (equals > 0) {
				values.put(
					URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
					URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8)
				);
			}
		}
		return values;
	}

	private static void respondJson(HttpExchange exchange, JsonObject body) throws IOException {
		byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static String contentType(String name) {
		if (name.endsWith(".html")) {
			return "text/html; charset=utf-8";
		}
		if (name.endsWith(".js")) {
			return "text/javascript; charset=utf-8";
		}
		if (name.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		return "application/octet-stream";
	}
}
