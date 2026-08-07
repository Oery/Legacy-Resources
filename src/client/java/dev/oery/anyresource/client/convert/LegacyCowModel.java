package dev.oery.anyresource.client.convert;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Replicates the pre-variant-update cow model: same head/body/leg boxes and UV offsets as the
 * modern {@code CowModel}, minus the 3D muzzle and horn cubes added for the temperate/warm/cold
 * split (which need a 64x64 canvas), declared against the classic 64x32 canvas instead. Legacy
 * 1.6-1.12 cow textures are 64x32 and were never painted with muzzle/horn UV regions, so baking
 * them against the modern model scrambles every UV coordinate; this restores the exact classic
 * layout instead.
 */
public final class LegacyCowModel {
	private LegacyCowModel() {
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head",
			CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -4.0f, -6.0f, 8.0f, 8.0f, 6.0f),
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
		root.addOrReplaceChild("right_front_leg", rightLeg, PartPose.offset(-4.0f, 12.0f, -5.0f));
		root.addOrReplaceChild("left_front_leg", leftLeg, PartPose.offset(4.0f, 12.0f, -5.0f));
		return LayerDefinition.create(mesh, 64, 32);
	}
}
