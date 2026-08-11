package dev.oery.legacyresources.lab;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Ensures the converted chest atlas exposes corrected single and synthesized double-chest sheets. */
public final class ChestCompatibilityVerifier {
	private ChestCompatibilityVerifier() { }

	public static void main(String[] args) {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path packs = Path.of(System.getProperty("lab.packs", System.getProperty("user.home") + "/.minecraft/resourcepacks"));
		LabPack bdcraft = PackCorpus.load(packs, project).packs().stream()
			.filter(pack -> pack.name().toLowerCase().contains("purebdcraft")).findFirst()
			.orElseThrow(() -> new IllegalStateException("PureBDcraft is required for this regression check"));
		for (String stem : List.of("normal", "normal_left", "normal_right", "trapped", "trapped_left", "trapped_right", "christmas_left", "christmas_right", "ender")) {
			var texture = bdcraft.texture("entity/chest/" + stem).orElseThrow(() -> new IllegalStateException("Converted chest texture missing: " + stem));
			if (texture.getWidth() != texture.getHeight() || texture.getWidth() % 64 != 0) {
				throw new IllegalStateException("Converted chest texture has an invalid canvas: " + stem);
			}
		}
		System.out.println("Verified converted chest atlas against " + bdcraft.name());
	}
}
