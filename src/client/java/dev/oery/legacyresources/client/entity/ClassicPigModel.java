package dev.oery.legacyresources.client.entity;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.animal.pig.PigModel;

/** 1.8.9 pig geometry and its 64x32 UV canvas. */
final class ClassicPigModel {
	private ClassicPigModel() { }

	static LayerDefinition createLayer(float inflation) {
		CubeDeformation grow = new CubeDeformation(inflation);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
			.addBox(-4, -4, -8, 8, 8, 8, grow).texOffs(16, 16).addBox(-2, 0, -9, 4, 3, 1, grow), PartPose.offset(0, 12, -6));
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(28, 8).addBox(-5, -10, -7, 10, 16, 8, grow), PartPose.offsetAndRotation(0, 11, 2, 1.5707964f, 0, 0));
		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 6, 4, grow);
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-3, 18, 7));
		root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(3, 18, 7));
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-3, 18, -5));
		root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(3, 18, -5));
		return LayerDefinition.create(mesh, 64, 32);
	}

	static LayerDefinition createBabyLayer(float inflation) {
		return createLayer(inflation).apply(PigModel.BABY_TRANSFORMER);
	}
}
