package dev.oery.legacyresources.client.derive;

import java.util.List;

/**
 * One of the sixteen dye colours as a band of concrete: where vanilla's own block for that colour sits
 * in hue, saturation, mean luminance and spread. Read by {@link ConcretePowder} and {@link Concrete}.
 * <p>
 * Every number here is measured off {@code reference/26.2}, not chosen. The two blocks are the same
 * sixteen colours at two contrasts: the powder is a granular field with a standard deviation of 6 to 14,
 * the hardened block is flat to within a deviation of 1 and sits between 0.38x (black) and 0.93x
 * (white) of the powder's brightness, slightly more saturated. Hue is a saturation-weighted circular
 * mean, the same statistic {@link DirtPath} takes off dirt.
 * <p>
 * What is <em>not</em> taken from vanilla is the pattern. The powder's noise field correlates with
 * vanilla's own sand at |r| &lt; 0.13 across all sixteen colours, so there is nothing there to copy even
 * if it were wanted; the grain in a derived block is the pack's own sand, and only the band it is
 * painted onto comes from this table.
 *
 * @param colour     the dye name, which is also the texture stem's prefix
 * @param hue        in degrees
 * @param saturation 0-1
 * @param mean       luminance the block averages to, 0-255
 * @param spread     standard deviation of that luminance
 */
record ConcreteColor(String colour, double hue, double saturation, double mean, double spread) {
	/** Mean luminance of vanilla's own sand, which {@code follow_source} measures a pack's against. */
	static final double VANILLA_SAND_MEAN = 206.6;

	static final List<ConcreteColor> POWDER = List.of(
		new ConcreteColor("white", 189.8, 0.009, 227.1, 8.24),
		new ConcreteColor("orange", 30.7, 0.860, 144.9, 10.31),
		new ConcreteColor("magenta", 304.5, 0.567, 114.2, 10.44),
		new ConcreteColor("light_blue", 194.1, 0.653, 160.5, 11.99),
		new ConcreteColor("yellow", 48.6, 0.765, 195.9, 8.63),
		new ConcreteColor("lime", 86.1, 0.779, 165.1, 7.98),
		new ConcreteColor("pink", 338.0, 0.331, 171.4, 14.30),
		new ConcreteColor("gray", 209.0, 0.093, 80.5, 5.77),
		new ConcreteColor("light_gray", 60.6, 0.044, 154.5, 9.67),
		new ConcreteColor("cyan", 184.6, 0.767, 125.0, 9.07),
		new ConcreteColor("purple", 277.5, 0.687, 80.7, 5.99),
		new ConcreteColor("blue", 238.1, 0.580, 79.4, 6.28),
		new ConcreteColor("brown", 26.0, 0.572, 91.3, 7.43),
		new ConcreteColor("green", 77.7, 0.622, 109.1, 7.34),
		new ConcreteColor("red", 1.8, 0.699, 78.1, 6.00),
		new ConcreteColor("black", 226.5, 0.225, 26.8, 7.89)
	);

	static final List<ConcreteColor> HARDENED = List.of(
		new ConcreteColor("white", 188.6, 0.033, 211.9, 0.81),
		new ConcreteColor("orange", 25.9, 0.997, 117.2, 0.89),
		new ConcreteColor("magenta", 305.0, 0.714, 82.1, 0.97),
		new ConcreteColor("light_blue", 202.7, 0.821, 119.9, 0.86),
		new ConcreteColor("yellow", 42.1, 0.911, 178.2, 0.96),
		new ConcreteColor("lime", 91.1, 0.855, 142.5, 1.01),
		new ConcreteColor("pink", 337.7, 0.527, 127.9, 0.86),
		new ConcreteColor("gray", 214.3, 0.114, 57.3, 0.53),
		new ConcreteColor("light_gray", 60.0, 0.080, 124.3, 0.73),
		new ConcreteColor("cyan", 188.9, 0.843, 99.7, 0.67),
		new ConcreteColor("purple", 273.1, 0.798, 55.3, 0.65),
		new ConcreteColor("blue", 238.8, 0.689, 53.1, 0.60),
		new ConcreteColor("brown", 25.9, 0.672, 65.4, 0.55),
		new ConcreteColor("green", 79.6, 0.600, 83.6, 0.63),
		new ConcreteColor("red", 0.0, 0.770, 56.1, 0.83),
		new ConcreteColor("black", 222.9, 0.456, 10.3, 1.00)
	);

	/**
	 * This colour as something {@link Palette#repaint} can paint onto.
	 *
	 * @param saturationScale multiplier on the measured saturation
	 * @param brightness      multiplier on the measured mean; see {@code follow_source} in
	 *                        {@link ConcretePowder#params}
	 */
	Palette palette(double saturationScale, double brightness) {
		return new Palette(hue / 360.0, saturation * saturationScale, mean * brightness);
	}
}
