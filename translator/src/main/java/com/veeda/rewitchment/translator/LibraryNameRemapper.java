package com.veeda.rewitchment.translator;

import java.util.Map;
import org.objectweb.asm.commons.Remapper;

public final class LibraryNameRemapper extends Remapper {
    public static final Map<String, String> PACKAGE_MAPPINGS = Map.of(
            "dev/onyxstudios/cca/", "org/ladysnake/cca/",
            "io/github/ladysnake/impersonate/", "org/ladysnake/impersonate/"
    );

    @Override
    public String map(String internalName) {
        if (internalName == null) {
            return null;
        }
        for (var mapping : PACKAGE_MAPPINGS.entrySet()) {
            if (internalName.startsWith(mapping.getKey())) {
                return mapping.getValue() + internalName.substring(mapping.getKey().length());
            }
        }
        return internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {

        if ("net/minecraft/class_4588".equals(owner)
                && "method_22916".equals(name)
                && "(I)Lnet/minecraft/class_4588;".equals(descriptor)) {
            return "method_60803";
        }
        return super.mapMethodName(owner, name, descriptor);
    }
}
