package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

public final class MixinMigrationAdapter implements Opcodes {
    static final String M = "moriyashiine/bewitchment/mixin/", MC = "net/minecraft/";
    static final String STACK = "L" + MC + "class_1799;", WORLD = "L" + MC + "class_1937;";
    static final String HOOKS = "com/veeda/rewitchment/probe/LegacyMixinHooks";
    static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    static final String CIR_OWNER = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", CIR = "L" + CIR_OWNER + ";";
    static final String GROUP = "com/veeda/rewitchment/probe/LegacyEntityGroup";
    static final String EAT = "method_18866(" + WORLD + STACK + "L" + MC + "class_4174;)" + STACK;
    static final String CONTENTS = "L" + MC + "class_1844;";

    public static final Set<String> REMOVED = Set.of("DamageEnchantmentMixin", "vampirehack.ProtectionEnchantmentMixin", "BlocksAccessor");

    public static final Set<String> MIGRATED = Set.of(M + "client/GameRendererMixin", M + "client/InGameHudMixin", M + "client/MobEntityRendererMixin", M + "BlockEntityTypeMixin", M + "HopperBlockEntityMixin", M + "LandPathNodeMakerMixin", M + "PlayerEntityMixin",
            M + "brew/PlayerEntityMixin", M + "contract/PlayerEntityMixin", M + "transformation/PlayerEntityMixin", M + "brew/ArrowEntityMixin",
            M + "brew/BrewingRecipeRegistryMixin", M + "brew/PotionEntityMixin", M + "brew/TippedArrowRecipeMixin", M + "curse/EntityMixin",
            M + "familiar/StatusEffectMixin", M + "transformation/StatusEffectMixin", M + "pledge/LivingEntityMixin", M + "transformation/LivingEntityMixin",
            M + "poppet/ItemStackMixin", M + "poppet/PotionEntityMixin");

    private MixinMigrationAdapter() {}

    static MethodNode handler(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name) && annotation(m) != null).findFirst()
                .orElseThrow(() -> new IllegalStateException("No handler " + name + " in " + node.name));
    }
    static AnnotationNode annotation(MethodNode method) {
        for (var list : Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations))
            if (list != null) for (var entry : list) if (entry.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !entry.desc.endsWith("At;")) return entry;
        return null;
    }
    static AnnotationNode mixinAnnotation(ClassNode node) {
        for (var list : Arrays.asList(node.visibleAnnotations, node.invisibleAnnotations))
            if (list != null) for (var entry : list) if (entry.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return entry;
        throw new IllegalStateException("No @Mixin on " + node.name);
    }
    static void set(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) annotation.values = new ArrayList<>();
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) {
            annotation.values.set(i + 1, value);
            return;
        }
        annotation.values.add(key);
        annotation.values.add(value);
    }
    static void remove(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) {
            annotation.values.remove(i + 1);
            annotation.values.remove(i);
            return;
        }
    }
    static Object get(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    @SuppressWarnings("unchecked") static AnnotationNode at(AnnotationNode injector) {
        var value = get(injector, "at");
        return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
    }
    static void selector(MethodNode method, String literal) { set(annotation(method), "method", new ArrayList<>(List.of(literal))); }

    private static int size(Object local) { return local == LONG || local == DOUBLE ? 2 : 1; }

    private static int position(List<Object> locals, int slot) {
        int cursor = 0, index = 0;
        while (cursor < slot && index < locals.size()) cursor += size(locals.get(index++));
        return index;
    }

    static void shift(MethodNode method, int slot, int delta, Object inserted) {
        for (var insn : method.instructions.toArray()) {
            if (insn instanceof VarInsnNode local && local.var >= slot) local.var += delta;
            else if (insn instanceof IincInsnNode local && local.var >= slot) local.var += delta;
            else if (insn instanceof FrameNode frame) {
                if (frame.type != F_NEW) throw new IllegalStateException("Expanded frames required");
                var locals = new ArrayList<>(frame.local);
                if (delta > 0) locals.add(position(locals, slot), inserted);
                else locals.remove(position(locals, slot + delta));
                frame.local = locals;
            }
        }
        method.localVariables = null;
        method.signature = null;
        method.maxLocals += delta;
    }
    static void retypeFrames(MethodNode method, String from, String to) {
        for (var insn : method.instructions.toArray()) if (insn instanceof FrameNode frame) {
            frame.local.replaceAll(value -> from.equals(value) ? to : value);
            if (frame.stack != null) frame.stack.replaceAll(value -> from.equals(value) ? to : value);
        }
    }

    static void returnable(MethodNode method) {
        method.desc = method.desc.replace(CI + ")V", CIR + ")V");
        method.signature = null;
        method.localVariables = null;
        retypeFrames(method, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", CIR_OWNER);
        for (var insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode call && call.name.equals("cancel")) {
            method.instructions.insertBefore(call, new FieldInsnNode(GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;"));
            method.instructions.set(call, new MethodInsnNode(INVOKEVIRTUAL, CIR_OWNER, "setReturnValue", "(Ljava/lang/Object;)V", false));
        }
    }
    static void target(ClassNode node, String owner) { set(mixinAnnotation(node), "value", new ArrayList<>(List.of(Type.getObjectType(owner)))); }

    static void guard(ClassNode node, MethodNode method, InsnList condition, int branch) {
        var pass = new LabelNode();
        var code = new InsnList();
        code.add(condition);
        code.add(new JumpInsnNode(branch, pass));
        code.add(new InsnNode(RETURN));
        code.add(pass);
        var locals = new ArrayList<Object>();
        if ((method.access & ACC_STATIC) == 0) locals.add(node.name);
        for (var type : Type.getArgumentTypes(method.desc))
            locals.add(switch (type.getSort()) {
                case Type.INT, Type.BOOLEAN -> INTEGER;
                case Type.FLOAT -> FLOAT;
                default -> type.getInternalName();
            });
        code.add(new FrameNode(F_NEW, locals.size(), locals.toArray(), 0, new Object[0]));
        method.instructions.insert(code);
    }
    static void drop(ClassNode node, String... names) {
        var doomed = Set.of(names);
        if (!node.methods.removeIf(m -> doomed.contains(m.name))) throw new IllegalStateException("Nothing dropped in " + node.name);
    }

    public static int dropUnusedShadows(ClassNode node) {
        var used = new HashSet<String>();
        for (var method : node.methods) for (var insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode call) used.add(call.name + call.desc);
            if (insn instanceof InvokeDynamicInsnNode dynamic) for (var argument : dynamic.bsmArgs)
                if (argument instanceof org.objectweb.asm.Handle handle) used.add(handle.getName() + handle.getDesc());
        }
        int before = node.methods.size();
        node.methods.removeIf(m -> !used.contains(m.name + m.desc) && Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations).stream()
                .filter(Objects::nonNull).flatMap(List::stream).anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")));
        return before - node.methods.size();
    }

    private static void eat(ClassNode node, String name) {
        var method = handler(node, name);
        selector(method, EAT);
        shift(method, 3, 1, MC + "class_4174");
        method.desc = method.desc.replace(WORLD + STACK, WORLD + STACK + "L" + MC + "class_4174;");
    }

    public static ClassNode rewrite(ClassNode node) {
        switch (node.name.substring(Math.min(node.name.length(), M.length()))) {
            case "client/GameRendererMixin" -> {

                var method = handler(node, "renderWorld");
                selector(method, "method_3198(L" + MC + "class_4587;F)V");
                var injection = at(annotation(method));
                set(injection, "value", "HEAD");
                remove(injection, "target");
                remove(injection, "shift");
                method.desc = "(L" + MC + "class_4587;F" + CI + ")V";
                method.signature = null;
                method.localVariables = null;
                for (var insn : method.instructions.toArray()) {
                    if (insn instanceof VarInsnNode local && local.var == 4) local.var = 1;
                    else if (insn instanceof VarInsnNode local && local.var == 5) local.var = 3;
                    else if (insn instanceof FrameNode frame) frame.local = new ArrayList<>(List.of(node.name, MC + "class_4587", FLOAT, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"));
                }
            }
            case "client/InGameHudMixin" -> {
                drop(node, "bewitchment$vampireHideFood0", "bewitchment$vampireHideFood1", "bewitchment$vampireHideFood2", "bewitchment$vampireShowHunger");
                set(at(annotation(handler(node, "renderPre"))), "target",
                        "L" + MC + "class_329;method_58477(L" + MC + "class_332;L" + MC + "class_1657;II)V");
                var injection = at(annotation(handler(node, "renderPre")));
                remove(injection, "ordinal");
                set(injection, "shift", new String[] {"Lorg/spongepowered/asm/mixin/injection/At$Shift;", "BEFORE"});
            }
            case "client/MobEntityRendererMixin" -> {
                target(node, MC + "class_897");
                node.signature = null;
                var method = handler(node, "shouldRender");
                selector(method, "method_3933(L" + MC + "class_1297;L" + MC + "class_4604;DDD)Z");
                method.desc = method.desc.replace("(L" + MC + "class_1308;", "(L" + MC + "class_1297;");
                method.signature = null;
                method.localVariables = null;
                retypeFrames(method, MC + "class_1308", MC + "class_1297");
            }
            case "BlockEntityTypeMixin" -> {

                var method = handler(node, "supports");
                for (var insn : method.instructions.toArray()) if (insn instanceof TypeInsnNode type && type.getOpcode() == INSTANCEOF && type.desc.equals(MC + "class_2587")) {
                    var branch = (JumpInsnNode) GameplayCompletionAdapter.next(insn);
                    method.instructions.set(insn, new FieldInsnNode(GETSTATIC, MC + "class_2591", "field_11910", "L" + MC + "class_2591;"));
                    branch.setOpcode(IF_ACMPNE);
                }
            }
            case "HopperBlockEntityMixin" -> {
                var method = handler(node, "getAvailableSlots");
                selector(method, "method_17767(L" + MC + "class_1263;L" + MC + "class_2350;)[I");
                for (var insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode call && call.owner.equals("java/util/stream/IntStream")) {
                    method.instructions.insertBefore(call, new InsnNode(ICONST_0));
                    method.instructions.set(call, new IntInsnNode(NEWARRAY, T_INT));
                }
            }
            case "LandPathNodeMakerMixin" -> {
                target(node, MC + "class_8");
                node.superName = "java/lang/Object";
                for (var method : node.methods) for (var insn : method.instructions.toArray())
                    if (insn instanceof MethodInsnNode call && call.name.equals("<init>") && call.owner.equals(MC + "class_8")) {
                        call.owner = "java/lang/Object";
                        call.desc = "()V";
                    }
                selector(handler(node, "inflictsFireDamage"), "method_57082(L" + MC + "class_2680;)Z");
            }
            case "PlayerEntityMixin", "transformation/PlayerEntityMixin" -> eat(node, "eat");
            case "brew/PlayerEntityMixin", "contract/PlayerEntityMixin" -> eat(node, "eatFood");
            case "brew/ArrowEntityMixin" -> {
                drop(node, "modifyGetCustomPotionEffects", "asItemStack", "lambda$asItemStack$0");
                selector(handler(node, "initFromStack"), "bewitchment$initFromStack(" + STACK + ")V");
            }
            case "brew/BrewingRecipeRegistryMixin" -> {
                var method = handler(node, "craft");
                method.access &= ~ACC_STATIC;
                shift(method, 0, 1, node.name);
                selector(method, "method_8078(" + STACK + STACK + ")" + STACK);
                var injection = at(annotation(method));
                set(injection, "value", "HEAD");
                remove(injection, "target");
                if (get(injection, "shift") != null) remove(injection, "shift");
                if (get(injection, "ordinal") != null) remove(injection, "ordinal");
                var condition = new InsnList();
                condition.add(new VarInsnNode(ALOAD, 1));
                condition.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "convertsPotions", "(" + STACK + ")Z", false));
                guard(node, method, condition, IFNE);
            }
            case "brew/PotionEntityMixin" -> {
                drop(node, "modifyIterator", "lambda$modifyIterator$0");
                var method = handler(node, "applyLingeringPotion");
                selector(method, "method_7497(" + CONTENTS + ")V");
                set(at(annotation(method)), "target", "L" + MC + "class_1295;method_57280(" + CONTENTS + ")V");
                for (var insn : method.instructions.toArray()) if (insn instanceof VarInsnNode local && local.getOpcode() == ALOAD && local.var == 1) {
                    method.instructions.insert(local, new MethodInsnNode(INVOKEVIRTUAL, MC + "class_3857", "method_7495", "()" + STACK, false));
                    local.var = 0;
                }
                shift(method, 3, -1, null);
                method.desc = method.desc.replace("(" + STACK + "L" + MC + "class_1842;", "(" + CONTENTS);
                retypeFrames(method, MC + "class_1799", MC + "class_1844");
            }
            case "brew/TippedArrowRecipeMixin" -> {
                var method = handler(node, "craft");
                selector(method, "method_17740(L" + MC + "class_9694;L" + MC + "class_7225$class_7874;)" + STACK);
                method.desc = method.desc.replace("L" + MC + "class_8566;L" + MC + "class_5455;", "L" + MC + "class_9694;L" + MC + "class_7225$class_7874;");
                method.signature = null;
                method.localVariables = null;
                retypeFrames(method, MC + "class_8566", MC + "class_9694");
                retypeFrames(method, MC + "class_5455", MC + "class_7225$class_7874");
                for (var insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode call && call.owner.equals(MC + "class_8566"))
                    method.instructions.set(call, new MethodInsnNode(INVOKEVIRTUAL, MC + "class_9694", call.name.equals("method_17398") ? "method_59991" : "method_59984", call.desc, false));
            }
            case "curse/EntityMixin" -> {
                var method = handler(node, "setOnFireFor");
                selector(method, "method_5639(F)V");
                method.desc = method.desc.replace("(I", "(F");
                method.localVariables = null;
                for (var insn : method.instructions.toArray()) if (insn instanceof FrameNode frame) frame.local.replaceAll(v -> v == INTEGER ? FLOAT : v);
            }
            case "familiar/StatusEffectMixin", "transformation/StatusEffectMixin" -> {
                target(node, MC + (node.name.contains("familiar") ? "class_8635" : "class_8637"));
                var method = handler(node, "applyUpdateEffect");
                selector(method, "method_5572(L" + MC + "class_1309;I)Z");
                returnable(method);
            }
            case "pledge/LivingEntityMixin", "transformation/LivingEntityMixin" ->
                selector(handler(node, "getGroup"), "bewitchment$legacyGroup()L" + GROUP + ";");
            case "poppet/ItemStackMixin" -> {
                var method = handler(node, "damage");
                selector(method, "method_7956(IL" + MC + "class_3218;L" + MC + "class_3222;Ljava/util/function/Consumer;)V");
                set(at(annotation(method)), "target", "L" + MC + "class_1799;method_7934(I)V");
                shift(method, 2, 1, MC + "class_3218");
                method.desc = "(IL" + MC + "class_3218;L" + MC + "class_3222;Ljava/util/function/Consumer;" + CI + ")V";
                var condition = new InsnList();
                condition.add(new VarInsnNode(ALOAD, 3));
                guard(node, method, condition, IFNONNULL);
            }
            case "poppet/PotionEntityMixin" -> {
                var method = handler(node, "applySplashPotion");
                selector(method, "method_7498(Ljava/lang/Iterable;L" + MC + "class_1297;)V");
                method.desc = method.desc.replace("(Ljava/util/List;", "(Ljava/lang/Iterable;");
                method.signature = null;
                method.localVariables = null;
                retypeFrames(method, "java/util/List", "java/lang/Iterable");
                for (var insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode call && call.owner.equals("java/util/List") && call.name.equals("iterator"))
                    call.owner = "java/lang/Iterable";
            }
            default -> {}
        }
        return relocateGroup(node);
    }

    private static ClassNode relocateGroup(ClassNode node) {
        if (!node.name.startsWith(M)) return node;
        var map = new TreeMap<String, String>();
        map.put("net/minecraft/class_1310", GROUP);
        map.put("net/minecraft/class_1310.field_6289", "UNDEAD");
        var result = new ClassNode();
        node.accept(new ClassRemapper(result, new SimpleRemapper(map)));
        return result;
    }
}
