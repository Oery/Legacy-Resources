package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** 1.8.9 Enderman pivots, retaining the names required by the modern EndermanModel controller. */
public final class LegacyEndermanModel {
	private LegacyEndermanModel() { }
	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh=new MeshDefinition(); PartDefinition r=mesh.getRoot();
		PartDefinition head=r.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8),PartPose.offset(0,-13,0));
		head.addOrReplaceChild("hat",CubeListBuilder.create().texOffs(0,16).addBox(-4,-8,-4,8,8,8,new CubeDeformation(-.5f)),PartPose.ZERO);
		r.addOrReplaceChild("body",CubeListBuilder.create().texOffs(32,16).addBox(-4,0,-2,8,12,4),PartPose.offset(0,-14,0));
		// The current EndermanModel drives symmetric shoulder parts.  Keeping 1.8's old -3 pivot
		// leaves this arm two units inside the torso; -5 is the matching shoulder to the left +5.
		r.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(56,0).addBox(-1,-2,-1,2,30,2),PartPose.offset(-5,-12,0));
		r.addOrReplaceChild("left_arm",CubeListBuilder.create().mirror().texOffs(56,0).addBox(-1,-2,-1,2,30,2),PartPose.offset(5,-12,0));
		r.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(56,0).addBox(-1,0,-1,2,30,2),PartPose.offset(-2,-2,0));
		r.addOrReplaceChild("left_leg",CubeListBuilder.create().mirror().texOffs(56,0).addBox(-1,0,-1,2,30,2),PartPose.offset(2,-2,0));
		return LayerDefinition.create(mesh,64,32);
	}
}
