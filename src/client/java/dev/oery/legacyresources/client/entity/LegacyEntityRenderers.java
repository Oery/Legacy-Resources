package dev.oery.legacyresources.client.entity;

import dev.oery.legacyresources.client.convert.LegacyPackResources;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.HorseRenderer;
import net.minecraft.client.renderer.entity.DonkeyRenderer;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityTypes;

/** Registers reload-aware renderer providers for entity families with incompatible classic UVs. */
public final class LegacyEntityRenderers {
	private static final Identifier[] HORSE_COATS = {
		id("horse_white"), id("horse_creamy"), id("horse_chestnut"), id("horse_brown"), id("horse_black"), id("horse_gray"), id("horse_darkbrown")
	};

	private LegacyEntityRenderers() { }

	public static void register() {
		EntityRendererRegistry.register(EntityTypes.HORSE, LegacyEntityRenderers::horse);
		EntityRendererRegistry.register(EntityTypes.DONKEY, LegacyEntityRenderers::donkey);
		EntityRendererRegistry.register(EntityTypes.MULE, LegacyEntityRenderers::mule);
		EntityRendererRegistry.register(EntityTypes.SKELETON_HORSE, LegacyEntityRenderers::skeletonHorse);
		EntityRendererRegistry.register(EntityTypes.ZOMBIE_HORSE, LegacyEntityRenderers::zombieHorse);
		EntityRendererRegistry.register(EntityTypes.BAT, LegacyEntityRenderers::bat);
		EntityRendererRegistry.register(EntityTypes.SHEEP, LegacyEntityRenderers::sheep);
		EntityRendererRegistry.register(EntityTypes.PIG, LegacyEntityRenderers::pig);
		EntityRendererRegistry.register(EntityTypes.CHICKEN, LegacyEntityRenderers::chicken);
	}

	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.equine.Horse, ?> horse(EntityRendererProvider.Context context) {
		return usesLegacy(context.getResourceManager(), HORSE_COATS) ? new HybridHorseRenderer(context) : new HorseRenderer(context);
	}
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.ambient.Bat, ?> bat(EntityRendererProvider.Context c) {
		Identifier t=Identifier.withDefaultNamespace("textures/entity/bat/bat.png"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new ClassicBatRenderer(c) : new BatRenderer(c);
	}
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.sheep.Sheep, ?> sheep(EntityRendererProvider.Context c) {
		Identifier t=Identifier.withDefaultNamespace("textures/entity/sheep/sheep_wool.png");return usesLegacy(c.getResourceManager(),new Identifier[]{t})?new ClassicSheepRenderer(c):new SheepRenderer(c);
	}
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.pig.Pig, ?> pig(EntityRendererProvider.Context c) {
		Identifier t=Identifier.withDefaultNamespace("textures/entity/pig/pig_temperate.png"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new HybridPigRenderer(c) : new PigRenderer(c);
	}
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.chicken.Chicken, ?> chicken(EntityRendererProvider.Context c) {
		Identifier t=Identifier.withDefaultNamespace("textures/entity/chicken/chicken_temperate.png"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new HybridChickenRenderer(c) : new ChickenRenderer(c);
	}
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.equine.Donkey, ?> donkey(EntityRendererProvider.Context c) {
		Identifier t=id("donkey"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new ClassicDonkeyRenderer<>(c,t) : new DonkeyRenderer<>(c,EquipmentClientInfo.LayerType.DONKEY_SADDLE,ModelLayers.DONKEY_SADDLE,DonkeyRenderer.Type.DONKEY,DonkeyRenderer.Type.DONKEY_BABY);
	}
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.equine.Mule, ?> mule(EntityRendererProvider.Context c) {
		Identifier t=id("mule"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new ClassicDonkeyRenderer<>(c,t) : new DonkeyRenderer<>(c,EquipmentClientInfo.LayerType.MULE_SADDLE,ModelLayers.MULE_SADDLE,DonkeyRenderer.Type.MULE,DonkeyRenderer.Type.MULE_BABY);
	}
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.equine.SkeletonHorse, ?> skeletonHorse(EntityRendererProvider.Context c) {
		Identifier t=id("horse_skeleton"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new ClassicUndeadHorseRenderer<>(c,t) : (net.minecraft.client.renderer.entity.EntityRenderer)new UndeadHorseRenderer(c,EquipmentClientInfo.LayerType.SKELETON_HORSE_SADDLE,ModelLayers.SKELETON_HORSE_SADDLE,UndeadHorseRenderer.Type.SKELETON,UndeadHorseRenderer.Type.SKELETON_BABY);
	}
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.animal.equine.ZombieHorse, ?> zombieHorse(EntityRendererProvider.Context c) {
		Identifier t=id("horse_zombie"); return usesLegacy(c.getResourceManager(),new Identifier[]{t}) ? new ClassicUndeadHorseRenderer<>(c,t) : (net.minecraft.client.renderer.entity.EntityRenderer)new UndeadHorseRenderer(c,EquipmentClientInfo.LayerType.ZOMBIE_HORSE_SADDLE,ModelLayers.ZOMBIE_HORSE_SADDLE,UndeadHorseRenderer.Type.ZOMBIE,UndeadHorseRenderer.Type.ZOMBIE_BABY);
	}

	private static boolean usesLegacy(ResourceManager manager, Identifier[] textures) {
		for (Identifier texture : textures) if (manager.getResource(texture).map(Resource::source).filter(LegacyPackResources.class::isInstance).isPresent()) return true;
		return false;
	}
	private static Identifier id(String stem) { return Identifier.withDefaultNamespace("textures/entity/horse/" + stem + ".png"); }
}
