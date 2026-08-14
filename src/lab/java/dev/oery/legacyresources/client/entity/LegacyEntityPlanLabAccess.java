package dev.oery.legacyresources.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

/** Development-only bridge; keeps the production plan package-private. */
public final class LegacyEntityPlanLabAccess {
	private LegacyEntityPlanLabAccess() { }
	public static void applyOuter(String family, boolean baby, PoseStack poses) {
		if ("bat".equals(family)) return; // Clean vanilla baseline has no legacy production plan yet.
		LegacyEntityRenderPlan.applyModelSpace(LegacyEntityRenderPlan.Family.valueOf(family.toUpperCase(java.util.Locale.ROOT)), baby, poses);
	}
}
