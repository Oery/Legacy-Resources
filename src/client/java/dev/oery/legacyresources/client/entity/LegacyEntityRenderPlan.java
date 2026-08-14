package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

/** Production-owned entity-anchor and model-group transforms shared with the conformance lab. */
final class LegacyEntityRenderPlan {
	enum Family { OCELOT, CAT, WOLF, DOG, HORSE, CHICKEN, PIG, COW, RABBIT }
	private LegacyEntityRenderPlan() { }

	/** Applies the complete transform at LivingEntityRenderer.scale(), including its world anchor. */
	static void applyOuter(Family family, boolean baby, PoseStack poses) {
		if (!baby) return;
		if (family == Family.OCELOT || family == Family.CAT) {
			// LivingEntityRenderer flips Y before calling scale(). Moving -0.75 here therefore
			// raises the submitted cub by 0.75 world units and keeps its feet at the entity origin.
			poses.translate(0, -.75f, 0);
			applyModelSpace(family, true, poses);
		}
		else if (family == Family.HORSE) {
			// The legacy foal group transform otherwise composes with Minecraft's common
			// -1.501 model translation and leaves the hooves about 0.675 blocks in the air.
			// Y is already flipped here, so positive model Y moves the rendered horse down.
			poses.translate(0, .675f, 0);
			applyModelSpace(family, true, poses);
		}
		// Wolf head/body grouping is represented by the production BabyModelTransform-baked tree.
	}

	/** Applies only geometry-relative transforms; trace coordinates deliberately exclude entity anchoring. */
	static void applyModelSpace(Family family, boolean baby, PoseStack poses) {
		if (!baby) return;
		if (family == Family.OCELOT || family == Family.CAT) { poses.scale(.5f,.5f,.5f); poses.translate(0,1.5f,0); }
		else if (family == Family.HORSE) { poses.scale(.5f,.5f,.5f); poses.translate(0,-1.35f,0); }
	}
}
