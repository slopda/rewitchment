package com.veeda.rewitchment.probe;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public final class LegacyClientRendering {
    private LegacyClientRendering() {}

    public static void drawEntity(DrawContext context, int x, int y, int size, float relativeX, float relativeY, LivingEntity entity) {
        int left = x - size, right = x + size, top = y - size * 2;
        float mouseX = (left + right) / 2F - relativeX, mouseY = (top + y) / 2F - relativeY;
        InventoryScreen.drawEntity(context, left, top, right, y, size, 0.0625F, mouseX, mouseY, entity);
    }

    public static void beginDemonPortrait(int pixelX, int pixelY, int pixelWidth, int pixelHeight,
            DrawContext context, int x, int y) {

        context.enableScissor(x, y + 4, x + 64, y + 68);
    }

    public static void endDemonPortrait(DrawContext context) {
        context.disableScissor();
    }

    public static void drawDemonHeart(DrawContext context, net.minecraft.util.Identifier oldSheet,
            int x, int y, int u, int v, int width, int height) {
        if (v != 0 || width != 9 || height != 9) throw new IllegalArgumentException("Unexpected demon heart region");
        String sprite = switch (u) {
            case 16 -> "container";
            case 52 -> "full";
            case 61 -> "half";
            default -> throw new IllegalArgumentException("Unexpected demon heart offset " + u);
        };
        context.drawGuiTexture(net.minecraft.util.Identifier.ofVanilla("hud/heart/" + sprite), x, y, width, height);
    }

    public static void entityEffect(World world, double x, double y, double z, double red, double green, double blue) {
        world.addParticle(EntityEffectParticleEffect.create(ParticleTypes.ENTITY_EFFECT, (float) red, (float) green, (float) blue), x, y, z, 0, 0, 0);
    }
}
