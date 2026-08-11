package dev.oery.legacyresources.client.entity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/** Exact 64x64 geometry and two-pose animation from 1.8.9's ModelBat. */
final class ClassicBatModel extends EntityModel<BatRenderState> {
	private final ModelPart head, body, rightWing, leftWing, outerRightWing, outerLeftWing;
	ClassicBatModel(ModelPart root) { super(root, RenderTypes::entityCutout); head=root.getChild("head"); body=root.getChild("body"); rightWing=body.getChild("right_wing"); leftWing=body.getChild("left_wing"); outerRightWing=rightWing.getChild("outer_right_wing"); outerLeftWing=leftWing.getChild("outer_left_wing"); }
	static LayerDefinition createLayer() {
		MeshDefinition mesh=new MeshDefinition(); PartDefinition root=mesh.getRoot();
		PartDefinition head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3,-3,-3,6,6,6),PartPose.ZERO);
		head.addOrReplaceChild("right_ear",CubeListBuilder.create().texOffs(24,0).addBox(-4,-6,-2,3,4,1),PartPose.ZERO);
		head.addOrReplaceChild("left_ear",CubeListBuilder.create().mirror().texOffs(24,0).addBox(1,-6,-2,3,4,1),PartPose.ZERO);
		PartDefinition body=root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,16).addBox(-3,4,-3,6,12,6).texOffs(0,34).addBox(-5,16,0,10,6,1),PartPose.ZERO);
		PartDefinition right=body.addOrReplaceChild("right_wing",CubeListBuilder.create().texOffs(42,0).addBox(-12,1,1.5f,10,16,1),PartPose.ZERO);
		right.addOrReplaceChild("outer_right_wing",CubeListBuilder.create().texOffs(24,16).addBox(-8,1,0,8,12,1),PartPose.offset(-12,1,1.5f));
		PartDefinition left=body.addOrReplaceChild("left_wing",CubeListBuilder.create().mirror().texOffs(42,0).addBox(2,1,1.5f,10,16,1),PartPose.ZERO);
		left.addOrReplaceChild("outer_left_wing",CubeListBuilder.create().mirror().texOffs(24,16).addBox(0,1,0,8,12,1),PartPose.offset(12,1,1.5f));
		return LayerDefinition.create(mesh,64,64);
	}
	@Override public void setupAnim(BatRenderState s) {
		super.setupAnim(s); float pitch=s.xRot*((float)Math.PI/180), yaw=s.yRot*((float)Math.PI/180);
		if(s.isResting){head.xRot=pitch;head.yRot=(float)Math.PI-yaw;head.zRot=(float)Math.PI;head.setPos(0,-2,0);body.xRot=(float)Math.PI;rightWing.setPos(-3,0,3);leftWing.setPos(3,0,3);rightWing.xRot=leftWing.xRot=-.15707964f;rightWing.yRot=-1.2566371f;leftWing.yRot=1.2566371f;outerRightWing.yRot=-1.7278761f;outerLeftWing.yRot=1.7278761f;}
		else {head.xRot=pitch;head.yRot=yaw;body.xRot=.7853982f+Mth.cos(s.ageInTicks*.1f)*.15f;rightWing.yRot=Mth.cos(s.ageInTicks*1.3f)*(float)Math.PI*.25f;leftWing.yRot=-rightWing.yRot;outerRightWing.yRot=rightWing.yRot*.5f;outerLeftWing.yRot=-rightWing.yRot*.5f;}
	}
}
