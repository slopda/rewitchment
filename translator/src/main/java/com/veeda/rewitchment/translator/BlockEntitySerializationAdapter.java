package com.veeda.rewitchment.translator;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class BlockEntitySerializationAdapter {
    public static final String PREFIX = "moriyashiine/bewitchment/common/block/entity/";
    public static final String BLOCK_ENTITY = "net/minecraft/class_2586";
    public static final String INVENTORIES = "net/minecraft/class_1262";
    public static final String NBT = "net/minecraft/class_2487";
    public static final String REGISTRY = "net/minecraft/class_7225$class_7874";
    public static final String INV = "net/minecraft/class_2371";
    public static final String OLD_RW = "(L" + NBT + ";)V";
    public static final String NEW_RW = "(L" + NBT + ";L" + REGISTRY + ";)V";
    public static final String OLD_INITIAL = "()L" + NBT + ";";
    public static final String NEW_INITIAL = "(L" + REGISTRY + ";)L" + NBT + ";";
    public static final String OLD_INVENTORY_READ = "(L" + NBT + ";L" + INV + ";)V";
    public static final String NEW_INVENTORY_READ =
            "(L" + NBT + ";L" + INV + ";L" + REGISTRY + ";)V";
    public static final String OLD_INVENTORY_WRITE =
            "(L" + NBT + ";L" + INV + ";)L" + NBT + ";";
    public static final String NEW_INVENTORY_WRITE =
            "(L" + NBT + ";L" + INV + ";L" + REGISTRY + ";)L" + NBT + ";";

    private static final Set<String> OWNERS = Set.of(
            "BrazierBlockEntity",
            "DragonsBloodChestBlockEntity",
            "ElderChestBlockEntity",
            "GlyphBlockEntity",
            "JuniperChestBlockEntity",
            "LockableBlockEntity",
            "PoppetShelfBlockEntity",
            "SigilBlockEntity",
            "TaglockHolderBlockEntity",
            "WitchAltarBlockEntity",
            "WitchCauldronBlockEntity"
    );

    public record Outcome(int overridesMigrated, int vanillaCallsMigrated,
                          int inventoriesMigrated, int localFramesAdjusted) {}

    private BlockEntitySerializationAdapter() {}

    public static boolean isTargetClass(String name) {
        return name.startsWith(PREFIX) && OWNERS.contains(name.substring(PREFIX.length()));
    }

    private static boolean isTargetMethod(MethodNode method) {
        return switch (method.name) {
            case "method_16887" -> OLD_INITIAL.equals(method.desc);
            case "method_11014", "method_11007" -> OLD_RW.equals(method.desc);
            default -> false;
        };
    }

    public static Outcome rewrite(ClassNode node) {
        if (!isTargetClass(node.name)) return new Outcome(0, 0, 0, 0);
        int overrides = 0, calls = 0, inventories = 0, frames = 0;
        for (MethodNode method : node.methods) {
            if (!isTargetMethod(method)) continue;
            boolean initial = "method_16887".equals(method.name);
            int registrySlot = initial ? 1 : 2;

            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                 insn = insn.getNext()) {
                if (insn instanceof VarInsnNode var && var.var >= registrySlot) {
                    var.var += 1;
                } else if (insn instanceof IincInsnNode increment && increment.var >= registrySlot) {
                    increment.var += 1;
                } else if (insn instanceof FrameNode frame) {
                    if (frame.type != Opcodes.F_NEW && frame.type != Opcodes.F_FULL) {
                        throw new IllegalStateException("Expand original frames before moving registry arg");
                    }
                    if (frame.local == null || frame.local.size() < registrySlot) {
                        throw new IllegalStateException("Incomplete original stack map for " + node.name);
                    }
                    frame.local.add(registrySlot, REGISTRY);
                    frames++;
                }
            }
            if (method.localVariables != null) {
                for (LocalVariableNode local : method.localVariables) {
                    if (local.index >= registrySlot) local.index++;
                }
            }
            if (method.visibleLocalVariableAnnotations != null) {
                method.visibleLocalVariableAnnotations.forEach(annotation ->
                        annotation.index.replaceAll(i -> i >= registrySlot ? i + 1 : i));
            }
            if (method.invisibleLocalVariableAnnotations != null) {
                method.invisibleLocalVariableAnnotations.forEach(annotation ->
                        annotation.index.replaceAll(i -> i >= registrySlot ? i + 1 : i));
            }
            method.maxLocals++;
            method.maxStack++;

            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                 insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode call)) continue;
                boolean vanilla = (call.name.equals("method_11014")
                        || call.name.equals("method_11007"))
                        && OLD_RW.equals(call.desc)
                        && (call.owner.equals(BLOCK_ENTITY)
                            || call.owner.equals(node.superName)
                            || call.owner.equals(node.name));
                boolean initialSuper = call.name.equals("method_16887")
                        && OLD_INITIAL.equals(call.desc)
                        && (call.owner.equals(BLOCK_ENTITY) || call.owner.equals(node.superName));
                boolean invRead = INVENTORIES.equals(call.owner)
                        && call.name.equals("method_5429")
                        && OLD_INVENTORY_READ.equals(call.desc)
                        && call.getOpcode() == Opcodes.INVOKESTATIC;
                boolean invWrite = INVENTORIES.equals(call.owner)
                        && call.name.equals("method_5426")
                        && OLD_INVENTORY_WRITE.equals(call.desc)
                        && call.getOpcode() == Opcodes.INVOKESTATIC;
                if (!vanilla && !initialSuper && !invRead && !invWrite) continue;
                method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, registrySlot));
                if (initialSuper) call.desc = NEW_INITIAL;
                else if (vanilla) call.desc = NEW_RW;
                else call.desc = invRead ? NEW_INVENTORY_READ : NEW_INVENTORY_WRITE;
                if (invRead || invWrite) inventories++;
                else calls++;
            }
            method.desc = initial ? NEW_INITIAL : NEW_RW;
            overrides++;
        }
        return new Outcome(overrides, calls, inventories, frames);
    }
}
