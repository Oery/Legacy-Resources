package dev.oery.legacyresources.lab;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBootstrap;
import net.minecraft.server.Bootstrap;

/** Drives every legacy particle-sheet mapping through the real converted pack and atlas listing. */
public final class ParticleCompatibilityVerifier {
	private static final int PARTICLE_GRID = 16;
	private static final int EXPLOSION_GRID = 4;

	private ParticleCompatibilityVerifier() { }

	public static void main(String[] args) {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
		ClientBootstrap.bootstrap();
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path packs = Path.of(System.getProperty("lab.packs", System.getProperty("user.home") + "/.minecraft/resourcepacks"));
		PackCorpus corpus = PackCorpus.load(packs, project);
		LabPack control = corpus.control();
		if (control == null) throw new IllegalStateException("1.8.9 vanilla control is required");
		LabPack bdcraft = corpus.packs().stream()
			.filter(pack -> pack.name().toLowerCase().contains("purebdcraft")).findFirst()
			.orElseThrow(() -> new IllegalStateException("PureBDcraft is required for the HD regression check"));

		verifyEveryMapping(control);
		verifyHdMappings(bdcraft);
		CorpusResult result = verifyCorpus(corpus);
		control.close();
		System.out.println("Verified all vanilla particle mappings, representative HD crops, and "
			+ result.particleSheets + " particle / " + result.explosionSheets + " explosion sheets across "
			+ result.packs + " legacy packs");
	}

	private static CorpusResult verifyCorpus(PackCorpus corpus) {
		int packs = 0;
		int particleSheets = 0;
		int explosionSheets = 0;
		for (LabPack pack : corpus.packs()) {
			packs++;
			try {
				BufferedImage particles = pack.texture("particle/particles").orElse(null);
				BufferedImage explosion = pack.texture("entity/explosion").orElse(null);
				boolean validParticles = particles != null
					&& particles.getWidth() % PARTICLE_GRID == 0 && particles.getHeight() % PARTICLE_GRID == 0;
				boolean validExplosion = explosion != null
					&& explosion.getWidth() % EXPLOSION_GRID == 0 && explosion.getHeight() % EXPLOSION_GRID == 0;
				if (!validParticles && !validExplosion) continue;

				// This is the game's actual discovery route. It forces every computed sprite through the
				// wrapped pack, while the exact-pixel assertions below sample every source family.
				Set<String> listed = pack.listedPaths("textures/particle");
				if (validParticles) {
					particleSheets++;
					assertParticleCell(pack, listed, "generic_7", 7);
					assertParticleCell(pack, listed, "effect_4", 132);
					assertParticleCell(pack, listed, "sga_m", 237);
					assertCrop(pack, listed, "particle/flash", "particle/particles",
						PARTICLE_GRID, PARTICLE_GRID, 4, 2, 4, 4);
					assertCrop(pack, null, "entity/fishing/fishing_hook", "particle/particles",
						PARTICLE_GRID, PARTICLE_GRID, 1, 2, 1, 1);
				}
				if (validExplosion) {
					explosionSheets++;
					assertCrop(pack, listed, "particle/explosion_9", "entity/explosion",
						EXPLOSION_GRID, EXPLOSION_GRID, 1, 2, 1, 1);
				}
			} finally {
				pack.close();
			}
		}
		if (packs == 0 || particleSheets == 0 || explosionSheets == 0) {
			throw new IllegalStateException("Particle corpus coverage is empty: packs=" + packs
				+ ", particle sheets=" + particleSheets + ", explosion sheets=" + explosionSheets);
		}
		return new CorpusResult(packs, particleSheets, explosionSheets);
	}

	private record CorpusResult(int packs, int particleSheets, int explosionSheets) { }

	private static void verifyEveryMapping(LabPack pack) {
		Set<String> listed = pack.listedPaths("textures/particle");
		for (int frame = 0; frame < 8; frame++) assertParticleCell(pack, listed, "generic_" + frame, frame);
		for (int frame = 0; frame < 4; frame++) assertParticleCell(pack, listed, "splash_" + frame, 19 + frame);
		assertParticleCell(pack, listed, "bubble", 32);
		assertParticleCell(pack, listed, "flame", 48);
		assertParticleCell(pack, listed, "lava", 49);
		assertParticleCell(pack, listed, "note", 64);
		assertParticleCell(pack, listed, "critical_hit", 65);
		assertParticleCell(pack, listed, "enchanted_hit", 66);
		assertParticleCell(pack, listed, "heart", 80);
		assertParticleCell(pack, listed, "angry", 81);
		assertParticleCell(pack, listed, "glint", 82);
		assertParticleCell(pack, listed, "drip_fall", 112);
		assertParticleCell(pack, listed, "drip_hang", 113);
		assertParticleCell(pack, listed, "drip_land", 114);
		for (int frame = 0; frame < 8; frame++) assertParticleCell(pack, listed, "effect_" + frame, 128 + frame);
		for (int frame = 0; frame < 8; frame++) assertParticleCell(pack, listed, "spell_" + frame, 144 + frame);
		for (int frame = 0; frame < 8; frame++) assertParticleCell(pack, listed, "spark_" + frame, 160 + frame);
		for (int letter = 0; letter < 26; letter++) {
			assertParticleCell(pack, listed, "sga_" + (char) ('a' + letter), 225 + letter);
		}
		assertCrop(pack, listed, "particle/flash", "particle/particles", PARTICLE_GRID, PARTICLE_GRID, 4, 2, 4, 4);
		for (int frame = 0; frame < 16; frame++) {
			assertCrop(pack, listed, "particle/explosion_" + frame, "entity/explosion",
				EXPLOSION_GRID, EXPLOSION_GRID, frame % 4, frame / 4, 1, 1);
		}
		assertCrop(pack, null, "entity/fishing/fishing_hook", "particle/particles",
			PARTICLE_GRID, PARTICLE_GRID, 1, 2, 1, 1);
	}

	private static void verifyHdMappings(LabPack pack) {
		Set<String> listed = pack.listedPaths("textures/particle");
		assertParticleCell(pack, listed, "generic_7", 7);
		assertParticleCell(pack, listed, "splash_2", 21);
		assertParticleCell(pack, listed, "effect_4", 132);
		assertParticleCell(pack, listed, "spell_4", 148);
		assertParticleCell(pack, listed, "spark_4", 164);
		assertParticleCell(pack, listed, "sga_m", 237);
		assertCrop(pack, listed, "particle/flash", "particle/particles", PARTICLE_GRID, PARTICLE_GRID, 4, 2, 4, 4);
		assertCrop(pack, listed, "particle/explosion_9", "entity/explosion", EXPLOSION_GRID, EXPLOSION_GRID, 1, 2, 1, 1);
		assertCrop(pack, null, "entity/fishing/fishing_hook", "particle/particles", PARTICLE_GRID, PARTICLE_GRID, 1, 2, 1, 1);
	}

	private static void assertParticleCell(LabPack pack, Set<String> listed, String stem, int index) {
		assertCrop(pack, listed, "particle/" + stem, "particle/particles",
			PARTICLE_GRID, PARTICLE_GRID, index % PARTICLE_GRID, index / PARTICLE_GRID, 1, 1);
	}

	private static void assertCrop(
		LabPack pack, Set<String> listed, String target, String source, int columns, int rows,
		int column, int row, int widthCells, int heightCells
	) {
		BufferedImage sheet = pack.texture(source)
			.orElseThrow(() -> new IllegalStateException(pack.name() + " is missing source " + source));
		if (sheet.getWidth() % columns != 0 || sheet.getHeight() % rows != 0) {
			throw new IllegalStateException(pack.name() + " has a non-integral " + columns + "x" + rows + " source grid for " + source);
		}
		int cellWidth = sheet.getWidth() / columns;
		int cellHeight = sheet.getHeight() / rows;
		int x = column * cellWidth;
		int y = row * cellHeight;
		int width = widthCells * cellWidth;
		int height = heightCells * cellHeight;
		BufferedImage actual = pack.texture(target)
			.orElseThrow(() -> new IllegalStateException(pack.name() + " did not resolve " + target));
		if (actual.getWidth() != width || actual.getHeight() != height) {
			throw new IllegalStateException(pack.name() + " served " + target + " at " + actual.getWidth() + "x" + actual.getHeight()
				+ " instead of " + width + "x" + height);
		}
		int[] expected = sheet.getRGB(x, y, width, height, null, 0, width);
		int[] found = actual.getRGB(0, 0, width, height, null, 0, width);
		if (!java.util.Arrays.equals(expected, found)) {
			throw new IllegalStateException(pack.name() + " served pixels other than the exact legacy crop for " + target);
		}
		String listedPath = "textures/" + target + ".png";
		if (listed != null && !listed.contains(listedPath)) {
			throw new IllegalStateException(pack.name() + " resolves but does not announce " + listedPath);
		}
	}
}
