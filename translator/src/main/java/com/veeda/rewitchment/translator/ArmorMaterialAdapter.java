package com.veeda.rewitchment.translator;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;

public final class ArmorMaterialAdapter {
    public static final String MATERIALS = "moriyashiine/bewitchment/common/registry/BWMaterials";
    public static final String OBJECTS = "moriyashiine/bewitchment/common/registry/BWObjects";
    public static final String HELPER = "com/veeda/rewitchment/probe/LegacyArmorRegistration";
    public static final String MATERIAL = "net/minecraft/class_1741";
    public static final String ITEM = "net/minecraft/class_1738";
    public static final String TYPE = "net/minecraft/class_1738$class_8051";
    public static final String SETTINGS = "net/minecraft/class_1792$class_1793";
    public static final String OLD_CTOR = "(L" + MATERIAL + ";L" + TYPE + ";L" + SETTINGS + ";)V";
    public static final String FACTORY = OLD_CTOR.substring(0, OLD_CTOR.length() - 1) + "L" + ITEM + ";";
    public static final Set<String> OBSOLETE = Set.of(
            MATERIALS + "$1", MATERIALS + "$2", MATERIALS + "$3", MATERIALS + "$4");
    private static final String[] FACTORIES = {"hedgewitch", "alchemist", "besmirched", "harbinger"};

    public record Outcome(int materials, int armorItems, int rejected) {}
    private ArmorMaterialAdapter() {}

    public static Outcome rewrite(ClassNode node) {
        if (!node.name.startsWith("moriyashiine/bewitchment/")) return new Outcome(0, 0, 0);
        int materials = 0, items = 0, rejected = 0;
        if (MATERIALS.equals(node.name)) {
            for (int i = 1; i <= 4; i++) {
                var result = IdentifierFactoryAdapter.rewrite(node, MATERIALS + "$" + i,
                        Map.of("()V", new IdentifierFactoryAdapter.Factory(HELPER,
                                FACTORIES[i - 1], "()L" + MATERIAL + ";")));
                materials += result.converted();
                rejected += result.rejected();
            }
        } else if (OBJECTS.equals(node.name)) {
            var result = IdentifierFactoryAdapter.rewrite(node, ITEM,
                    Map.of(OLD_CTOR, new IdentifierFactoryAdapter.Factory(HELPER,
                            "createArmor", FACTORY)));
            items += result.converted();
            rejected += result.rejected();
        }

        if (node.nestMembers != null) node.nestMembers.removeIf(OBSOLETE::contains);
        if (node.innerClasses != null) node.innerClasses.removeIf(c -> OBSOLETE.contains(c.name));
        return new Outcome(materials, items, rejected);
    }
}
