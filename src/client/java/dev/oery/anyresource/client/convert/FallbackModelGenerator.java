package dev.oery.anyresource.client.convert;

import java.nio.charset.StandardCharsets;

/**
 * Builds minimal vanilla-compatible model/blockstate JSON for legacy packs that only ship
 * textures (pre-1.8 style packs, or textures whose model/blockstate got split up by the
 * 1.13 flattening). Blocks get a plain {@code cube_all} model, items a plain {@code generated}
 * (layered icon) model, matching PLAN.md's fallback model generator requirement.
 */
final class FallbackModelGenerator {
	private FallbackModelGenerator() {
	}

	static byte[] cubeAllModel(String namespace, String textureStem) {
		String json = "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"" + namespace + ":block/" + textureStem + "\"}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Same cube shape as {@link #cubeAllModel}, but through the {@code minecraft:block/leaves}
	 * parent, which applies {@code tintindex 0} (biome foliage color) to every face. Legacy leaf
	 * textures are greyscale, exactly like vanilla's, and rely on that tint for their color -
	 * without it they render washed-out grey instead of green.
	 */
	static byte[] leavesModel(String namespace, String textureStem) {
		String json = "{\"parent\":\"minecraft:block/leaves\",\"textures\":{\"all\":\"" + namespace + ":block/" + textureStem + "\"}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	static byte[] generatedItemModel(String namespace, String texturePath) {
		String json = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + namespace + ":" + texturePath + "\"}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	static byte[] singleVariantBlockstate(String namespace, String blockStem) {
		String json = "{\"variants\":{\"\":{\"model\":\"" + namespace + ":block/" + blockStem + "\"}}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Cube with distinct side/end textures, through {@code cube_column} (vertical) or
	 * {@code cube_column_horizontal} (for logs lying on their side). Used for legacy packs'
	 * log textures, which - like vanilla's - ship a separate bark ({@code side}) and end-grain
	 * ({@code end}) texture instead of the single texture a plain {@code cube_all} assumes.
	 */
	static byte[] pillarModel(String namespace, String sideStem, String endStem, boolean horizontal) {
		String parent = horizontal ? "minecraft:block/cube_column_horizontal" : "minecraft:block/cube_column";
		String json = "{\"parent\":\"" + parent + "\",\"textures\":{\"end\":\"" + namespace + ":block/" + endStem
			+ "\",\"side\":\"" + namespace + ":block/" + sideStem + "\"}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/** Axis-aware blockstate matching vanilla's own log/pillar blockstates. */
	static byte[] pillarBlockstate(String namespace, String blockStem) {
		String vertical = namespace + ":block/" + blockStem;
		String horizontal = namespace + ":block/" + blockStem + "_horizontal";
		String json = "{\"variants\":{"
			+ "\"axis=x\":{\"model\":\"" + horizontal + "\",\"x\":90,\"y\":90},"
			+ "\"axis=y\":{\"model\":\"" + vertical + "\"},"
			+ "\"axis=z\":{\"model\":\"" + horizontal + "\",\"x\":90}"
			+ "}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Torches (and redstone torches, wall-mounted or not) use vanilla's own thin billboard-style
	 * template models ({@code template_torch}, {@code template_torch_wall},
	 * {@code template_redstone_torch}, etc.) - never a full cube - keyed by a {@code "torch"}
	 * texture variable rather than {@code "all"}. Legacy packs never ship these models, and a
	 * plain {@code cube_all} fallback would render a solid block instead of a torch sprite.
	 */
	static byte[] torchModel(String namespace, String template, String textureStem) {
		String json = "{\"parent\":\"minecraft:block/" + template + "\",\"textures\":{\"torch\":\"" + namespace + ":block/" + textureStem + "\"}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/** Facing-aware blockstate matching vanilla's own wall_torch blockstate. */
	static byte[] wallTorchBlockstate(String namespace, String blockStem) {
		String model = namespace + ":block/" + blockStem;
		String json = "{\"variants\":{"
			+ "\"facing=east\":{\"model\":\"" + model + "\"},"
			+ "\"facing=north\":{\"model\":\"" + model + "\",\"y\":270},"
			+ "\"facing=south\":{\"model\":\"" + model + "\",\"y\":90},"
			+ "\"facing=west\":{\"model\":\"" + model + "\",\"y\":180}"
			+ "}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/** Lit-aware blockstate matching vanilla's own redstone_torch blockstate. */
	static byte[] litUnlitBlockstate(String namespace, String litStem, String unlitStem) {
		String lit = namespace + ":block/" + litStem;
		String unlit = namespace + ":block/" + unlitStem;
		String json = "{\"variants\":{"
			+ "\"lit=false\":{\"model\":\"" + unlit + "\"},"
			+ "\"lit=true\":{\"model\":\"" + lit + "\"}"
			+ "}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	/** Facing+lit-aware blockstate matching vanilla's own redstone_wall_torch blockstate. */
	static byte[] wallLitUnlitBlockstate(String namespace, String litStem, String unlitStem) {
		String lit = namespace + ":block/" + litStem;
		String unlit = namespace + ":block/" + unlitStem;
		String json = "{\"variants\":{"
			+ "\"facing=east,lit=false\":{\"model\":\"" + unlit + "\"},"
			+ "\"facing=east,lit=true\":{\"model\":\"" + lit + "\"},"
			+ "\"facing=north,lit=false\":{\"model\":\"" + unlit + "\",\"y\":270},"
			+ "\"facing=north,lit=true\":{\"model\":\"" + lit + "\",\"y\":270},"
			+ "\"facing=south,lit=false\":{\"model\":\"" + unlit + "\",\"y\":90},"
			+ "\"facing=south,lit=true\":{\"model\":\"" + lit + "\",\"y\":90},"
			+ "\"facing=west,lit=false\":{\"model\":\"" + unlit + "\",\"y\":180},"
			+ "\"facing=west,lit=true\":{\"model\":\"" + lit + "\",\"y\":180}"
			+ "}}";
		return json.getBytes(StandardCharsets.UTF_8);
	}
}
