package com.veeda.rewitchment.translator;

import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

public final class CraftingRecipeAdapter implements Opcodes {
    static final String P="moriyashiine/bewitchment/common/recipe/";
    static final String REGISTRY="moriyashiine/bewitchment/common/registry/BWRecipeTypes";
    static final String ID="Lnet/minecraft/class_2960;", CATEGORY="Lnet/minecraft/class_7710;";
    static final String OLD_INPUT="net/minecraft/class_8566", INPUT="net/minecraft/class_9694";
    static final String LOOKUP="net/minecraft/class_7225$class_7874";
    static final Set<String> RECIPES=Set.of(P+"TaglockCraftingRecipe",P+"ScepterCraftingRecipe",P+"PricklyBeltCraftingRecipe");
    static final String FACTORY="net/minecraft/class_1866$class_7711";
    public static final Map<String,Integer> EXPECTED_COUNTS=Map.ofEntries(
        Map.entry("recipe_constructors",3),Map.entry("super_constructors",3),
        Map.entry("typed_recipe_overrides",6),Map.entry("erased_recipe_bridges",6),
        Map.entry("crafting_input_queries",12),Map.entry("typed_bridge_dispatch",6),
        Map.entry("factory_implementations",3),Map.entry("factory_recipe_constructors",3),
        Map.entry("factory_sam_types",3),Map.entry("factory_instantiated_types",3),Map.entry("factory_method_handles",3));
    public record Result(ClassNode node,Map<String,Integer> counts) {}
    private CraftingRecipeAdapter() {}
    static void count(Map<String,Integer> counts,String name){counts.merge(name,1,Integer::sum);}
    static List<AbstractInsnNode> code(MethodNode m){return Arrays.stream(m.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();}
    static boolean load(AbstractInsnNode n,int slot){return n instanceof VarInsnNode v && v.getOpcode()==ALOAD && v.var==slot;}
    static String factoryDesc(String desc) {
        if(!desc.startsWith("("+ID+CATEGORY+")L"))throw new IllegalStateException("Unexpected factory descriptor "+desc);
        return desc.replace("("+ID,"(");
    }
    public static Result rewrite(ClassNode node) {
        var counts=new TreeMap<String,Integer>();
        if(RECIPES.contains(node.name))return recipe(node,counts);
        if(node.name.equals(REGISTRY))return registry(node,counts);
        return new Result(node,counts);
    }
    private static Result recipe(ClassNode node,Map<String,Integer> counts) {
        if(!node.superName.equals("net/minecraft/class_1852"))throw new IllegalStateException("Unexpected special recipe superclass");
        for(var m:node.methods) {
            if(m.name.equals("<init>") && m.desc.equals("("+ID+")V")) {
                var c=code(m);
                if(c.size()!=5 || !load(c.get(0),0)||!load(c.get(1),1)
                    || !(c.get(2) instanceof FieldInsnNode field)||field.getOpcode()!=GETSTATIC||!field.owner.equals("net/minecraft/class_7710")
                    || !field.name.equals("field_40250")||!field.desc.equals(CATEGORY)
                    || !(c.get(3) instanceof MethodInsnNode call)||call.getOpcode()!=INVOKESPECIAL||!call.owner.equals(node.superName)
                    || !call.name.equals("<init>")||!call.desc.equals("("+ID+CATEGORY+")V")||c.get(4).getOpcode()!=RETURN)
                    throw new IllegalStateException("Unexpected original crafting constructor");

                m.instructions.remove(c.get(1));m.desc="("+CATEGORY+")V";call.desc=m.desc;
                if(m.localVariables!=null)m.localVariables.forEach(v->{if(v.index==1){v.desc=CATEGORY;v.name="category";}});
                count(counts,"recipe_constructors");count(counts,"super_constructors");
            } else if(m.name.equals("matches")||m.name.equals("craft")) {
                String expected=m.name.equals("matches")?"(L"+OLD_INPUT+";Lnet/minecraft/class_1937;)Z"
                    :"(L"+OLD_INPUT+";Lnet/minecraft/class_5455;)Lnet/minecraft/class_1799;";
                if(!m.desc.equals(expected)||(m.access&ACC_STATIC)!=0)throw new IllegalStateException("Unexpected typed recipe method");
                count(counts,"typed_recipe_overrides");
            } else if((m.name.equals("method_8115")||m.name.equals("method_8116")) && m.desc.startsWith("(Lnet/minecraft/class_1263;")) {
                if((m.access&(ACC_BRIDGE|ACC_SYNTHETIC))!=(ACC_BRIDGE|ACC_SYNTHETIC)
                        || !m.desc.startsWith("(Lnet/minecraft/class_1263;"))throw new IllegalStateException("Unexpected erased recipe bridge");
                var c=code(m);
                if(c.size()!=6 || !load(c.get(0),0)||!load(c.get(1),1)||!(c.get(2) instanceof TypeInsnNode cast)
                        ||cast.getOpcode()!=CHECKCAST||!cast.desc.equals(OLD_INPUT)||!load(c.get(3),2)
                        ||!(c.get(4) instanceof MethodInsnNode call)||!call.owner.equals(node.name)
                        ||!call.name.equals(m.name.equals("method_8115")?"matches":"craft")
                        ||c.get(5).getOpcode()!=(m.name.equals("method_8115")?IRETURN:ARETURN))
                    throw new IllegalStateException("Unexpected bridge dispatch body");
                count(counts,"erased_recipe_bridges");
            }
            for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode call) {
                if(call.owner.equals(OLD_INPUT)) {
                    if(call.getOpcode()!=INVOKEINTERFACE || !call.itf)throw new IllegalStateException("Unexpected crafting query opcode");
                    if(call.name.equals("method_5439")&&call.desc.equals("()I"))call.name="method_59983";
                    else if(call.name.equals("method_5438")&&call.desc.equals("(I)Lnet/minecraft/class_1799;"))call.name="method_59984";
                    else throw new IllegalStateException("Unaudited crafting input query");
                    call.setOpcode(INVOKEVIRTUAL);call.itf=false;count(counts,"crafting_input_queries");
                } else if(call.owner.equals(node.name)&&(call.name.equals("craft")||call.name.equals("matches"))) {
                    count(counts,"typed_bridge_dispatch");
                }
            }
        }
        Remapper mapper=new Remapper() {
            public String map(String name){return switch(name){
                case OLD_INPUT -> INPUT;
                case "net/minecraft/class_1263" -> "net/minecraft/class_9695";
                case "net/minecraft/class_5455" -> LOOKUP;
                default -> name;};}
            public String mapMethodName(String owner,String name,String desc){
                if(owner.equals(node.name)&&name.equals("matches"))return "method_8115";
                if(owner.equals(node.name)&&name.equals("craft"))return "method_8116";
                return name;
            }
        };
        var mapped=new ClassNode();node.accept(new ClassRemapper(mapped,mapper));
        return new Result(mapped,counts);
    }
    private static Result registry(ClassNode node,Map<String,Integer> counts) {
        for(var m:node.methods) {
            if(m.name.matches("lambda\\$static\\$[012]") && m.desc.startsWith("("+ID)) {
                var c=code(m);
                if(c.size()!=5 || !(c.get(0) instanceof TypeInsnNode type)||type.getOpcode()!=NEW||!RECIPES.contains(type.desc)
                    ||c.get(1).getOpcode()!=DUP||!load(c.get(2),0)||!(c.get(3) instanceof MethodInsnNode ctor)
                    ||ctor.getOpcode()!=INVOKESPECIAL||!ctor.owner.equals(type.desc)||!ctor.name.equals("<init>")
                    ||!ctor.desc.equals("("+ID+")V")||c.get(4).getOpcode()!=ARETURN)
                    throw new IllegalStateException("Unexpected original crafting factory body");
                m.desc=factoryDesc(m.desc);ctor.desc="("+CATEGORY+")V";m.maxLocals=1;m.signature=null;
                if(m.localVariables!=null){m.localVariables.removeIf(v->v.index==1);m.localVariables.forEach(v->{v.desc=CATEGORY;v.name="category";});}
                count(counts,"factory_implementations");count(counts,"factory_recipe_constructors");
            }
            for(var n:m.instructions.toArray())if(n instanceof InvokeDynamicInsnNode indy && indy.desc.equals("()L"+FACTORY+";")) {
                if(!indy.name.equals("create")||indy.bsmArgs.length!=3||!indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        ||!indy.bsm.getName().equals("metafactory")||!(indy.bsmArgs[1] instanceof Handle handle)
                        ||!handle.getOwner().equals(REGISTRY)||handle.getTag()!=H_INVOKESTATIC)
                    throw new IllegalStateException("Unexpected special recipe factory bootstrap");
                if(!handle.getDesc().startsWith("("+ID))continue;  
                for(int i:new int[]{0,2}) {
                    if(!(indy.bsmArgs[i] instanceof Type t)||t.getSort()!=Type.METHOD)
                        throw new IllegalStateException("Unexpected factory method type");
                    indy.bsmArgs[i]=Type.getMethodType(factoryDesc(t.getDescriptor()));
                    count(counts,i==0?"factory_sam_types":"factory_instantiated_types");
                }
                indy.bsmArgs[1]=new Handle(handle.getTag(),handle.getOwner(),handle.getName(),factoryDesc(handle.getDesc()),handle.isInterface());
                count(counts,"factory_method_handles");
            }
        }
        return new Result(node,counts);
    }
}
