# Vanilla model-alias derivation exceptions

Generated from `reference/1.8.9` and `reference/26.2` by `./gradlew -PmodelMap.report generateModelNameMaps`. These entries are deliberately excluded from `block_models.json`: they need a dedicated conversion or an explicit reviewed alias.

## Legacy blockstates with no modern blockstate file

- `acacia_double_slab` → `acacia_double_slab`
- `birch_double_slab` → `birch_double_slab`
- `black_stained_hardened_clay` → `black_stained_hardened_clay`
- `blue_stained_hardened_clay` → `blue_stained_hardened_clay`
- `brick_double_slab` → `brick_double_slab`
- `brown_stained_hardened_clay` → `brown_stained_hardened_clay`
- `chiseled_brick_monster_egg` → `chiseled_brick_monster_egg`
- `chiseled_stonebrick` → `chiseled_stonebrick`
- `cobblestone_double_slab` → `cobblestone_double_slab`
- `cobblestone_monster_egg` → `cobblestone_monster_egg`
- `cracked_brick_monster_egg` → `cracked_brick_monster_egg`
- `cracked_stonebrick` → `cracked_stonebrick`
- `cyan_stained_hardened_clay` → `cyan_stained_hardened_clay`
- `dark_oak_double_slab` → `dark_oak_double_slab`
- `double_fern` → `double_fern`
- `double_grass` → `double_grass`
- `double_rose` → `double_rose`
- `gray_stained_hardened_clay` → `gray_stained_hardened_clay`
- `green_stained_hardened_clay` → `green_stained_hardened_clay`
- `houstonia` → `houstonia`
- `jungle_double_slab` → `jungle_double_slab`
- `light_blue_stained_hardened_clay` → `light_blue_stained_hardened_clay`
- `lime_stained_hardened_clay` → `lime_stained_hardened_clay`
- `magenta_stained_hardened_clay` → `magenta_stained_hardened_clay`
- `melon_block` → `melon_block`
- `mob_spawner` → `mob_spawner`
- `mossy_brick_monster_egg` → `mossy_brick_monster_egg`
- `mossy_stonebrick` → `mossy_stonebrick`
- `nether_brick_double_slab` → `nether_brick_double_slab`
- `oak_double_slab` → `oak_double_slab`
- `orange_stained_hardened_clay` → `orange_stained_hardened_clay`
- `paeonia` → `paeonia`
- `pink_stained_hardened_clay` → `pink_stained_hardened_clay`
- `portal` → `portal`
- `purple_stained_hardened_clay` → `purple_stained_hardened_clay`
- `quartz_column` → `quartz_column`
- `quartz_double_slab` → `quartz_double_slab`
- `red_sandstone_double_slab` → `red_sandstone_double_slab`
- `red_stained_hardened_clay` → `red_stained_hardened_clay`
- `sandstone_double_slab` → `sandstone_double_slab`
- `silver_carpet` → `silver_carpet`
- `silver_stained_glass` → `silver_stained_glass`
- `silver_stained_glass_pane` → `silver_stained_glass_pane`
- `silver_stained_hardened_clay` → `silver_stained_hardened_clay`
- `silver_wool` → `silver_wool`
- `smooth_andesite` → `smooth_andesite`
- `smooth_diorite` → `smooth_diorite`
- `smooth_granite` → `smooth_granite`
- `spruce_double_slab` → `spruce_double_slab`
- `stone_brick_double_slab` → `stone_brick_double_slab`
- `stone_brick_monster_egg` → `stone_brick_monster_egg`
- `stone_double_slab` → `stone_double_slab`
- `stone_monster_egg` → `stone_monster_egg`
- `syringa` → `syringa`
- `white_stained_hardened_clay` → `white_stained_hardened_clay`
- `wood_old_double_slab` → `wood_old_double_slab`
- `wood_old_slab` → `wood_old_slab`
- `yellow_stained_hardened_clay` → `yellow_stained_hardened_clay`

## Modern model names with conflicting legacy sources

- `anvil` ← [`anvil_undamaged`, `anvil_very_damaged`, `anvil_slightly_damaged`]
- `cauldron` ← [`cauldron_empty`, `cauldron_level3`, `cauldron_level2`, `cauldron_level1`]
- `potted_cactus` ← [`flower_pot_tulip_pink`, `flower_pot_tulip_orange`, `flower_pot_mushroom_red`, `flower_pot_birch`, `flower_pot_mushroom_brown`, `flower_pot_allium`, `flower_pot_orchid`, `flower_pot_daisy`, `flower_pot_houstonia`, `flower_pot_tulip_red`, `flower_pot_fern`, `flower_pot_oak`, `flower_pot_tulip_white`, `flower_pot_jungle`, `flower_pot_dark_oak`, `flower_pot`, `flower_pot_rose`, `flower_pot_cactus`, `flower_pot_spruce`, `flower_pot_dead_bush`, `flower_pot_dandelion`, `flower_pot_acacia`]
- `tripwire_hook_attached_on` ← [`tripwire_hook_attached_suspended_powered`, `tripwire_hook_attached_powered`]
