package dev.oery.legacyresources.lab;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBootstrap;
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
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/** Regression checks for the custom-model failures reported against PureBDcraft. */
public final class ReportedIssueVerifier {
	private ReportedIssueVerifier() { }

	public static void main(String[] args) {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
		ClientBootstrap.bootstrap();
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path packs = Path.of(System.getProperty("lab.packs", System.getProperty("user.home") + "/.minecraft/resourcepacks"));
		PackCorpus corpus = PackCorpus.load(packs, project);
		LabPack bdcraft = corpus.packs().stream()
			.filter(pack -> pack.name().toLowerCase().contains("purebdcraft")).findFirst()
			.orElseThrow(() -> new IllegalStateException("PureBDcraft is required for this regression check"));
		LabPack control = corpus.control();
		if (control == null) throw new IllegalStateException("1.8.9 vanilla control is required for item-model checks");
		verifyItemDefinitionRouting(control);
		verifyItemHandTransforms(control);
		verifyMissingLegacyItemWrapper(bdcraft);
		verifyInheritedCustomItemTransforms(bdcraft);
		verifyGameLoader(corpus, control, "stone", .25, .4);
		verifyGameLoader(corpus, control, "torch", .5, .68);
		verifyGameLoader(corpus, bdcraft, "stone", .25, .4);
		verifyGameLoader(corpus, bdcraft, "torch", .375, .64);
		verifyTrapdoorGeometry(bdcraft);
		verifyRabbitSource(bdcraft);
		verifyChestConversion(bdcraft);
		verifyLegacyVariants(bdcraft);
		verifyVariantRecolours(bdcraft);
		verifyRailSlopeModels(bdcraft);
		verifySigns(bdcraft);
		verifyPlayerSkins(bdcraft);
		verifyTripwire(bdcraft);
		verifyBeaconBeam(bdcraft);
		System.out.println("Verified reported custom-model regressions against " + bdcraft.name());
	}

	private static void verifyItemDefinitionRouting(LabPack pack) {
		JsonObject definition = pack.text("items/grass_block.json").map(JsonParser::parseString)
			.filter(JsonObject.class::isInstance).map(JsonObject.class::cast)
			.orElseThrow(() -> new IllegalStateException("Converted grass-block item definition is missing"));
		JsonObject selected = definition.getAsJsonObject("model");
		if (selected == null || !"minecraft:item/grass_block".equals(selected.get("model").getAsString())) {
			throw new IllegalStateException("Block item definition did not select its converted legacy item wrapper: " + definition);
		}
		if (!pack.listedPaths("items").contains("items/grass_block.json")
			|| !pack.listedPaths("models").contains("models/item/grass_block.json")) {
			throw new IllegalStateException("Converted item definition or wrapper was not announced to the current loaders");
		}
	}

	private static void verifyItemHandTransforms(LabPack pack) {
		JsonObject model = model(pack, "models/item/torch.json");
		JsonObject display = model.getAsJsonObject("display");
		if (!"minecraft:item/generated".equals(model.get("parent").getAsString()) || display == null
			|| number(display.getAsJsonObject("firstperson_righthand").getAsJsonArray("scale"), 0) != .68
			|| number(display.getAsJsonObject("ground").getAsJsonArray("scale"), 0) != .5) {
			throw new IllegalStateException("Canonical generated-item transforms did not defer to current defaults: " + model);
		}
	}

	private static void verifyMissingLegacyItemWrapper(LabPack pack) {
		JsonObject definition = model(pack, "items/stone.json");
		if (!"minecraft:item/stone".equals(definition.getAsJsonObject("model").get("model").getAsString())) {
			throw new IllegalStateException("Pack without a legacy stone item wrapper was not routed through a synthesized one");
		}
		JsonObject wrapper = model(pack, "models/item/stone.json");
		JsonObject display = wrapper.getAsJsonObject("display");
		if (!"minecraft:block/stone".equals(wrapper.get("parent").getAsString())
			|| display == null || number(display.getAsJsonObject("ground").getAsJsonArray("scale"), 0) != .25
			|| number(display.getAsJsonObject("firstperson_righthand").getAsJsonArray("scale"), 0) != .4) {
			throw new IllegalStateException("Synthesized block-item wrapper lacks current context defaults: " + wrapper);
		}
		if (!pack.listedPaths("items").contains("items/stone.json")
			|| !pack.listedPaths("models").contains("models/item/stone.json")) {
			throw new IllegalStateException("Synthesized stone definition or wrapper was not announced");
		}
	}

	private static void verifyInheritedCustomItemTransforms(LabPack pack) {
		JsonObject display = model(pack, "models/item/torch.json").getAsJsonObject("display");
		if (display == null || display.has("firstperson") || display.has("thirdperson")
			|| Math.abs(number(display.getAsJsonObject("firstperson_righthand").getAsJsonArray("scale"), 0) - .64) > 1.0e-5
			|| Math.abs(number(display.getAsJsonObject("thirdperson_righthand").getAsJsonArray("scale"), 0) - .85) > 1.0e-5
			|| Math.abs(number(display.getAsJsonObject("gui").getAsJsonArray("scale"), 0) - 1.0) > 1.0e-5
			|| Math.abs(number(display.getAsJsonObject("ground").getAsJsonArray("scale"), 0) - .375) > 1.0e-5
			|| Math.abs(number(display.getAsJsonObject("head").getAsJsonArray("scale"), 0) - 1.5) > 1.0e-5) {
			throw new IllegalStateException("Custom item did not preserve its effective legacy presentation: " + display);
		}
	}

	/** Uses the same item codec, dependency discovery and resolved-parent traversal as ModelManager. */
	private static void verifyGameLoader(PackCorpus corpus, LabPack pack, String stem,
		double expectedGroundScale, double expectedFirstPersonScale) {
		Path modernAssets = corpus.modernAssets();
		if (modernAssets == null) throw new IllegalStateException("Modern reference assets are required for model-loader checks");
		PackLocationInfo modernInfo = new PackLocationInfo("modern-model-control", Component.literal("modern-model-control"),
			PackSource.BUILT_IN, Optional.empty());
		PackResources modern = new PathPackResources(modernInfo, modernAssets);
		MultiPackResourceManager manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(modern, pack.resources()));
		try {
			FileToIdConverter models = FileToIdConverter.json("models");
			Map<Identifier, UnbakedModel> parsedModels = new HashMap<>();
			models.listMatchingResources(manager).forEach((file, resource) -> {
				try (var reader = resource.openAsReader()) {
					parsedModels.put(models.fileToId(file), CuboidModel.fromStream(reader));
				} catch (Exception e) {
					throw new IllegalStateException("Game model codec rejected " + file, e);
				}
			});
			var itemInfos = ClientItemInfoLoader.scheduleLoad(manager, Runnable::run).join();
			Identifier itemId = Identifier.fromNamespaceAndPath("minecraft", stem);
			if (!itemInfos.contents().containsKey(itemId)) {
				throw new IllegalStateException("Game item codec did not load " + itemId + " from " + pack.name());
			}
			ModelDiscovery discovery = new ModelDiscovery(parsedModels, MissingCuboidModel.missingModel());
			discovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
			itemInfos.contents().values().forEach(info -> discovery.addRoot(info.model()));
			Map<Identifier, ResolvedModel> resolved = discovery.resolve();
			ResolvedModel itemModel = resolved.get(Identifier.fromNamespaceAndPath("minecraft", "item/" + stem));
			if (itemModel == null) throw new IllegalStateException("Game dependency loader did not resolve item/" + stem);
			double ground = itemModel.getTopTransforms().getTransform(ItemDisplayContext.GROUND).scale().x();
			double first = itemModel.getTopTransforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).scale().x();
			if (Math.abs(ground - expectedGroundScale) > 1.0e-5 || Math.abs(first - expectedFirstPersonScale) > 1.0e-5) {
				throw new IllegalStateException("Resolved " + stem + " transforms are wrong in " + pack.name()
					+ ": ground=" + ground + ", first-person=" + first);
			}
			if (pack.name().toLowerCase().contains("purebdcraft") && stem.equals("torch")) {
				verifyPureTorchMatrices(itemModel.getTopTransforms());
			}
		} finally {
			// MultiPackResourceManager.close would also close the converted corpus pack, which is
			// deliberately reused by the remaining checks in this short-lived verifier process.
			modern.close();
		}
	}

	/** Compares the complete old and new renderer paths, including their different centering/scales. */
	private static void verifyPureTorchMatrices(ItemTransforms current) {
		Matrix4f center = new Matrix4f().translate(-.5f, -.5f, -.5f);
		Matrix4f firstOld = new Matrix4f().rotateY(rad(45)).scale(.4f).scale(2)
			.mul(legacy(0, -131, 0, 0, 5, 1, 1.6f)).scale(.5f).mul(center);
		assertMatrix("first person", firstOld,
			modern(current.getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)));

		Matrix4f oldThirdBase = new Matrix4f().translate(-1f / 16, 7f / 16, 1f / 16);
		Matrix4f newThirdBase = new Matrix4f().rotateX(rad(-90)).rotateY(rad(180))
			.translate(1f / 16, 2f / 16, -10f / 16);
		Matrix4f thirdOld = oldThirdBase.scale(2)
			.mul(legacy(0, 90, -160, 0, -.2f, -4.5f, .85f)).scale(.5f).mul(center);
		Matrix4f thirdNew = newThirdBase.mul(modern(current.getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)));
		assertMatrix("third person", thirdOld, thirdNew);

		Matrix4f oldGuiBase = new Matrix4f().scale(1, 1, -1).scale(.5f).scale(40)
			.rotateX(rad(210)).rotateY(rad(-135));
		Matrix4f newGuiBase = new Matrix4f().scale(16, -16, 16);
		Matrix4f guiOld = oldGuiBase.mul(legacy(30, -45, 0, -2.5f, -2, 0, 1.6f)).scale(.5f).mul(center);
		Matrix4f guiNew = newGuiBase.mul(modern(current.getTransform(ItemDisplayContext.GUI)));
		assertMatrix("GUI", guiOld, guiNew);

		Matrix4f groundOld = new Matrix4f().scale(.5f)
			.mul(legacy(0, 0, 0, 0, 0, 0, 1.5f)).scale(.5f).mul(center);
		assertMatrix("ground", groundOld, modern(current.getTransform(ItemDisplayContext.GROUND)));

		Matrix4f fixedOld = new Matrix4f().scale(.5f).scale(.5f).mul(center);
		Matrix4f fixedNew = new Matrix4f().scale(.5f)
			.mul(modern(current.getTransform(ItemDisplayContext.FIXED)));
		assertMatrix("fixed", fixedOld, fixedNew);

		Matrix4f headOld = new Matrix4f().scale(2)
			.mul(legacy(0, -90, 0, 0, 5.5f, -6, 1.5f)).scale(.5f).mul(center);
		assertMatrix("head", headOld, modern(current.getTransform(ItemDisplayContext.HEAD)));
	}

	private static Matrix4f legacy(float rx, float ry, float rz, float tx, float ty, float tz, float scale) {
		return new Matrix4f().translate(tx / 16, ty / 16, tz / 16).rotateY(rad(ry)).rotateX(rad(rx))
			.rotateZ(rad(rz)).scale(scale);
	}

	private static Matrix4f modern(ItemTransform transform) {
		return new Matrix4f().translate(transform.translation()).rotateXYZ(
			transform.rotation().x() * rad(1), transform.rotation().y() * rad(1), transform.rotation().z() * rad(1)
		).scale(transform.scale()).translate(-.5f, -.5f, -.5f);
	}

	private static float rad(float degrees) {
		return degrees * (float) Math.PI / 180;
	}

	private static void assertMatrix(String context, Matrix4f expected, Matrix4f actual) {
		if (!expected.equals(actual, 3.0e-4f)) {
			throw new IllegalStateException(context + " matrix does not preserve the 1.8.9 pose\nexpected:\n"
				+ expected + "actual:\n" + actual);
		}
	}

	private static void verifyTrapdoorGeometry(LabPack pack) {
		for (String wood : List.of("spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped", "pale_oak")) {
			JsonObject model = model(pack, "models/block/" + wood + "_trapdoor_bottom.json");
			if (!"block/template_trapdoor_bottom".equals(model.get("parent").getAsString())) {
				throw new IllegalStateException("Derived " + wood + " trapdoor did not retain the custom template parent");
			}
			JsonObject textures = model.getAsJsonObject("textures");
			if (textures == null || !("minecraft:block/" + wood + "_trapdoor").equals(textures.get("texture").getAsString())) {
				throw new IllegalStateException("Derived " + wood + " trapdoor did not bind its derived texture");
			}
		}
	}

	private static void verifyRabbitSource(LabPack pack) {
		var texture = pack.texture("entity/rabbit/brown").orElseThrow(() -> new IllegalStateException("Legacy rabbit texture did not resolve"));
		if (texture.getWidth() % 64 != 0 || texture.getHeight() % 32 != 0) {
			throw new IllegalStateException("Legacy rabbit texture is not on the classic 64×32 canvas");
		}
	}

	private static void verifyChestConversion(LabPack pack) {
		for (String stem : List.of("normal", "normal_left", "normal_right", "trapped", "trapped_left", "trapped_right", "christmas_left", "christmas_right", "ender")) {
			var texture = pack.texture("entity/chest/" + stem).orElseThrow(() -> new IllegalStateException("Converted chest texture missing: " + stem));
			if (texture.getWidth() != texture.getHeight() || texture.getWidth() % 64 != 0) {
				throw new IllegalStateException("Converted chest texture has an invalid canvas: " + stem);
			}
		}
	}

	private static void verifyLegacyVariants(LabPack pack) {
		for (String texture : List.of(
			"block/wither_rose", "item/knowledge_book", "entity/enderdragon/dragon_fireball",
			"item/glow_ink_sac", "item/glow_item_frame", "block/glow_item_frame",
			"entity/squid/glow_squid", "entity/squid/glow_squid_baby",
			"entity/cow/mooshroom_brown", "entity/cow/mooshroom_brown_baby",
			"item/leather_horse_armor", "item/netherite_horse_armor", "entity/horse/armor/horse_armor_leather",
			"entity/horse/armor/horse_armor_netherite", "block/copper_trapdoor", "block/nether_gold_ore"
		)) {
			if (pack.texture(texture).isEmpty()) {
				throw new IllegalStateException("Legacy variant texture did not resolve: " + texture);
			}
		}
	}

	private static void verifyVariantRecolours(LabPack pack) {
		verifyHue(pack.texture("entity/cow/mooshroom_brown").orElseThrow(), .025, .14, "brown mooshroom");
		verifyHue(pack.texture("entity/enderdragon/dragon_fireball").orElseThrow(), .67, .86, "dragon fireball");
		verifyHue(pack.texture("item/knowledge_book").orElseThrow(), .22, .43, "knowledge book");
		int darkPetals = 0;
		for (int pixel : pack.texture("block/wither_rose").orElseThrow().getRGB(0, 0, 128, 128, null, 0, 128)) {
			if ((pixel >>> 24) != 0 && ((pixel >>> 16) & 255) < 55 && ((pixel >>> 8) & 255) < 55 && (pixel & 255) < 55) darkPetals++;
		}
		if (darkPetals == 0) throw new IllegalStateException("Derived wither rose contains no dark bloom pixels");
	}

	private static void verifyRailSlopeModels(LabPack pack) {
		for (String stem : List.of("rail_raised_ne", "rail_raised_sw")) {
			var text = pack.text("models/block/" + stem + ".json");
			// This pack does not override the legacy wrapper, in which case leaving vanilla's finished
			// model alone is correct. Packs that do must get the non-cyclic template parent and sprite.
			if (text.isEmpty()) continue;
			JsonObject rail = JsonParser.parseString(text.get()).getAsJsonObject();
			if (!("minecraft:block/template_" + stem).equals(rail.get("parent").getAsString())
				|| !"minecraft:block/rail".equals(rail.getAsJsonObject("textures").get("rail").getAsString())) {
				throw new IllegalStateException("Raised rail was not converted into a finished, bound slope model: " + stem);
			}
		}
	}

	private static void verifySigns(LabPack pack) {
		for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped", "pale_oak")) {
			var sign = pack.texture("block/" + wood + "_sign").orElseThrow(() -> new IllegalStateException("Missing derived " + wood + " sign"));
			if (sign.getWidth() != sign.getHeight() || sign.getWidth() % 32 != 0) throw new IllegalStateException("Invalid sign UV canvas for " + wood);
			if (!pack.listedPaths("textures/block").contains("textures/block/" + wood + "_sign.png")) throw new IllegalStateException("Sign atlas did not announce " + wood);
		}
	}

	private static void verifyPlayerSkins(LabPack pack) {
		for (String arm : List.of("slim", "wide")) for (String skin : List.of("steve", "alex")) {
			var image = pack.texture("entity/player/" + arm + "/" + skin).orElseThrow(() -> new IllegalStateException("Missing legacy " + arm + " " + skin + " skin"));
			if (image.getWidth() != image.getHeight()) throw new IllegalStateException("Player skin was not upgraded to a square canvas: " + arm + "/" + skin);
		}
	}

	private static void verifyTripwire(LabPack pack) {
		var texture = pack.texture("block/tripwire");
		if (texture.isEmpty() || !pack.listedPaths("textures/block").contains("textures/block/tripwire.png")) {
			throw new IllegalStateException("Tripwire texture is not both served and announced to the block atlas");
		}
		var image = texture.get();
		int scale = image.getWidth() / 16;
		boolean sampledBandOpaque = false;
		for (int y = 4 * scale; y < 6 * scale && !sampledBandOpaque; y++) for (int x = 0; x < image.getWidth(); x++) {
			if ((image.getRGB(x, y) >>> 24) != 0) { sampledBandOpaque = true; break; }
		}
		if (!sampledBandOpaque) throw new IllegalStateException("Tripwire's current-model UV band remains transparent");
	}

	private static void verifyBeaconBeam(LabPack pack) {
		var beam = pack.texture("entity/beacon/beacon_beam").orElseThrow(() -> new IllegalStateException("Legacy beacon beam did not resolve"));
		for (int pixel : beam.getRGB(0, 0, beam.getWidth(), beam.getHeight(), null, 0, beam.getWidth())) {
			if ((pixel >>> 24) != 255) throw new IllegalStateException("Beacon beam still contains transparent-black background pixels");
		}
	}

	private static void verifyHue(java.awt.image.BufferedImage image, double min, double max, String name) {
		int matching = 0;
		for (int pixel : image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) {
			if ((pixel >>> 24) == 0) continue;
			float[] hsb = Color.RGBtoHSB((pixel >>> 16) & 255, (pixel >>> 8) & 255, pixel & 255, null);
			if (hsb[1] >= .25 && hsb[0] >= min && hsb[0] <= max) matching++;
		}
		if (matching == 0) throw new IllegalStateException("Derived " + name + " contains no expected recoloured pixels");
	}

	private static JsonObject model(LabPack pack, String path) {
		return pack.text(path).map(JsonParser::parseString).filter(element -> element.isJsonObject()).map(element -> element.getAsJsonObject())
			.orElseThrow(() -> new IllegalStateException("Missing converted model " + path));
	}

	private static double number(JsonArray values, int index) {
		return values.get(index).getAsDouble();
	}
}
