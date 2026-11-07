package com.veeda.rewitchment.translator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public final class LegacyItemNbtAdapter {
    static final String ITEM_STACK = "net/minecraft/class_1799";
    static final String COMPOUND = "net/minecraft/class_2487";
    static final String SHIM = "com/veeda/rewitchment/probe/LegacyItemNbt";
    private static final Set<String> SPECIAL_VANILLA_KEYS = Set.of(
            "Unbreakable", "SkullOwner", "CustomPotionColor", "CustomPotionEffects",
            "Potion", "display", "Enchantments", "StoredEnchantments",
            "AttributeModifiers", "HideFlags", "Damage", "RepairCost",
            "CanDestroy", "CanPlaceOn", "BlockEntityTag", "BlockStateTag",
            "EntityTag", "CustomModelData", "Trim", "ChargedProjectiles",
            "LodestonePos", "LodestoneDimension", "LodestoneTracked"
    );
    private static final Map<String, String> MUTATORS = Map.of(
            "method_10556(Ljava/lang/String;Z)V", "putBoolean",
            "method_10569(Ljava/lang/String;I)V", "putInt",
            "method_10582(Ljava/lang/String;Ljava/lang/String;)V", "putString",
            "method_25927(Ljava/lang/String;Ljava/util/UUID;)V", "putUuid",
            "method_10551(Ljava/lang/String;)V", "remove"
    );
    private static final Map<String, String> READERS = Map.of(
            "method_10545(Ljava/lang/String;)Z", "contains",
            "method_10577(Ljava/lang/String;)Z", "getBoolean",
            "method_10550(Ljava/lang/String;)I", "getInt",
            "method_10537(Ljava/lang/String;)J", "getLong",
            "method_10558(Ljava/lang/String;)Ljava/lang/String;", "getString",
            "method_25926(Ljava/lang/String;)Ljava/util/UUID;", "getUuid"
    );

    public record Outcome(int converted, int protectedKeysSkipped, int unsafeSkipped,
                          int readsConverted, int unbreakableConverted,
                          int potionColorConverted) {}
    private record Pair(MethodInsnNode getter, MethodInsnNode writer,
                        String shimMethod, String shimOwner) {}

    private LegacyItemNbtAdapter() {}

    private static boolean isOldNbtGetter(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode getter)) return false;
        return getter.getOpcode() == Opcodes.INVOKEVIRTUAL
                && ITEM_STACK.equals(getter.owner)
                && ("method_7948".equals(getter.name) || "method_7969".equals(getter.name))
                && ("()L" + COMPOUND + ";").equals(getter.desc);
    }

    private static String constantKey(SourceValue source) {
        if (source == null || source.insns.size() != 1) return null;
        AbstractInsnNode origin = source.insns.iterator().next();
        return origin instanceof LdcInsnNode ldc && ldc.cst instanceof String key ? key : null;
    }

    private static boolean safeWindow(MethodInsnNode getter, MethodInsnNode writer) {
        boolean reached = false;
        for (AbstractInsnNode node = getter.getNext(); node != null; node = node.getNext()) {
            if (node == writer) {
                reached = true;
                break;
            }
            if (node instanceof FrameNode || node instanceof JumpInsnNode
                    || node instanceof TableSwitchInsnNode || node instanceof LookupSwitchInsnNode
                    || node.getOpcode() == Opcodes.DUP || node.getOpcode() == Opcodes.SWAP
                    || node.getOpcode() == Opcodes.POP
                    || (node instanceof VarInsnNode var && var.getOpcode() == Opcodes.ASTORE)) {
                return false;
            }
        }
        return reached;
    }

    private static Outcome rewriteMethod(String classOwner, MethodNode method) {
        List<AbstractInsnNode> nodes = new ArrayList<>();
        int seen = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            nodes.add(insn);
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && COMPOUND.equals(call.owner)
                    && (MUTATORS.containsKey(call.name + call.desc)
                        || READERS.containsKey(call.name + call.desc))) {
                seen++;
            }
        }
        if (seen == 0) return new Outcome(0, 0, 0, 0, 0, 0);
        Frame<SourceValue>[] frames;
        try {
            frames = new Analyzer<>(new SourceInterpreter()).analyze(classOwner, method);
        } catch (AnalyzerException | RuntimeException failure) {
            return new Outcome(0, 0, seen, 0, 0, 0);
        }
        List<Pair> updates = new ArrayList<>();
        Set<MethodInsnNode> usedGetters = new HashSet<>();
        int protectedCount = 0, unsafe = 0, reads = 0, unbreakable = 0, writes = 0, potionColor = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (!(nodes.get(i) instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !COMPOUND.equals(call.owner)) continue;
            String lookupKey = call.name + call.desc;
            String bridgeName = MUTATORS.get(lookupKey);
            boolean isRead = bridgeName == null;
            if (isRead) bridgeName = READERS.get(lookupKey);
            if (bridgeName == null) continue;
            Frame<SourceValue> frame = frames[i];
            int args = Type.getArgumentTypes(call.desc).length;
            if (frame == null || frame.getStackSize() < args + 1) {
                unsafe++;
                continue;
            }
            SourceValue receiver = frame.getStack(frame.getStackSize() - args - 1);
            if (receiver.insns.size() != 1) {
                unsafe++;
                continue;
            }
            AbstractInsnNode producer = receiver.insns.iterator().next();
            if (!isOldNbtGetter(producer)) {

                continue;
            }
            String key = constantKey(frame.getStack(frame.getStackSize() - args));
            if (key == null) {
                unsafe++;
                continue;
            }
            boolean nativeUnbreakable = !isRead && key.equals("Unbreakable")
                    && lookupKey.equals("method_10556(Ljava/lang/String;Z)V");
            boolean nativePotionColor = !isRead && key.equals("CustomPotionColor")
                    && lookupKey.equals("method_10569(Ljava/lang/String;I)V");
            if (SPECIAL_VANILLA_KEYS.contains(key) && !nativeUnbreakable
                    && !nativePotionColor) {
                protectedCount++;
                continue;
            }
            MethodInsnNode getter = (MethodInsnNode) producer;
            if (!safeWindow(getter, call) || !usedGetters.add(getter)) {
                unsafe++;
                continue;
            }
            if (nativeUnbreakable) {
                bridgeName = "setUnbreakable";
                unbreakable++;
            } else if (nativePotionColor) {
                bridgeName = "setCustomPotionColor";
                potionColor++;
            } else if (isRead) {
                if (getter.name.equals("method_7948")) {
                    bridgeName = bridgeName.startsWith("get")
                            ? "getOrCreate" + bridgeName.substring(3)
                            : "getOrCreate" + Character.toUpperCase(bridgeName.charAt(0))
                              + bridgeName.substring(1);
                }
                reads++;
            } else {
                writes++;
            }
            updates.add(new Pair(getter, call, bridgeName, nativePotionColor
                    ? LegacyPotionAdapter.SHIM : SHIM));
        }
        for (Pair update : updates) {
            method.instructions.set(update.getter(), new InsnNode(Opcodes.NOP));
            String oldDescriptor = update.writer().desc;
            String staticDescriptor = "(L" + ITEM_STACK + ";" + oldDescriptor.substring(1);
            method.instructions.set(update.writer(), new MethodInsnNode(
                    Opcodes.INVOKESTATIC, update.shimOwner(), update.shimMethod(), staticDescriptor, false));
        }
        return new Outcome(writes, protectedCount, unsafe, reads, unbreakable, potionColor);
    }

    public static Outcome rewrite(ClassNode node) {
        int converted = 0, protectedKeys = 0, unsafe = 0, reads = 0, unbreakable = 0, potionColor = 0;
        for (MethodNode method : node.methods) {
            Outcome outcome = rewriteMethod(node.name, method);
            converted += outcome.converted();
            protectedKeys += outcome.protectedKeysSkipped();
            unsafe += outcome.unsafeSkipped();
            reads += outcome.readsConverted();
            unbreakable += outcome.unbreakableConverted();
            potionColor += outcome.potionColorConverted();
        }
        return new Outcome(converted, protectedKeys, unsafe, reads, unbreakable, potionColor);
    }
}
