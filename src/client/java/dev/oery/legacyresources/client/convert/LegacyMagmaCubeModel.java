package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;

/** The pre-1.9 magma cube UV layout, which occupies a 64×32 rather than 64×64 sheet. */
public final class LegacyMagmaCubeModel {
	private LegacyMagmaCubeModel() { }

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int segment = 0; segment < 8; segment++) {
			int u = 0;
			int v = segment;
			if (segment == 2) { u = 24; v = 10; }
			if (segment == 3) { u = 24; v = 19; }
			root.addOrReplaceChild("cube" + segment, CubeListBuilder.create().texOffs(u, v)
				.addBox(-4, 16 + segment, -4, 8, 1, 8), PartPose.ZERO);
		}
		root.addOrReplaceChild("inside_cube", CubeListBuilder.create().texOffs(0, 16)
			.addBox(-2, 18, -2, 4, 4, 4), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}
}
