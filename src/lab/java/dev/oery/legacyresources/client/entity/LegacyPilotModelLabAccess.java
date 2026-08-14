package dev.oery.legacyresources.client.entity;

import dev.oery.legacyresources.client.convert.LegacyBatModel;
import dev.oery.legacyresources.client.convert.LegacyChickenModel;
import dev.oery.legacyresources.client.convert.LegacyCowModel;
import dev.oery.legacyresources.client.convert.LegacyWolfAnimalModel;
import dev.oery.legacyresources.client.convert.LegacyWolfModel;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.BabyModelTransform;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.pig.PigModel;

/** Development-only construction bridge for package-private production pilot models. */
public final class LegacyPilotModelLabAccess {
	private LegacyPilotModelLabAccess() { }
	public static Model<?> chicken(boolean baby) {
		var layer = AdultChickenModel.createBodyLayer();
		if (baby) layer = layer.apply(new BabyModelTransform(false, 5, 2, Set.of("head")));
		return new LegacyChickenModel(layer.bakeRoot());
	}
	public static Model<?> cat() { return new LegacyCatModel(LegacyCatModel.createBodyLayer().bakeRoot()); }
	public static Model<?> cow(boolean baby) {
		var layer = LegacyCowModel.createBodyLayer();
		if (baby) layer = layer.apply(CowModel.BABY_TRANSFORMER);
		return new CowModel(layer.bakeRoot());
	}
	public static Model<?> pig(boolean baby, float inflation) {
		return new PigModel((baby ? ClassicPigModel.createBabyLayer(inflation) : ClassicPigModel.createLayer(inflation)).bakeRoot());
	}
	public static Model<?> rabbit(boolean baby) { return new ClassicRabbitModel(ClassicRabbitModel.createLayer(baby).bakeRoot()); }
	/** The 1.8.9 bat, oracle-derived; see {@link dev.oery.legacyresources.client.convert.LegacyBatModel}. */
	public static Model<?> bat() { return new LegacyBatModel(LegacyBatModel.createRoot()); }
	public static Model<?> wolf(boolean baby) { return new LegacyWolfAnimalModel((baby ? LegacyWolfModel.createBabyLayer() : LegacyWolfModel.createBodyLayer()).bakeRoot()); }
}
