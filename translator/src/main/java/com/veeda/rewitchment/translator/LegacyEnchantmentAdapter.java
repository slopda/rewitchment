package com.veeda.rewitchment.translator;

import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class LegacyEnchantmentAdapter {
    private static final String HELPER =
            "com/veeda/rewitchment/probe/LegacyEnchantmentQueries";
    private static final String ITEM = "net/minecraft/class_1799";
    private static final String ENCHANT = "net/minecraft/class_1887";
    private static final String LEVEL_HELPER = "net/minecraft/class_1890";
    private static final String ENCHANTMENTS = "net/minecraft/class_1893";
    private static final String LIVING = "net/minecraft/class_1309";
    private static final String REGISTRY_KEY = "net/minecraft/class_5321";
    private static final String GET_MAP = "(L" + ITEM + ";)Ljava/util/Map;";
    private static final String GET_LEVEL = "(L" + ENCHANT + ";L" + ITEM + ";)I";
    private static final String SET_MAP = "(Ljava/util/Map;L" + ITEM + ";)V";
    private static final String GET_EQUIPMENT_LEVEL =
            "(L" + ENCHANT + ";L" + LIVING + ";)I";
    private static final String MODERN_EQUIPMENT_LEVEL =
            "(L" + REGISTRY_KEY + ";L" + LIVING + ";)I";

    private static final String CLEAR =
            "moriyashiine/bewitchment/common/ritualfunction/ClearEnchantmentsRitualFunction";
    private static final String DAMAGE =
            "moriyashiine/bewitchment/common/registry/BWDamageSources";
    private static final String LIVING_MIXIN =
            "moriyashiine/bewitchment/mixin/LivingEntityMixin";
    private static final Set<String> SILK_MIXINS = Set.of(
            "moriyashiine/bewitchment/mixin/BlockMixin",
            "moriyashiine/bewitchment/mixin/contract/BlockMixin",
            "moriyashiine/bewitchment/mixin/fortune/BlockMixin"
    );

    public record Outcome(int silkTouchGuards, int legacyMapReads,
                          int legacyLevelReads, int legacyMapClears,
                          int repairCostWrites, int equipmentLevelQueries) {}

    private LegacyEnchantmentAdapter() {}

    private static boolean isOldGet(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC
                && LEVEL_HELPER.equals(call.owner)
                && "method_8222".equals(call.name)
                && GET_MAP.equals(call.desc);
    }

    public static Outcome rewrite(ClassNode cls) {
        boolean silk = SILK_MIXINS.contains(cls.name);
        boolean clear = CLEAR.equals(cls.name);
        boolean damage = DAMAGE.equals(cls.name);
        boolean livingMixin = LIVING_MIXIN.equals(cls.name);
        if (!silk && !clear && !damage && !livingMixin) {
            return new Outcome(0, 0, 0, 0, 0, 0);
        }
        int silkGuards = 0, mapReads = 0, levelReads = 0, mapClears = 0;
        int costs = 0, equipment = 0;
        for (MethodNode method : cls.methods) {
            for (AbstractInsnNode it = method.instructions.getFirst(); it != null;) {
                AbstractInsnNode next = it.getNext();

                if (silk && it instanceof MethodInsnNode get && isOldGet(get)
                        && next instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETSTATIC
                        && ENCHANTMENTS.equals(field.owner)
                        && "field_9099".equals(field.name)
                        && ("L" + ENCHANT + ";").equals(field.desc)
                        && next.getNext() instanceof MethodInsnNode contains
                        && contains.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/util/Map".equals(contains.owner)
                        && "containsKey".equals(contains.name)
                        && "(Ljava/lang/Object;)Z".equals(contains.desc)) {
                    get.owner = HELPER;
                    get.name = "hasSilkTouch";
                    get.desc = "(L" + ITEM + ";)Z";
                    get.itf = false;
                    AbstractInsnNode after = contains.getNext();
                    method.instructions.remove(field);
                    method.instructions.remove(contains);
                    silkGuards++;
                    it = after;
                    continue;
                }

                if (it instanceof MethodInsnNode call) {
                    if (clear && isOldGet(call)) {
                        call.owner = HELPER;
                        call.name = "legacyLevels";
                        mapReads++;
                    } else if ((clear || livingMixin)
                            && call.getOpcode() == Opcodes.INVOKESTATIC
                            && LEVEL_HELPER.equals(call.owner)
                            && "method_8225".equals(call.name)
                            && GET_LEVEL.equals(call.desc)) {
                        call.owner = HELPER;
                        call.name = "getLegacyLevel";
                        levelReads++;
                    } else if (clear && call.getOpcode() == Opcodes.INVOKESTATIC
                            && LEVEL_HELPER.equals(call.owner)
                            && "method_8214".equals(call.name)
                            && SET_MAP.equals(call.desc)) {
                        call.owner = HELPER;
                        call.name = "clearLegacyEnchantments";
                        mapClears++;
                    } else if (clear && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && ITEM.equals(call.owner)
                            && "method_7927".equals(call.name)
                            && "(I)V".equals(call.desc)) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = HELPER;
                        call.name = "setLegacyRepairCost";
                        call.desc = "(L" + ITEM + ";I)V";
                        call.itf = false;
                        costs++;
                    } else if (damage && call.getOpcode() == Opcodes.INVOKESTATIC
                            && LEVEL_HELPER.equals(call.owner)
                            && "method_8203".equals(call.name)
                            && GET_EQUIPMENT_LEVEL.equals(call.desc)) {
                        call.owner = HELPER;
                        call.name = "equipmentLevel";
                        call.desc = MODERN_EQUIPMENT_LEVEL;
                        equipment++;
                    }
                }
                if (damage && it instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.GETSTATIC
                        && ENCHANTMENTS.equals(field.owner)
                        && "field_9123".equals(field.name)
                        && ("L" + ENCHANT + ";").equals(field.desc)) {
                    field.desc = "L" + REGISTRY_KEY + ";";
                }
                it = next;
            }
        }
        return new Outcome(silkGuards, mapReads, levelReads, mapClears, costs, equipment);
    }
}
