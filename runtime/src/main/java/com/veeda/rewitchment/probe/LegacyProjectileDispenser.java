package com.veeda.rewitchment.probe;

import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

public abstract class LegacyProjectileDispenser extends ItemDispenserBehavior {
    private static final float FORCE = 1.1F, VARIATION = 6.0F;

    protected abstract ProjectileEntity createProjectile(World world, Position position, ItemStack stack);

    @Override protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
        World world = pointer.world();
        Direction direction = pointer.state().get(DispenserBlock.FACING);
        ProjectileEntity projectile = createProjectile(world, DispenserBlock.getOutputLocation(pointer), stack);
        projectile.setVelocity(direction.getOffsetX(), direction.getOffsetY() + 0.1F, direction.getOffsetZ(), FORCE, VARIATION);
        world.spawnEntity(projectile);
        stack.decrement(1);
        return stack;
    }

    @Override protected void playSound(BlockPointer pointer) {
        pointer.world().syncWorldEvent(WorldEvents.DISPENSER_ACTIVATED, pointer.pos(), 0);
    }
}
