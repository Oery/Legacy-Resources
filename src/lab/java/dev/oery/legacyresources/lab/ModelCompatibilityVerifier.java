package dev.oery.legacyresources.lab;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.resources.model.BlockStateDefinitions;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.cuboid.MissingCuboidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Drives converted blockstates, models and item definitions through the current game's loaders. */
public final class ModelCompatibilityVerifier {
	private static final FileToIdConverter BLOCKSTATES = FileToIdConverter.json("blockstates");
	private static final FileToIdConverter MODELS = FileToIdConverter.json("models");
	private static final FileToIdConverter ITEMS = FileToIdConverter.json("items");

	private ModelCompatibilityVerifier() {
	}

	public static void main(String[] args) {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
		ClientBootstrap.bootstrap();
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path packs = Path.of(System.getProperty("lab.packs", System.getProperty("user.home") + "/.minecraft/resourcepacks"));
		PackCorpus corpus = PackCorpus.load(packs, project);
		if (corpus.modernAssets() == null) {
			throw new IllegalStateException("Modern reference assets are required for loader verification");
		}
		List<LabPack> inputs = new ArrayList<>(corpus.packs());
		if (corpus.control() != null) inputs.add(corpus.control());
		if (inputs.isEmpty()) throw new IllegalStateException("No legacy packs were available for verification");
		int models = 0;
		int blockstates = 0;
		int items = 0;
		for (int index = 0; index < inputs.size(); index++) {
			LabPack pack = inputs.get(index);
			Counts counts = verify(corpus.modernAssets(), pack);
			models += counts.models();
			blockstates += counts.blockstates();
			items += counts.items();
			System.out.println("[" + (index + 1) + "/" + inputs.size() + "] " + pack.name()
				+ ": " + counts.blockstates() + " blockstates, " + counts.models() + " models, " + counts.items() + " items");
		}
		System.out.println("Verified the real game loaders across " + inputs.size() + " converted packs ("
			+ blockstates + " blockstates, " + models + " models, " + items + " item definitions)");
	}

	private static Counts verify(Path modernAssets, LabPack pack) {
		verifyConvertedBlockstates(pack);
		PackLocationInfo modernInfo = new PackLocationInfo("modern-model-control", Component.literal("modern-model-control"),
			PackSource.BUILT_IN, Optional.empty());
		PackResources modern = new PathPackResources(modernInfo, modernAssets);
		MultiPackResourceManager manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
			List.of(modern, pack.resources()));
		try {
			Map<Identifier, UnbakedModel> parsedModels = parseModels(manager);
			verifyNoParentCycles(pack, parsedModels);
			BlockStateModelLoader.LoadedModels loadedBlockstates = BlockStateModelLoader.loadBlockStates(manager, Runnable::run).join();
			ClientItemInfoLoader.LoadedClientInfos itemInfos = ClientItemInfoLoader.scheduleLoad(manager, Runnable::run).join();
			verifyAnnouncedItemsLoaded(pack, itemInfos);

			ModelDiscovery discovery = new ModelDiscovery(parsedModels, MissingCuboidModel.missingModel());
			discovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
			loadedBlockstates.models().values().forEach(discovery::addRoot);
			itemInfos.contents().values().forEach(info -> discovery.addRoot(info.model()));
			ResolvedModel missing = discovery.missingModel();
			Map<Identifier, ResolvedModel> resolved = discovery.resolve();
			List<Identifier> missingDependencies = resolved.entrySet().stream()
				.filter(entry -> !entry.getKey().equals(MissingCuboidModel.LOCATION) && entry.getValue() == missing)
				.map(Map.Entry::getKey).sorted().toList();
			if (!missingDependencies.isEmpty()) {
				throw new IllegalStateException(pack.name() + " resolves model dependencies to the missing model: "
					+ missingDependencies);
			}
			verifyTextureAnnouncements(pack, manager);
			return new Counts(parsedModels.size(), listed(pack.resources(), "blockstates").size(),
				listed(pack.resources(), "items").size());
		} finally {
			// Closing the manager would also close the converted pack held by PackCorpus. It is not
			// reused here, but leaving corpus ownership in one place avoids surprising other lab checks.
			modern.close();
		}
	}

	private static void verifyNoParentCycles(LabPack pack, Map<Identifier, UnbakedModel> models) {
		Set<Identifier> cyclic = new HashSet<>();
		for (Identifier root : models.keySet()) {
			Set<Identifier> chain = new HashSet<>();
			Identifier current = root;
			while (current != null && chain.add(current)) {
				UnbakedModel model = models.get(current);
				current = model == null ? null : model.parent();
			}
			if (current != null) cyclic.add(current);
		}
		if (!cyclic.isEmpty()) {
			throw new IllegalStateException(pack.name() + " has cyclic model parent chains: "
				+ cyclic.stream().sorted().toList());
		}
	}

	private static void verifyConvertedBlockstates(LabPack pack) {
		Function<Identifier, StateDefinition<Block, BlockState>> definitions =
			BlockStateDefinitions.definitionLocationToBlockStateMapper();
		List<String> broken = new ArrayList<>();
		forEach(pack.resources(), "blockstates", (file, reader) -> {
			Identifier id = BLOCKSTATES.fileToId(file);
			StateDefinition<Block, BlockState> definition = definitions.apply(id);
			if (definition == null) return;
			try {
				JsonElement json = JsonParser.parseReader(reader);
				BlockStateModelDispatcher dispatcher = BlockStateModelDispatcher.CODEC.parse(JsonOps.INSTANCE, json)
					.getOrThrow(JsonParseException::new);
				Map<BlockState, ?> instantiated = dispatcher.instantiate(definition, () -> file.toString());
				if (instantiated.size() != definition.getPossibleStates().size()) {
					broken.add(id + " covers " + instantiated.size() + "/" + definition.getPossibleStates().size() + " states");
				}
			} catch (RuntimeException e) {
				broken.add(id + " rejected: " + e.getMessage());
			}
		});
		if (!broken.isEmpty()) {
			throw new IllegalStateException(pack.name() + " has broken converted blockstates:\n" + String.join("\n", broken));
		}
	}

	private static Map<Identifier, UnbakedModel> parseModels(MultiPackResourceManager manager) {
		Map<Identifier, UnbakedModel> result = new HashMap<>();
		MODELS.listMatchingResources(manager).forEach((file, resource) -> {
			try (Reader reader = resource.openAsReader()) {
				result.put(MODELS.fileToId(file), CuboidModel.fromStream(reader));
			} catch (Exception e) {
				throw new IllegalStateException("Game model codec rejected " + file + " from " + resource.sourcePackId(), e);
			}
		});
		return result;
	}

	private static void verifyAnnouncedItemsLoaded(LabPack pack, ClientItemInfoLoader.LoadedClientInfos infos) {
		Set<Identifier> announced = listed(pack.resources(), "items").stream().map(ITEMS::fileToId).collect(java.util.stream.Collectors.toSet());
		announced.removeAll(infos.contents().keySet());
		if (!announced.isEmpty()) {
			throw new IllegalStateException(pack.name() + " announces item definitions rejected by the game codec: " + announced);
		}
	}

	private static void verifyTextureAnnouncements(LabPack pack, MultiPackResourceManager manager) {
		Set<Identifier> sprites = new HashSet<>();
		for (String namespace : manager.getNamespaces()) {
			manager.listResources("textures/block", id -> id.getNamespace().equals(namespace) && id.getPath().endsWith(".png"))
				.keySet().forEach(id -> sprites.add(spriteId(id)));
			manager.listResources("textures/item", id -> id.getNamespace().equals(namespace) && id.getPath().endsWith(".png"))
				.keySet().forEach(id -> sprites.add(spriteId(id)));
		}
		List<String> missing = new ArrayList<>();
		forEach(pack.resources(), "models", (file, reader) -> {
			JsonElement parsed;
			try {
				parsed = JsonParser.parseReader(reader);
			} catch (RuntimeException e) {
				return; // The game-codec pass reports this with the source pack and full exception.
			}
			if (!parsed.isJsonObject()) return;
			JsonElement textures = parsed.getAsJsonObject().get("textures");
			if (textures == null || !textures.isJsonObject()) return;
			for (Map.Entry<String, JsonElement> binding : textures.getAsJsonObject().entrySet()) {
				if (!binding.getValue().isJsonPrimitive() || !binding.getValue().getAsJsonPrimitive().isString()) continue;
				String value = binding.getValue().getAsString();
				if (value.startsWith("#")) continue;
				Identifier id = Identifier.tryParse(value);
				if (id != null && (id.getPath().startsWith("block/") || id.getPath().startsWith("item/")) && !sprites.contains(id)) {
					missing.add(MODELS.fileToId(file) + " #" + binding.getKey() + " -> " + id);
				}
			}
		});
		if (!missing.isEmpty()) {
			throw new IllegalStateException(pack.name() + " has model sprites absent from the announced atlases:\n"
				+ String.join("\n", missing));
		}
	}

	private static Identifier spriteId(Identifier textureFile) {
		String path = textureFile.getPath();
		return Identifier.fromNamespaceAndPath(textureFile.getNamespace(),
			path.substring("textures/".length(), path.length() - ".png".length()));
	}

	private static Set<Identifier> listed(PackResources resources, String directory) {
		Set<Identifier> result = new HashSet<>();
		for (String namespace : resources.getNamespaces(PackType.CLIENT_RESOURCES)) {
			resources.listResources(PackType.CLIENT_RESOURCES, namespace, directory, (id, supplier) -> result.add(id));
		}
		return result;
	}

	private static void forEach(PackResources resources, String directory, ResourceReader consumer) {
		for (String namespace : resources.getNamespaces(PackType.CLIENT_RESOURCES)) {
			resources.listResources(PackType.CLIENT_RESOURCES, namespace, directory, (id, supplier) -> {
				try (Reader reader = new java.io.InputStreamReader(supplier.get(), java.nio.charset.StandardCharsets.UTF_8)) {
					consumer.accept(id, reader);
				} catch (Exception e) {
					throw new IllegalStateException("Cannot read " + id, e);
				}
			});
		}
	}

	@FunctionalInterface
	private interface ResourceReader {
		void accept(Identifier id, Reader reader);
	}

	private record Counts(int models, int blockstates, int items) {
	}
}
