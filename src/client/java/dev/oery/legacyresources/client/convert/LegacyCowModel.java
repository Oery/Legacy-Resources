package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Replicates the pre-variant-update cow model, verified against the actual obfuscated 1.8.9
 * {@code ModelCow} (decompiled from a real {@code 1.8.9.jar}): same head/body/leg boxes and UV
 * offsets as the modern {@code CowModel} - including the horns, which turned out to already be
 * present in 1.8.9 at the same UV offset ({@code texOffs(22, 0)}, boxes {@code (-5,-5,-4,1,3,1)}
 * / {@code (4,-5,-4,1,3,1)} in head-local space) - minus the 3D muzzle cube added for the
 * temperate/warm/cold split (which needs a 64x64 canvas; 1.8.9's canvas, and this one, is
 * 64x32). The front legs' Z offset is -6, not the modern model's -5: 1.8.9's ModelQuadruped base
 * placed them at -5 then nudged them by -1 at construction, a tweak the modern rewrite dropped.
 */
public final class LegacyCowModel {
	private LegacyCowModel() {
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head",
			CubeListBuilder.create()
				.texOffs(0, 0).addBox(-4.0f, -4.0f, -6.0f, 8.0f, 8.0f, 6.0f)
				.texOffs(22, 0).addBox("right_horn", -5.0f, -5.0f, -4.0f, 1.0f, 3.0f, 1.0f)
				.texOffs(22, 0).addBox("left_horn", 4.0f, -5.0f, -4.0f, 1.0f, 3.0f, 1.0f),
			PartPose.offset(0.0f, 4.0f, -8.0f));
		root.addOrReplaceChild("body",
			CubeListBuilder.create()
				.texOffs(18, 4).addBox(-6.0f, -10.0f, -7.0f, 12.0f, 18.0f, 10.0f)
				.texOffs(52, 0).addBox(-2.0f, 2.0f, -8.0f, 4.0f, 6.0f, 1.0f),
			PartPose.offsetAndRotation(0.0f, 5.0f, 2.0f, 1.5707964f, 0.0f, 0.0f));
		CubeListBuilder leftLeg = CubeListBuilder.create().mirror().texOffs(0, 16).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f);
		CubeListBuilder rightLeg = CubeListBuilder.create().texOffs(0, 16).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f);
		root.addOrReplaceChild("right_hind_leg", rightLeg, PartPose.offset(-4.0f, 12.0f, 7.0f));
		root.addOrReplaceChild("left_hind_leg", leftLeg, PartPose.offset(4.0f, 12.0f, 7.0f));
		root.addOrReplaceChild("right_front_leg", rightLeg, PartPose.offset(-4.0f, 12.0f, -6.0f));
		root.addOrReplaceChild("left_front_leg", leftLeg, PartPose.offset(4.0f, 12.0f, -6.0f));
		return LayerDefinition.create(mesh, 64, 32);
	}
}
