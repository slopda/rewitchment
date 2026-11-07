package com.veeda.rewitchment.probe;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ColorHelper;

public final class LegacyModelRendering {
    private LegacyModelRendering() {}

    private static int color(float red, float green, float blue, float alpha) {
        return ColorHelper.Argb.fromFloats(alpha, red, green, blue);
    }

    public static void render(ModelPart part, MatrixStack matrices, VertexConsumer vertices,
                              int light, int overlay, float red, float green,
                              float blue, float alpha) {
        part.render(matrices, vertices, light, overlay, color(red, green, blue, alpha));
    }

    public static void render(Model model, MatrixStack matrices, VertexConsumer vertices,
                              int light, int overlay, float red, float green,
                              float blue, float alpha) {
        model.render(matrices, vertices, light, overlay, color(red, green, blue, alpha));
    }
}
