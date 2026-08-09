package dev.oery.legacyresources.client.convert;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Replicates the pre-variant-update horse model, verified against the actual obfuscated 1.8.9
 * {@code ModelHorse} (decompiled from a real {@code 1.8.9.jar}, class {@code bbh}, found via the
 * {@code new bjd(this, new bbh(), ...)} construction site in RenderManager/{@code biu.class}).
 * The modern {@code AbstractEquineModel} declares a 64x64 canvas; 1.8.9's is 128x128
 * ({@code this.t = 128; this.u = 128;} in the model constructor, matching the real
 * {@code horse_white.png} et al., which are 128x128) - an entirely different UV layout, not just
 * a few shifted boxes like the cow. Box shapes/positions/UV offsets below are transcribed
 * directly from that decompile.
 *
 * <p>Built to satisfy the exact part names/hierarchy {@code AbstractEquineModel} expects
 * ({@code body} with a {@code tail} child, top-level {@code head_parts} and four legs), so the
 * existing (unmodified) {@code HorseModel}/{@code AbstractEquineModel} Java class continues to
 * drive animation via those names - this only replaces the geometry they animate. 1.8.9's model
 * had additional independently-jointed sub-parts modern's simpler one-box-per-leg,
 * one-transform-per-head system can't articulate (3-segment legs with knee/hoof bend, a
 * 3-segment tail, ears that could move independently of the head) - those are combined into
 * single rigid parts here, so the classic shape and UV layout are exact but classic-specific
 * animation nuances are not replicated.
 */
public final class LegacyHorseModel {
	private LegacyHorseModel() {
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body",
			CubeListBuilder.create().texOffs(0, 34).addBox(-5.0f, -8.0f, -19.0f, 10.0f, 10.0f, 24.0f),
			PartPose.offset(0.0f, 11.0f, 9.0f));
		// l, m, n (tail base/mid/tip), all originally anchored at (0, 3, 14) - relative to the
		// body's (0, 11, 9) anchor that's (0, -8, 5) - with a shared -1.134464 rad tilt.
		body.addOrReplaceChild("tail",
			CubeListBuilder.create()
				.texOffs(44, 0).addBox(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 3.0f)
				.texOffs(38, 7).addBox(-1.5f, -2.0f, 3.0f, 3.0f, 4.0f, 7.0f)
				.texOffs(24, 3).addBox(-1.5f, -4.5f, 9.0f, 3.0f, 4.0f, 7.0f),
			PartPose.offsetAndRotation(0.0f, -8.0f, 5.0f, -1.134464f, 0.0f, 0.0f));

		// a (skull), b/c (upper mouth), d/e (ears), h (mane slab), i (snout), j (mane ridge) -
		// all originally anchored at (0, 4, -10) with a shared 0.5235988 rad (30 deg) tilt.
		root.addOrReplaceChild("head_parts",
			CubeListBuilder.create()
				.texOffs(0, 0).addBox(-2.5f, -10.0f, -1.5f, 5.0f, 5.0f, 7.0f)
				.texOffs(24, 18).addBox(-2.0f, -10.0f, -7.0f, 4.0f, 3.0f, 6.0f)
				.texOffs(24, 27).addBox(-2.0f, -7.0f, -6.5f, 4.0f, 2.0f, 5.0f)
				.texOffs(0, 0).addBox(0.45f, -12.0f, 4.0f, 2.0f, 3.0f, 1.0f)
				.texOffs(0, 0).addBox(-2.45f, -12.0f, 4.0f, 2.0f, 3.0f, 1.0f)
				.texOffs(0, 12).addBox(-2.05f, -9.8f, -2.0f, 4.0f, 14.0f, 8.0f)
				.texOffs(80, 12).addBox(-2.5f, -10.1f, -7.0f, 5.0f, 5.0f, 12.0f, new CubeDeformation(0.2f))
				.texOffs(58, 0).addBox(-1.0f, -11.5f, 5.0f, 2.0f, 16.0f, 4.0f),
			PartPose.offsetAndRotation(0.0f, 4.0f, -10.0f, 0.5235988f, 0.0f, 0.0f));

		// Each leg combines its classic 3 stacked segments (upper leg/lower leg/hoof) into one
		// rigid part, anchored at the upper segment's position; v/w (and their counterparts) are
		// shifted by their (0, +7, 0) offset from that anchor.
		root.addOrReplaceChild("left_hind_leg",
			CubeListBuilder.create()
				.texOffs(78, 29).addBox(-2.5f, -2.0f, -2.5f, 4.0f, 9.0f, 5.0f)
				.texOffs(78, 43).addBox(-2.0f, 7.0f, -1.5f, 3.0f, 5.0f, 3.0f)
				.texOffs(78, 51).addBox(-2.5f, 12.1f, -2.0f, 4.0f, 3.0f, 4.0f),
			PartPose.offset(4.0f, 9.0f, 11.0f));
		root.addOrReplaceChild("right_hind_leg",
			CubeListBuilder.create()
				.texOffs(96, 29).addBox(-1.5f, -2.0f, -2.5f, 4.0f, 9.0f, 5.0f)
				.texOffs(96, 43).addBox(-1.0f, 7.0f, -1.5f, 3.0f, 5.0f, 3.0f)
				.texOffs(96, 51).addBox(-1.5f, 12.1f, -2.0f, 4.0f, 3.0f, 4.0f),
			PartPose.offset(-4.0f, 9.0f, 11.0f));
		root.addOrReplaceChild("left_front_leg",
			CubeListBuilder.create()
				.texOffs(44, 29).addBox(-1.9f, -1.0f, -2.1f, 3.0f, 8.0f, 4.0f)
				.texOffs(44, 41).addBox(-1.9f, 7.0f, -1.6f, 3.0f, 5.0f, 3.0f)
				.texOffs(44, 51).addBox(-2.4f, 12.1f, -2.1f, 4.0f, 3.0f, 4.0f),
			PartPose.offset(4.0f, 9.0f, -8.0f));
		root.addOrReplaceChild("right_front_leg",
			CubeListBuilder.create()
				.texOffs(60, 29).addBox(-1.1f, -1.0f, -2.1f, 3.0f, 8.0f, 4.0f)
				.texOffs(60, 41).addBox(-1.1f, 7.0f, -1.6f, 3.0f, 5.0f, 3.0f)
				.texOffs(60, 51).addBox(-1.6f, 12.1f, -2.1f, 4.0f, 3.0f, 4.0f),
			PartPose.offset(-4.0f, 9.0f, -8.0f));

		return LayerDefinition.create(mesh, 128, 128);
	}
}
