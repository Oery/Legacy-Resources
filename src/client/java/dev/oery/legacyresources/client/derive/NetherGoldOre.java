package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/** Replaces only a legacy gold ore's inferred stone host with the pack's netherrack. */
final class NetherGoldOre implements Derivation {
	private static final String STONE = "block/stone";
	private static final String GOLD = "block/gold_ore";
	private static final String NETHERRACK = "block/netherrack";
	private static final String OUTPUT = "block/nether_gold_ore";

	@Override public String id() { return "nether_gold_ore"; }
	@Override public List<String> sources() { return List.of(STONE, GOLD, NETHERRACK); }
	@Override public List<String> outputs() { return List.of(OUTPUT); }
	@Override public List<Param> params() {
		return List.of(Param.ofInt("host_difference", 0, 64, 8), Param.of("min_host_fraction", .25, .95, .25));
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		BufferedImage gold = sources.get(GOLD);
		BufferedImage stone = sources.get(STONE);
		BufferedImage netherrack = sources.get(NETHERRACK);
		if (gold == null || stone == null || netherrack == null || Ops.scaleOf(gold) == 0) return Map.of();
		BufferedImage host = Ops.resizeNearest(stone, gold.getWidth());
		BufferedImage nether = Ops.resizeNearest(netherrack, gold.getWidth());
		int[] goldPixels = Ops.pixels(gold), hostPixels = Ops.pixels(host), netherPixels = Ops.pixels(nether);
		int[] output = goldPixels.clone();
		int hostCount = 0, limit = params.getInt("host_difference");
		for (int i = 0; i < output.length; i++) {
			if (Ops.alpha(goldPixels[i]) == 0 || Ops.alpha(hostPixels[i]) == 0) continue;
			int dr = Ops.red(goldPixels[i]) - Ops.red(hostPixels[i]);
			int dg = Ops.green(goldPixels[i]) - Ops.green(hostPixels[i]);
			int db = Ops.blue(goldPixels[i]) - Ops.blue(hostPixels[i]);
			if (dr * dr + dg * dg + db * db <= limit * limit) {
				output[i] = netherPixels[i];
				hostCount++;
			}
		}
		if ((double) hostCount / output.length < params.get("min_host_fraction")) return Map.of();
		return Map.of(OUTPUT, Ops.image(output, gold.getWidth(), gold.getHeight()));
	}
}
