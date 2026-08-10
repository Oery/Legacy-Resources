package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stripped logs for every overworld wood with a legacy source. There is no pre-1.13 stripped-log
 * art to copy: the side keeps the pack's own vertical log grain but moves it onto vanilla's exposed-
 * wood band, while the top keeps its end grain and paints only its source-inferred bark boundary with
 * that same side band.
 */
final class StrippedLogs implements Derivation {
	private record Band(double hue, double saturation, double mean, double spread) {
	}
	private record Wood(String name, Band side, Band top) {
		String source(String suffix) {
			return "block/" + name + "_log" + suffix;
		}
		String output(String suffix) {
			return "block/stripped_" + name + "_log" + suffix;
		}
	}

	/** Bands measured from reference/26.2's stripped logs; all are surfaced as Params below. */
	private static final List<Wood> WOODS = List.of(
		new Wood("oak", new Band(.106, .516, 146.9, 12.4), new Band(.106, .521, 132.6, 18.5)),
		new Wood("spruce", new Band(.100, .545, 92.8, 6.0), new Band(.097, .554, 89.8, 8.6)),
		new Wood("birch", new Band(.122, .400, 176.2, 10.9), new Band(.123, .392, 171.7, 17.2)),
		new Wood("jungle", new Band(.094, .508, 137.9, 8.0), new Band(.082, .509, 129.3, 10.3)),
		new Wood("acacia", new Band(.048, .657, 107.9, 4.8), new Band(.057, .688, 104.2, 7.9)),
		new Wood("dark_oak", new Band(.095, .503, 58.8, 4.7), new Band(.084, .645, 47.3, 6.0)),
		new Wood("cherry", new Band(.991, .329, 160.3, 6.9), new Band(.018, .291, 176.2, 21.5)),
		new Wood("pale_oak", new Band(.023, .036, 239.9, 9.2), new Band(.019, .042, 228.7, 17.6))
	);

	@Override
	public String id() {
		return "stripped_logs";
	}

	@Override
	public List<String> sources() {
		List<String> sources = new ArrayList<>();
		for (Wood wood : WOODS) {
			sources.add(wood.source(""));
			sources.add(wood.source("_top"));
		}
		return List.copyOf(sources);
	}

	@Override
	public List<String> outputs() {
		List<String> outputs = new ArrayList<>();
		for (Wood wood : WOODS) {
			outputs.add(wood.output(""));
			outputs.add(wood.output("_top"));
		}
		return List.copyOf(outputs);
	}

	@Override
	public List<Param> params() {
		List<Param> params = new ArrayList<>();
		for (Wood wood : WOODS) {
			addBand(params, wood.name() + "_side", wood.side());
			addBand(params, wood.name() + "_top", wood.top());
		}
		params.add(Param.ofInt("top_bark_search_width", 1, 8, 4));
		params.add(Param.of("max_gain", .5, 4, 2));
		params.add(Param.ofInt("levels", 0, 8, 0));
		return List.copyOf(params);
	}

	private static void addBand(List<Param> params, String prefix, Band band) {
		params.add(Param.of(prefix + "_hue", 0, 1, band.hue()));
		params.add(Param.of(prefix + "_saturation", 0, 1, band.saturation()));
		params.add(Param.of(prefix + "_brightness", 0, 255, band.mean()));
		params.add(Param.of(prefix + "_spread", 0, 64, band.spread()));
	}

	@Override
	public Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params) {
		Map<String, BufferedImage> derived = new LinkedHashMap<>();
		for (Wood wood : WOODS) {
			Palette sidePalette = palette(params, wood.name() + "_side");
			BufferedImage side = sources.get(wood.source(""));
			if (side != null && Ops.scaleOf(side) != 0) {
				derived.put(wood.output(""), repaint(sidePalette, side, params, wood.name() + "_side"));
			}
			BufferedImage top = sources.get(wood.source("_top"));
			if (top == null || Ops.scaleOf(top) == 0) {
				continue;
			}
			BufferedImage end = repaint(palette(params, wood.name() + "_top"), top, params, wood.name() + "_top");
			BufferedImage bark = repaint(sidePalette, top, params, wood.name() + "_side");
			derived.put(wood.output("_top"), LogTop.withBarkMask(end, bark, top, params.getInt("top_bark_search_width")));
		}
		return derived;
	}

	private static Palette palette(Params params, String prefix) {
		return new Palette(params.get(prefix + "_hue"), params.get(prefix + "_saturation"), params.get(prefix + "_brightness"));
	}

	private static BufferedImage repaint(Palette palette, BufferedImage source, Params params, String prefix) {
		return palette.repaint(source, params.get(prefix + "_spread"), params.get("max_gain"), params.getInt("levels"));
	}
}
