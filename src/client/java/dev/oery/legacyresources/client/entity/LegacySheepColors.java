package dev.oery.legacyresources.client.entity;

import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;

/** 1.8.9's EntitySheep.DYE_TO_RGB table, rather than the modern darkened palette. */
final class LegacySheepColors {
	private static final float[][] RGB = {
		{1,1,1},{.85f,.5f,.2f},{.7f,.3f,.85f},{.4f,.6f,.85f},{.9f,.9f,.2f},{.5f,.8f,.1f},{.95f,.5f,.65f},{.3f,.3f,.3f},
		{.6f,.6f,.6f},{.3f,.5f,.6f},{.5f,.25f,.7f},{.2f,.3f,.7f},{.4f,.3f,.2f},{.4f,.5f,.2f},{.6f,.2f,.2f},{.1f,.1f,.1f}
	};
	private LegacySheepColors() { }
	static int color(SheepRenderState state) {
		if (state.isJebSheep) return state.getWoolColor();
		float[] rgb = RGB[state.woolColor.getId()];
		return ARGB.color(255, Math.round(rgb[0] * 255), Math.round(rgb[1] * 255), Math.round(rgb[2] * 255));
	}
}
