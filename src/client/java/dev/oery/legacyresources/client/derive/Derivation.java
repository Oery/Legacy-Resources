package dev.oery.legacyresources.client.derive;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Synthesizes textures a legacy pack never shipped, out of ones it did.
 * <p>
 * Modern Minecraft has a decade of blocks and items that no 1.8.9 pack has art for. Some of them are
 * close relatives of something the pack does have - suspicious gravel is gravel that has been dug
 * into, a copper ingot is an iron ingot in a different metal, a soul torch is a torch burning a
 * different colour - and look far better re-derived from the pack's own art than left as vanilla's,
 * which would sit in the world as an obvious foreign object among 300 restyled neighbours.
 * <p>
 * Implementations must hold to two rules:
 * <ul>
 *   <li><b>Resolution independence.</b> Packs ship 16px up to 512px art. Author every measurement in
 *   16px units and multiply by {@code source.getWidth() / 16}, the same way
 *   {@code LegacyGuiSprites.SHEET_BASE_SIZE} and {@code CHEST_BASE_CANVAS_SIZE} already do. Sources
 *   whose size isn't a clean multiple of 16 should be declined (see below).</li>
 *   <li><b>Decline rather than guess.</b> Returning an empty map - because a source is missing, is an
 *   animation strip, or is an odd size - makes the game fall back to vanilla's own texture, which is
 *   always better than a mangled one.</li>
 * </ul>
 * <p>
 * Constants live in {@link #params()} rather than inline so the derivation lab can sweep them across
 * the whole pack corpus; see {@link Param}.
 */
public interface Derivation {
	/** Stable identifier, used by the lab's URLs and its derivation picker. */
	String id();

	/**
	 * Every texture this derivation may read, as a modern path relative to {@code textures/} and
	 * without its extension, e.g. {@code "block/gravel"}.
	 * <p>
	 * These are resolved <em>through the legacy conversion</em>, not read raw out of the pack, so a
	 * source may itself be something the pack has no file for - {@code item/compass_00} is
	 * synthesized by {@code LegacyPackResources} from a legacy strip, and reads here exactly as any
	 * other texture would.
	 */
	List<String> sources();

	/** Every texture this derivation produces, in the same path form as {@link #sources()}. */
	List<String> outputs();

	List<Param> params();

	/**
	 * @param sources only those {@link #sources()} that resolved in this pack - an implementation must
	 *                cope with entries being absent rather than assume the map is complete
	 * @return the produced images keyed by {@link #outputs()} path; empty, or missing individual keys,
	 *         where this pack cannot be derived from (see the decline rule above)
	 */
	Map<String, BufferedImage> derive(Map<String, BufferedImage> sources, Params params);
}
