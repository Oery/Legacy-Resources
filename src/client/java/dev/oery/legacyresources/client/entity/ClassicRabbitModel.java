package dev.oery.legacyresources.client.entity;

import java.util.Set;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;

/** 1.8.9 rabbit geometry and 64×32 UV layout, including its classic hop pose. */
final class ClassicRabbitModel extends EntityModel<RabbitRenderState> {
	private final ModelPart leftFoot, rightFoot, leftThigh, rightThigh, body, leftArm, rightArm, head, leftEar, rightEar, tail, nose;

	ClassicRabbitModel(ModelPart root) {
		super(root);
		leftFoot = root.getChild("left_foot"); rightFoot = root.getChild("right_foot");
		leftThigh = root.getChild("left_thigh"); rightThigh = root.getChild("right_thigh");
		body = root.getChild("body"); leftArm = root.getChild("left_arm"); rightArm = root.getChild("right_arm");
		head = root.getChild("head"); leftEar = root.getChild("left_ear"); rightEar = root.getChild("right_ear");
		tail = root.getChild("tail"); nose = head.getChild("nose");
	}

	static LayerDefinition createLayer(boolean baby) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(26, 24).addBox(-1, 5.5f, -3.7f, 2, 1, 7), PartPose.offset(3, 17.5f, 3.7f));
		root.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(8, 24).addBox(-1, 5.5f, -3.7f, 2, 1, 7), PartPose.offset(-3, 17.5f, 3.7f));
		root.addOrReplaceChild("left_thigh", CubeListBuilder.create().texOffs(30, 15).addBox(-1, 0, 0, 2, 4, 5), PartPose.offsetAndRotation(3, 17.5f, 3.7f, -.34906584f, 0, 0));
		root.addOrReplaceChild("right_thigh", CubeListBuilder.create().texOffs(16, 15).addBox(-1, 0, 0, 2, 4, 5), PartPose.offsetAndRotation(-3, 17.5f, 3.7f, -.34906584f, 0, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -10, 6, 5, 10), PartPose.offsetAndRotation(0, 19, 8, -.34906584f, 0, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(8, 15).addBox(-1, 0, -1, 2, 7, 2), PartPose.offsetAndRotation(3, 17, -1, -.17453292f, 0, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 15).addBox(-1, 0, -1, 2, 7, 2), PartPose.offsetAndRotation(-3, 17, -1, -.17453292f, 0, 0));
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0).addBox(-2.5f, -4, -5, 5, 4, 5), PartPose.offset(0, 16, -1));
		root.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(52, 0).addBox(-2.5f, -9, -1, 2, 5, 1), PartPose.offset(0, 16, -1));
		root.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(58, 0).addBox(.5f, -9, -1, 2, 5, 1), PartPose.offset(0, 16, -1));
		head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(32, 9).addBox(-.5f, -2.5f, -5.5f, 1, 1, 1), PartPose.ZERO);
		root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(52, 6).addBox(-1.5f, -1.5f, 0, 3, 3, 2), PartPose.offsetAndRotation(0, 20, 7, -.3490659f, 0, 0));
		LayerDefinition layer = LayerDefinition.create(mesh, 64, 32);
		return baby ? layer.apply(new BabyModelTransform(false, 5, 2, Set.of("head", "right_ear", "left_ear"))) : layer;
	}

	@Override public void setupAnim(RabbitRenderState state) {
		super.setupAnim(state);
		float pitch = state.xRot * ((float) Math.PI / 180);
		float yaw = state.yRot * ((float) Math.PI / 180);
		head.xRot = pitch; head.yRot = yaw;
		rightEar.xRot = leftEar.xRot = pitch;
		rightEar.yRot = yaw - .2617994f; leftEar.yRot = yaw + .2617994f;
		float jump = (float) Math.sin(state.jumpCompletion * Math.PI);
		leftThigh.xRot = rightThigh.xRot = (jump * 50 - 21) * ((float) Math.PI / 180);
		leftFoot.xRot = rightFoot.xRot = jump * 50 * ((float) Math.PI / 180);
		leftArm.xRot = rightArm.xRot = (jump * -40 - 11) * ((float) Math.PI / 180);
	}
}
