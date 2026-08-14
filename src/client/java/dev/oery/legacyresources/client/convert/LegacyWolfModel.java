package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import java.util.Set;

/**
 * The adult wolf geometry from 1.8.9, expressed with the part hierarchy expected by the current
 * {@code AdultWolfModel}.  Keeping {@code real_head}, {@code upper_body}, and {@code real_tail}
 * means the current renderer still drives sitting, tail wagging, shaking, collars, and armour;
 * only the legacy one-pixel X offsets and 64x32 UV layout change.
 */
public final class LegacyWolfModel {
	private LegacyWolfModel() { }

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(mesh(), 64, 32);
	}

	/** The current wolf armour layer drives the adult controller, so it needs the same named classic parts. */
	public static LayerDefinition createArmorLayer() {
		return LayerDefinition.create(mesh(), 64, 32);
	}

	/** 1.8 scaled the classic adult sheet for pups; retain that layout instead of selecting a 32×32 modern sheet. */
	public static LayerDefinition createBabyLayer() {
		return LayerDefinition.create(mesh().apply(new BabyModelTransform(false, 5, 2, Set.of("head"))), 64, 32);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(-1, 13.5f, -7));
		head.addOrReplaceChild("real_head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-3, -3, -2, 6, 6, 4)
			.texOffs(16, 14).addBox(-3, -5, 0, 2, 2, 1).addBox(1, -5, 0, 2, 2, 1)
			.texOffs(0, 10).addBox(-1.5f, 0, -5, 3, 3, 4), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(18, 14).addBox(-4, -2, -3, 6, 9, 6), PartPose.offsetAndRotation(0, 14, 2, 1.5707964f, 0, 0));
		root.addOrReplaceChild("upper_body", CubeListBuilder.create().texOffs(21, 0).addBox(-4, -3, -3, 8, 6, 7), PartPose.offsetAndRotation(-1, 14, -3, 1.5707964f, 0, 0));
		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 18).addBox(-1, 0, -1, 2, 8, 2);
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5f, 16, 7));
		root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(.5f, 16, 7));
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5f, 16, -4));
		root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(.5f, 16, -4));
		PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offsetAndRotation(-1, 12, 8, .62831855f, 0, 0));
		tail.addOrReplaceChild("real_tail", CubeListBuilder.create().texOffs(9, 18).addBox(-1, 0, -1, 2, 8, 2), PartPose.ZERO);
		return mesh;
	}
}
