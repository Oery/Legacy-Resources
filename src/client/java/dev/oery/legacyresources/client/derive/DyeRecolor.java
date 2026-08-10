package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One of the dyes 1.8.9 had no item for, recoloured from one of the twelve it did. Subclassed per dye:
 * {@link WhiteDye}, {@link BlackDye}, {@link BlueDye}, {@link BrownDye}.
 * <p>
 * The flattening split four items in two. Before 1.13 there was no white dye, only bone meal, and
 * {@code dye_powder_white} is the art for it; likewise {@code dye_powder_black} is the ink sac,
 * {@code dye_powder_blue} lapis lazuli and {@code dye_powder_brown} cocoa beans. Modern Minecraft has
 * both members of each pair and draws them quite differently - the four dyes are heaps of powder like
 * their twelve siblings, while bone meal is a scatter of shards and lapis a polished blue stone. Handing
 * the legacy file to both, which is what the texture map used to do, meant a legacy pack rendered white
 * dye as bone shards and black dye as an ink sac. The legacy art now goes to the material it was drawn
 * for, and the dye is derived here.
 * <p>
 * That the derivation is sound comes from the twelve: in 26.2 all sixteen dyes agree on 215 to 244 of
 * their 256 alpha values, and in 1.8.9 the powder dyes are even more uniform, five of them
 * alpha-identical to magenta. They are one drawing repainted, in both eras, so repainting one more time
 * is exactly the operation {@link RampRecolor} performs - and with {@code keep_hue} at 0, nothing of the
 * source's own colour survives to give the choice of source away. Only its shading does.
 */
abstract class DyeRecolor extends RampRecolor {
	/**
	 * The twelve dyes 1.8.9 draws as a heap of powder, which is every colour except the four this
	 * derivation produces. Modern names; {@code light_gray_dye} resolves back to
	 * {@code dye_powder_silver} through the texture map like any other.
	 */
	private static final List<String> PILES = List.of(
		"magenta", "orange", "light_blue", "yellow", "lime", "pink",
		"gray", "light_gray", "cyan", "purple", "green", "red"
	);

	private final String dye;
	private final String source;
	private final Map<String, String> pieces;
	private final List<String> references;

	/**
	 * @param source the dye whose art is repainted, by colour name
	 * @param dye    the dye produced, by colour name
	 */
	protected DyeRecolor(String source, String dye) {
		this.dye = dye;
		this.source = texture(source);
		this.pieces = Map.of(texture(source), texture(dye));
		this.references = PILES.stream().filter(colour -> !colour.equals(source)).map(DyeRecolor::texture).toList();
	}

	@Override
	public final String id() {
		return dye + "_dye";
	}

	@Override
	protected final Map<String, String> pieces() {
		return pieces;
	}

	/** The pack's other eleven powder dyes, which is how {@link #metal} tells dye from backdrop. */
	@Override
	protected final List<String> references() {
		return references;
	}

	/**
	 * Which pixels are the dye itself, as against whatever the pack draws it sitting on.
	 * <p>
	 * Most packs draw a dye as a heap of powder and nothing else, so every opaque pixel is dye and this
	 * has no work to do. PureBDcraft draws each one as a heap resting on a folded sheet of paper, and
	 * recolouring the whole icon turned that paper white, or black, along with the powder.
	 * <p>
	 * The pack has already answered which pixels are which, in the same way {@link CopperTools} gets a
	 * tool's handle out of its other metal tiers: a dye's powder is what the pack <em>changed</em> between
	 * its twelve dyes, and the sheet under it is what it did not. Asked per pixel against the source dye,
	 * pooled over all eleven references so that two dyes drawn in similar colours cannot mask each other.
	 * <p>
	 * A tolerance rather than exact equality, because BDcraft's paper is not quite untouched - it catches
	 * a faint cast from the powder above it, so on an equality test two thirds of the sheet still reads as
	 * dye and a tan wedge of it comes out tinted. The split is not delicate: its paper moves by at most 5
	 * counts between dyes and its powder by 120 or more, so {@code dye_change} sits in an empty gap
	 * roughly 60 counts wide rather than on a cliff edge, and anywhere in that gap classifies the same
	 * 53% of the icon. Across the rest of the corpus the mask is inert - 58 of 60 packs with a comparable
	 * dye set keep every pixel, the 59th keeps 98.8% - which is what it should be, since they have no
	 * backdrop to exclude.
	 * <p>
	 * Compared on colour with alpha dropped, as {@link CopperTools} does and for the same reason: alpha on
	 * these icons is antialiasing coverage, not a statement about what the pixel is made of.
	 * <p>
	 * A pack whose dyes cannot be compared at all - it ships only the one, or the others are sized
	 * differently - gets no mask and has the whole icon recoloured, which is the behaviour this had
	 * before and is right for a pack that draws no backdrop. So does one whose dyes are identical files,
	 * which would otherwise mask out everything and leave {@link RampRecolor}'s ramp nothing to measure.
	 */
	@Override
	protected final Metal metal(Map<String, BufferedImage> sources, Params params) {
		boolean[] powder = powderOf(sources, params.get("dye_change"));
		if (powder == null) {
			return (path, index, argb) -> true;
		}
		return (path, index, argb) -> index >= powder.length || powder[index];
	}

	/**
	 * The source dye's pixels that move when the pack repaints it as another colour.
	 *
	 * @return {@code null} where the question cannot be put - no comparable reference, or an answer of
	 *         "no pixel is dye" - leaving the caller to recolour everything
	 */
	private boolean @Nullable [] powderOf(Map<String, BufferedImage> sources, double tolerance) {
		BufferedImage dye = sources.get(source);
		if (dye == null) {
			return null;
		}
		int[] pixels = Ops.pixels(dye);
		boolean[] powder = new boolean[pixels.length];
		boolean compared = false;
		boolean any = false;
		for (String path : references) {
			BufferedImage other = sources.get(path);
			if (other == null || other.getWidth() != dye.getWidth() || other.getHeight() != dye.getHeight()) {
				continue;
			}
			compared = true;
			int[] reference = Ops.pixels(other);
			for (int i = 0; i < pixels.length; i++) {
				if (!powder[i] && Ops.alpha(pixels[i]) != 0 && difference(pixels[i], reference[i]) >= tolerance) {
					powder[i] = true;
					any = true;
				}
			}
		}
		return compared && any ? powder : null;
	}

	/** The larger of the two pixels' per-channel colour differences, alpha ignored. */
	private static int difference(int argb, int other) {
		return Math.max(
			Math.abs(Ops.red(argb) - Ops.red(other)),
			Math.max(
				Math.abs(Ops.green(argb) - Ops.green(other)),
				Math.abs(Ops.blue(argb) - Ops.blue(other))
			)
		);
	}

	private static String texture(String colour) {
		return "item/" + colour + "_dye";
	}
}
