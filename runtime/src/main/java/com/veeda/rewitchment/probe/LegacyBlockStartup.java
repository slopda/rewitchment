package com.veeda.rewitchment.probe;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingGenerator;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class LegacyBlockStartup {
    private LegacyBlockStartup() {}

    public static RegistryKey<ConfiguredFeature<?, ?>> configuredFeature(String id) {
        return RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, Identifier.of(id));
    }

    public static RegistryKey<PlacedFeature> placedFeature(String id) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(id));
    }

    public static SaplingGenerator saplingGenerator(String id, RegistryKey<ConfiguredFeature<?, ?>> tree) {
        return new SaplingGenerator(id, Optional.empty(), Optional.of(tree), Optional.empty());
    }

    public static ItemActionResult itemResult(ActionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemActionResult.SUCCESS;
            case CONSUME -> ItemActionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemActionResult.CONSUME_PARTIAL;
            case FAIL -> ItemActionResult.FAIL;
            case PASS -> ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }

    public static ActionResult legacyUse(Block block, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!state.isOf(block)) throw new IllegalArgumentException("State does not belong to " + block);
        return state.onUseWithItem(player.getStackInHand(hand), world, player, hand, hit).toActionResult();
    }

    public static MapCodec<Block> fixedCodec(Block block) {
        return MapCodec.unit(block);
    }

    public static EntityAttribute attackRange() {
        return EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE.value();
    }

    public static EntityAttribute blockReach() {
        return EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE.value();
    }

    public static EntityAttribute stepHeight() {
        return EntityAttributes.GENERIC_STEP_HEIGHT.value();
    }

    public static StatusEffect effectModifier(StatusEffect effect, EntityAttribute attribute, String uuid, double amount, EntityAttributeModifier.Operation operation) {
        return effect.addAttributeModifier(Registries.ATTRIBUTE.getEntry(attribute), Identifier.of("bewitchment", "legacy_modifier/" + uuid), amount, operation);
    }

    public static void reserveEffect(StatusEffect effect, Identifier id) {
        Registry.register(Registries.STATUS_EFFECT, id, effect);
    }

    public static <T> T registerOnce(Registry<? super T> registry, Identifier id, T value) {
        if (!id.equals(registry.getId(value))) throw new IllegalStateException("Effect was not reserved: " + id);
        return value;
    }

    public static void registerLootInjections() {
        var seeds = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("bewitchment", "inject/seeds"));
        var fortress = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("bewitchment", "inject/nether_fortress"));
        var plants = Set.of(Blocks.SHORT_GRASS.getLootTableKey(), Blocks.FERN.getLootTableKey(),
                Blocks.TALL_GRASS.getLootTableKey(), Blocks.LARGE_FERN.getLootTableKey());
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (plants.contains(key)) table.pool(LootPool.builder().with(LootTableEntry.builder(seeds).weight(1)).build());
            if (LootTables.NETHER_BRIDGE_CHEST.equals(key)) table.pool(LootPool.builder().with(LootTableEntry.builder(fortress).weight(1)).build());
        });
    }

    public static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    public static FoodComponent food(Item item) {
        return item.getComponents().get(DataComponentTypes.FOOD);
    }

    public static float saturationModifier(FoodComponent food) {
        return food.nutrition() == 0 ? 0F : food.saturation() / (food.nutrition() * 2F);
    }

    public static boolean isMeat(ItemStack stack) {
        return stack.isIn(ItemTags.MEAT);
    }

    public static FabricBlockSettings drops(FabricBlockSettings settings, Identifier lootTable) {
        return settings.drops(RegistryKey.of(RegistryKeys.LOOT_TABLE, lootTable));
    }
}
