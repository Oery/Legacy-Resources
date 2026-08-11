package dev.oery.legacyresources.lab;

import dev.oery.legacyresources.client.convert.EntityTextureMappings;
import dev.oery.legacyresources.client.convert.LegacyEndermanModel;
import dev.oery.legacyresources.client.convert.LegacyWolfModel;
import dev.oery.legacyresources.client.entity.ClassicHorseModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;

/**
 * Exercises the shipping resource wrapper over vanilla 1.8.9 and every legacy zip in the lab
 * corpus.  Entity renderers fetch these images by identifier rather than through an atlas, so this
 * deliberately verifies {@code getResource}, which is the path the game takes.
 */
public final class EntityCompatibilityVerifier {
	private EntityCompatibilityVerifier() { }

	public static void main(String[] args) {
		SharedConstants.setVersion(DetectedVersion.BUILT_IN);
		Bootstrap.bootStrap();
		verifyModelTopologies();
		Path project = Path.of(System.getProperty("lab.project", "."));
		Path packs = Path.of(System.getProperty("lab.packs", System.getProperty("user.home") + "/.minecraft/resourcepacks"));
		PackCorpus corpus = PackCorpus.load(packs, project);
		List<String> failures = new ArrayList<>();
		if (corpus.control() == null) {
			throw new IllegalStateException("Missing reference/1.8.9/assets vanilla control");
		}
		verify("vanilla 1.8.9", corpus.control(), failures, true);
		for (LabPack pack : corpus.packs()) verify(pack.name(), pack, failures, false);
		if (!failures.isEmpty()) throw new IllegalStateException("Entity texture conversion failures:\n" + String.join("\n", failures));
		System.out.printf("Verified %d entity path mappings over vanilla 1.8.9 and %d legacy packs.%n",
			EntityTextureMappings.vanillaCompatibleMappings().size(), corpus.packs().size());
	}

	/** Bakes every custom mesh through Minecraft's own part hierarchy constructors. */
	private static void verifyModelTopologies() {
		new EndermanModel(LegacyEndermanModel.createBodyLayer().bakeRoot());
		new AdultWolfModel(LegacyWolfModel.createBodyLayer().bakeRoot());
		new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), false, true);
		new ClassicHorseModel(ClassicHorseModel.createLayer().bakeRoot(), true, true);
	}

	private static void verify(String name, LabPack pack, List<String> failures, boolean requireAll) {
		for (Map.Entry<String, String> mapping : EntityTextureMappings.vanillaCompatibleMappings().entrySet()) {
			boolean sourcePresent = pack.resolves(mapping.getValue());
			boolean targetPresent = pack.resolves(mapping.getKey());
			if (requireAll && !sourcePresent) failures.add(name + ": reference source missing " + mapping.getValue());
			if (sourcePresent && !targetPresent) failures.add(name + ": " + mapping.getKey() + " did not resolve from " + mapping.getValue());
		}
	}
}
