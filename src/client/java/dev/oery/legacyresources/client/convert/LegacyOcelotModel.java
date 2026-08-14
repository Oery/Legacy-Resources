package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.animal.feline.AdultOcelotModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.FelineRenderState;

/**
 * The classic 1.8.9 ocelot geometry with the 64×32 sheet, plus the legacy cub
 * transforms.  Adults get the exact legacy adult layout; cubs additionally carry the
 * 1.8.9 baby head group (1.5× head at a raised pivot) on top of the outer body-group
 * scaling applied by {@code LegacyEntityRenderPlan}.  The head group is re-applied here
 * because {@code Model.setupAnim} resets every part to its baked pose, wiping any pose
 * set earlier in the frame.
 */
public final class LegacyOcelotModel extends AdultOcelotModel {
	public LegacyOcelotModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(mesh(), 64, 32);
	}

	@Override
	public void setupAnim(FelineRenderState state) {
		super.setupAnim(state);
		if (state.isBaby) {
			head.x = 0.0f;
			head.y = 13.5f;
			head.z = -7.5f;
			head.xScale = head.yScale = head.zScale = 1.5f;
		}
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeDeformation g = CubeDeformation.NONE;
		root.addOrReplaceChild("head", CubeListBuilder.create().addBox("main", -2.5f, -2.0f, -3.0f, 5.0f, 4.0f, 5.0f, g).addBox("nose", -1.5f, 0.0f, -4.0f, 3, 2, 2, g, 0, 24).addBox("ear1", -2.0f, -3.0f, 0.0f, 1, 1, 2, g, 0, 10).addBox("ear2", 1.0f, -3.0f, 0.0f, 1, 1, 2, g, 6, 10), PartPose.offset(0.0f, 15.0f, -9.0f));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(20, 0).addBox(-2.0f, 3.0f, -8.0f, 4.0f, 16.0f, 6.0f, g), PartPose.offsetAndRotation(0.0f, 12.0f, -10.0f, 1.5707964f, 0.0f, 0.0f));
		root.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 15).addBox(-0.5f, 0.0f, 0.0f, 1.0f, 8.0f, 1.0f, g), PartPose.offsetAndRotation(0.0f, 15.0f, 8.0f, 0.9f, 0.0f, 0.0f));
		root.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(4, 15).addBox(-0.5f, 0.0f, 0.0f, 1.0f, 8.0f, 1.0f, g), PartPose.offset(0.0f, 20.0f, 14.0f));
		CubeListBuilder hindLeg = CubeListBuilder.create().texOffs(8, 13).addBox(-1.0f, 0.0f, 1.0f, 2.0f, 6.0f, 2.0f, g);
		root.addOrReplaceChild("left_hind_leg", hindLeg, PartPose.offset(1.1f, 18.0f, 5.0f));
		root.addOrReplaceChild("right_hind_leg", hindLeg, PartPose.offset(-1.1f, 18.0f, 5.0f));
		CubeListBuilder frontLeg = CubeListBuilder.create().texOffs(40, 0).addBox(-1.0f, 0.0f, 0.0f, 2.0f, 10.0f, 2.0f, g);
		root.addOrReplaceChild("left_front_leg", frontLeg, PartPose.offset(1.2f, 13.8f, -5.0f));
		root.addOrReplaceChild("right_front_leg", frontLeg, PartPose.offset(-1.2f, 13.8f, -5.0f));
		return mesh;
	}
}
