package com.veeda.rewitchment.probe;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public final class LegacyGameplay {
    static final RegistryKey<net.minecraft.enchantment.Enchantment> MAGIC_PROTECTION =
            RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("bewitchment", "magic_protection"));

    private LegacyGameplay() {}

    public static boolean aboveTeleportFloor(BlockPos position, Entity entity) {
        return position.getY() > entity.getWorld().getBottomY();
    }

    public static void replaceTransformationModifier(net.minecraft.entity.attribute.EntityAttributeInstance instance,
            net.minecraft.entity.attribute.EntityAttributeModifier modifier) {
        instance.removeModifier(modifier.id());
        instance.addPersistentModifier(modifier);
    }

    public static boolean damage(ItemStack stack, int amount, Random random, ServerPlayerEntity player) {
        if (!stack.isDamageable()) return false;
        if (amount > 0) {
            int level = 0;
            for (var entry : stack.getEnchantments().getEnchantmentEntries())
                if (entry.getKey().matchesKey(Enchantments.UNBREAKING)) level = entry.getIntValue();
            int prevented = 0;
            for (int i = 0; level > 0 && i < amount; i++) {
                boolean spared = !(stack.getItem() instanceof ArmorItem && random.nextFloat() < 0.6F) && random.nextInt(level + 1) > 0;
                if (spared) prevented++;
            }
            amount -= prevented;
            if (amount <= 0) return false;
        }
        int damage = stack.getDamage() + amount;
        stack.setDamage(damage);
        return damage >= stack.getMaxDamage();
    }

    public static FireballEntity fireball(World world, LivingEntity owner, double x, double y, double z, int power) {
        return new FireballEntity(world, owner, new Vec3d(x, y, z), power);
    }

    public static SmallFireballEntity smallFireball(World world, LivingEntity owner, double x, double y, double z) {
        return new SmallFireballEntity(world, owner, new Vec3d(x, y, z));
    }

    public static WitherSkullEntity witherSkull(World world, LivingEntity owner, double x, double y, double z) {
        return new WitherSkullEntity(world, owner, new Vec3d(x, y, z));
    }

    public static void targetDamaged(LivingEntity attacker, Entity target) {
        if (target.getWorld() instanceof ServerWorld world)
            EnchantmentHelper.onTargetDamaged(world, target, target.getDamageSources().mobAttack(attacker), attacker.getMainHandStack());
    }

    public static float attackBonus(ItemStack weapon, LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld world)) return 0F;
        return EnchantmentHelper.getDamage(world, weapon, target, target.getDamageSources().generic(), 0F);
    }

    public static int looting(LivingEntity entity) {
        var entry = entity.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.LOOTING).orElseThrow();
        return EnchantmentHelper.getEquipmentLevel(entry, entity);
    }

    public static ItemStack enchant(Random random, ItemStack stack, int level, boolean treasure, World world) {
        if (treasure) throw new IllegalArgumentException("The original only requests non-treasure enchantments");
        DynamicRegistryManager manager = world.getRegistryManager();
        var options = manager.get(RegistryKeys.ENCHANTMENT).getEntryList(EnchantmentTags.IN_ENCHANTING_TABLE);
        return EnchantmentHelper.enchant(random, stack, level, manager, options.map(list -> (net.minecraft.registry.entry.RegistryEntryList<net.minecraft.enchantment.Enchantment>) list));
    }

    public static int magicProtection(ItemStack stack) {
        for (var entry : stack.getEnchantments().getEnchantmentEntries())
            if (entry.getKey().matchesKey(MAGIC_PROTECTION)) return entry.getIntValue();
        return 0;
    }

    public static double attributeValue(LivingEntity entity, net.minecraft.entity.attribute.EntityAttribute attribute) {
        return entity.getAttributeValue(net.minecraft.registry.Registries.ATTRIBUTE.getEntry(attribute));
    }

    public static float red(int color) { return (color >> 16 & 255) / 255F; }
    public static float green(int color) { return (color >> 8 & 255) / 255F; }
    public static float blue(int color) { return (color & 255) / 255F; }
    public static float alpha(int color) { return (color >>> 24) / 255F; }

    public static int packColor(float red, float green, float blue, float alpha) {
        return (Math.round(alpha * 255) & 255) << 24 | (Math.round(red * 255) & 255) << 16 | (Math.round(green * 255) & 255) << 8 | Math.round(blue * 255) & 255;
    }

    public static com.google.common.collect.Multimap<net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>, net.minecraft.entity.attribute.EntityAttributeModifier> zephyrModifiers(
            com.google.common.collect.Multimap<net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>, net.minecraft.entity.attribute.EntityAttributeModifier> modifiers, Identifier slot) {
        modifiers.put(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, new net.minecraft.entity.attribute.EntityAttributeModifier(
                Identifier.of(slot.getNamespace(), slot.getPath() + "/zephyr_harness"), 0.1, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    public static ItemStack drain(FluidDrainable block, WorldAccess world, BlockPos pos, BlockState state) {
        return block.tryDrainFluid(null, world, pos, state);
    }

    public static ServerPlayerEntity fakePlayer(MinecraftServer server, ServerWorld world, GameProfile profile) {
        return new ServerPlayerEntity(server, world, profile, SyncedClientOptions.createDefault());
    }

    public static ServerPlayNetworkHandler fakeHandler(MinecraftServer server, ClientConnection connection, ServerPlayerEntity player) {
        return new ServerPlayNetworkHandler(server, connection, player, ConnectedClientData.createDefault(player.getGameProfile(), false));
    }

    public static void resolveProfile(GameProfile profile, Consumer<GameProfile> callback) {
        SkullBlockEntity.fetchProfileByName(profile.getName()).thenAccept(found -> found.ifPresent(callback));
    }

    public static void setSkullOwner(ItemStack stack, String name) {
        stack.set(DataComponentTypes.PROFILE, new ProfileComponent(Optional.of(name), Optional.empty(), new com.mojang.authlib.properties.PropertyMap()));
    }
}
