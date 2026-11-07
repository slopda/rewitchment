package com.veeda.rewitchment.probe;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

public final class LegacyItemRuntime {
    private LegacyItemRuntime() {}

    public static void damage(ItemStack stack, int amount, LivingEntity user,
            Consumer<LivingEntity> onBreak) {
        Objects.requireNonNull(onBreak, "Original break callback");
        if (!(user.getWorld() instanceof ServerWorld world)) return;
        if (user instanceof PlayerEntity player && player.getAbilities().creativeMode) return;
        stack.damage(amount, world, user instanceof ServerPlayerEntity player ? player : null, item -> {

            onBreak.accept(user);
            if (user instanceof PlayerEntity player) player.incrementStat(Stats.BROKEN.getOrCreateStat(item));
            stack.setDamage(0);
        });
    }

    public static EquipmentSlot slot(Hand hand) {
        return Objects.requireNonNull(hand) == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    public static void sendToolBreakStatus(LivingEntity user, Hand hand) {
        user.sendEquipmentBreakStatus(user.getStackInHand(hand).getItem(), slot(hand));
    }

    public static void potionTooltip(ItemStack stack, List<Text> lines, float durationScale,
            Item.TooltipContext context) {
        stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT)
                .buildTooltip(lines::add, durationScale, context.getUpdateTickRate());
    }

    public static EntityAttributeInstance attribute(LivingEntity entity, EntityAttribute attribute) {
        return entity.getAttributeInstance(Registries.ATTRIBUTE.getEntry(attribute));
    }
}
