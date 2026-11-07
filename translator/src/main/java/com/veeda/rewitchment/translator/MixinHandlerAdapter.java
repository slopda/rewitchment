package com.veeda.rewitchment.translator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class MixinHandlerAdapter {
    static final String MIXIN = "moriyashiine/bewitchment/mixin/brew/PotionEntityMixin";
    static final String OLD_HANDLER = "(Ljava/util/List;Lnet/minecraft/class_1297;"
            + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    static final String NEW_HANDLER = "(Ljava/lang/Iterable;Lnet/minecraft/class_1297;"
            + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    static final String OLD_TARGET = "Lnet/minecraft/class_1686;method_7498"
            + "(Ljava/util/List;Lnet/minecraft/class_1297;)V";
    static final String NEW_TARGET = "Lnet/minecraft/class_1686;method_7498"
            + "(Ljava/lang/Iterable;Lnet/minecraft/class_1297;)V";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record RefmapChange(byte[] bytes, int changes) {}

    private MixinHandlerAdapter() {}

    private static boolean annotatedForSplash(MethodNode method) {
        if (method.visibleAnnotations == null) return false;
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (!INJECT.equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("method".equals(annotation.values.get(i))) {
                    Object selectors = annotation.values.get(i + 1);
                    if (selectors instanceof List<?> values
                            && values.size() == 1 && "applySplashPotion".equals(values.get(0))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int rewrite(ClassNode node) {
        if (!MIXIN.equals(node.name)) return 0;
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!"applySplashPotion".equals(method.name) || !OLD_HANDLER.equals(method.desc)
                    || !annotatedForSplash(method)) continue;
            boolean touchesList = false;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof VarInsnNode variable && variable.var == 1) {
                    touchesList = true;
                    break;
                }
            }
            if (touchesList) continue;
            method.desc = NEW_HANDLER;

            method.signature = null;
            count++;
        }
        return count;
    }

    public static RefmapChange rewriteRefmap(byte[] bytes, boolean handlerWasAdapted) {
        if (!handlerWasAdapted) return new RefmapChange(bytes, 0);
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject mappings = root.getAsJsonObject("mappings");
        JsonObject single = mappings == null ? null : mappings.getAsJsonObject(MIXIN);
        if (single == null || !single.has("applySplashPotion")
                || !OLD_TARGET.equals(single.get("applySplashPotion").getAsString())) {
            throw new IllegalArgumentException("Refusing to patch unknown/incompatible Brew PotionEntityMixin refmap");
        }
        single.addProperty("applySplashPotion", NEW_TARGET);
        return new RefmapChange((GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8), 1);
    }
}
