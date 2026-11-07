package com.veeda.rewitchment.translator;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

public final class DataRecipeAdapter implements Opcodes {
    public static final String P="moriyashiine/bewitchment/common/recipe/";
    public static final String H="com/veeda/rewitchment/probe/LegacyDataRecipes";
    static final String ID="Lnet/minecraft/class_2960;", INV="Lnet/minecraft/class_1263;", INPUT="Lnet/minecraft/class_9695;";
    static final String LOOKUP="Lnet/minecraft/class_7225$class_7874;", OLD_LOOKUP="Lnet/minecraft/class_5455;", STACK="Lnet/minecraft/class_1799;";
    public record Field(String name,String type) {}
    public record Schema(String name,int kind,List<Field> fields) {
        String owner(){return P+name;}
        String ctor(){return "("+fields.stream().map(Field::type).reduce("",String::concat)+")V";}
    }
    static Field f(String name,String type){return new Field(name,type.length()==1?type:"L"+type+";");}
    public static final List<Schema> SCHEMAS=List.of(
        new Schema("AthameStrippingRecipe",0,List.of(f("log","net/minecraft/class_2248"),f("strippedLog","net/minecraft/class_2248"),f("output","net/minecraft/class_1799"))),
        new Schema("AthameDropRecipe",1,List.of(f("entity_type","net/minecraft/class_1299"),f("output","net/minecraft/class_1799"),f("chance","F"))),
        new Schema("RitualRecipe",2,List.of(f("input","net/minecraft/class_2371"),f("inner","java/lang/String"),f("outer","java/lang/String"),f("ritualFunction","moriyashiine/bewitchment/api/registry/RitualFunction"),f("cost","I"),f("runningTime","I"))),
        new Schema("OilRecipe",3,List.of(f("input","net/minecraft/class_2371"),f("output","net/minecraft/class_1799"),f("color","I"))),
        new Schema("CauldronBrewingRecipe",4,List.of(f("input","net/minecraft/class_1856"),f("output","net/minecraft/class_1291"),f("time","I"))),
        new Schema("IncenseRecipe",5,List.of(f("input","net/minecraft/class_2371"),f("effect","net/minecraft/class_1291"),f("amplifier","I"))),
        new Schema("CurseRecipe",6,List.of(f("input","net/minecraft/class_2371"),f("curse","moriyashiine/bewitchment/api/registry/Curse"),f("cost","I"))));
    public static final Map<String,Integer> EXPECTED_COUNTS=Map.ofEntries(Map.entry("entry_identity_adapters",7),Map.entry("entry_identity_fields",7),Map.entry("guarded_id_accessors",7),Map.entry("idless_constructors",7),Map.entry("legacy_serializer_methods_removed",48),Map.entry("manager_get_calls",4),Map.entry("manager_list_calls",20),Map.entry("native_inventory_bridges",14),Map.entry("native_serializer_codecs",14),Map.entry("recipe_data_exports",7),Map.entry("recipe_lookup_declarations",14),Map.entry("result_lookup_calls",8),Map.entry("serializer_factories",7),Map.entry("serializer_registry_boundaries",7));
    public record Result(ClassNode node,Map<String,Integer> counts) {}
    private DataRecipeAdapter() {}
    static void count(Map<String,Integer> counts,String key){counts.merge(key,1,Integer::sum);}
    static MethodNode method(String name,String desc){return new MethodNode(ACC_PUBLIC,name,desc,null,null);}
    static void call(MethodNode m,int op,String owner,String name,String desc){m.instructions.add(new MethodInsnNode(op,owner,name,desc,op==INVOKEINTERFACE));}
    static void load(MethodNode m,int slot){m.instructions.add(new VarInsnNode(ALOAD,slot));}
    static void end(MethodNode m,int opcode){m.instructions.add(new InsnNode(opcode));}
    static List<AbstractInsnNode> code(MethodNode m){return Arrays.stream(m.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();}
    static void unbox(MethodNode m,String type){
        String owner=type.equals("I")?"java/lang/Integer":type.equals("F")?"java/lang/Float":Type.getType(type).getInternalName();
        m.instructions.add(new TypeInsnNode(CHECKCAST,owner));
        if(type.length()==1)call(m,INVOKEVIRTUAL,owner,type.equals("I")?"intValue":"floatValue","()"+type);
    }
    static void box(MethodNode m,String type){if(type.length()==1)call(m,INVOKESTATIC,type.equals("I")?"java/lang/Integer":"java/lang/Float","valueOf","("+type+")L"+(type.equals("I")?"java/lang/Integer":"java/lang/Float")+";");}
    static Schema schema(String name){return SCHEMAS.stream().filter(s->name.equals(s.owner())||name.equals(s.owner()+"$Serializer")).findFirst().orElse(null);}
    public static Result rewrite(ClassNode node) {
        var counts=new TreeMap<String,Integer>();Schema schema=schema(node.name);
        if(schema!=null && node.name.endsWith("$Serializer")) {
            if(!node.interfaces.contains(H+"$Factory"))serializer(node,schema,counts);
        } else if(schema!=null && !node.interfaces.contains(H+"$Data"))node=recipe(node,schema,counts);
        if(!node.name.startsWith("moriyashiine/bewitchment/"))return new Result(node,counts);
        for(var m:node.methods)for(var n:m.instructions.toArray())if(n instanceof MethodInsnNode c) {
            if(c.owner.equals("net/minecraft/class_1863") && c.name.equals("method_30027") && c.desc.equals("(Lnet/minecraft/class_3956;)Ljava/util/List;")) {
                if(c.getOpcode()!=INVOKEVIRTUAL)throw new IllegalStateException("Unexpected recipe list opcode");
                c.setOpcode(INVOKESTATIC);c.owner=H;c.name="list";c.desc="(Lnet/minecraft/class_1863;Lnet/minecraft/class_3956;)Ljava/util/List;";c.itf=false;count(counts,"manager_list_calls");
            } else if(c.owner.equals("net/minecraft/class_1863") && c.name.equals("method_8130") && c.desc.equals("("+ID+")Ljava/util/Optional;")) {
                if(c.getOpcode()!=INVOKEVIRTUAL)throw new IllegalStateException("Unexpected recipe get opcode");
                c.setOpcode(INVOKESTATIC);c.owner=H;c.name="get";c.desc="(Lnet/minecraft/class_1863;"+ID+")Ljava/util/Optional;";c.itf=false;count(counts,"manager_get_calls");
            } else if(c.name.equals("method_8110") && c.desc.equals("("+OLD_LOOKUP+")"+STACK)) {
                if(!(c.owner.equals("net/minecraft/class_1860")||c.owner.equals("net/minecraft/class_3861")||SCHEMAS.stream().anyMatch(s->s.owner().equals(c.owner))))
                    throw new IllegalStateException("Unaudited recipe result owner "+c.owner);
                c.desc=c.desc.replace(OLD_LOOKUP,LOOKUP);count(counts,"result_lookup_calls");
            }
        }
        return new Result(node,counts);
    }
    static ClassNode recipe(ClassNode node,Schema schema,Map<String,Integer> counts) {
        for(var field:schema.fields())if(node.fields.stream().noneMatch(f->f.name.equals(field.name())&&f.desc.equals(field.type())))
            throw new IllegalStateException("Recipe schema field changed "+node.name+"."+field.name());
        var id=node.fields.stream().filter(f->f.name.equals("identifier")&&f.desc.equals(ID)).findFirst().orElseThrow();
        if((id.access&ACC_FINAL)==0)throw new IllegalStateException("Unexpected mutable original identity");
        id.access &= ~ACC_FINAL;count(counts,"entry_identity_fields");
        var ctor=node.methods.stream().filter(m->m.name.equals("<init>")&&m.desc.equals(schema.ctor().replace("(","("+ID))).findFirst().orElseThrow();
        boolean assigned=false;
        for(var i:ctor.instructions.toArray()) {
            if(i instanceof VarInsnNode v && v.var==1) {
                AbstractInsnNode next=i.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
                if(assigned||v.getOpcode()!=ALOAD||!(next instanceof FieldInsnNode f)||f.getOpcode()!=PUTFIELD||!f.owner.equals(node.name)||!f.name.equals("identifier")||!f.desc.equals(ID))
                    throw new IllegalStateException("Original constructor uses recipe ID outside identity assignment");
                ctor.instructions.set(i,new InsnNode(ACONST_NULL));assigned=true;
            } else if(i instanceof VarInsnNode v && v.var>1)v.var--;
            else if(i instanceof FrameNode)throw new IllegalStateException("Unexpected branched recipe constructor");
        }
        if(!assigned)throw new IllegalStateException("Missing original identifier assignment");
        if(ctor.localVariables!=null){ctor.localVariables.removeIf(v->v.index==1);ctor.localVariables.forEach(v->{if(v.index>1)v.index--;});}
        if(ctor.parameters!=null)ctor.parameters.removeFirst();
        if(ctor.visibleParameterAnnotations!=null||ctor.invisibleParameterAnnotations!=null)throw new IllegalStateException("Unaudited recipe constructor annotations");
        ctor.desc=schema.ctor();ctor.signature=null;ctor.maxLocals--;count(counts,"idless_constructors");
        var getId=node.methods.stream().filter(m->m.name.equals("method_8114")&&m.desc.equals("()"+ID)).findFirst().orElseThrow();
        var body=code(getId);
        if(body.size()!=3||!(body.get(1) instanceof FieldInsnNode f)||!f.name.equals("identifier")||body.get(2).getOpcode()!=ARETURN)throw new IllegalStateException("Unexpected recipe ID accessor");
        getId.instructions.insertBefore(body.get(2),new MethodInsnNode(INVOKESTATIC,H,"requireId","("+ID+")"+ID,false));count(counts,"guarded_id_accessors");
        var raw=method("legacyRecipeId","()"+ID);load(raw,0);raw.instructions.add(new FieldInsnNode(GETFIELD,node.name,"identifier",ID));end(raw,ARETURN);node.methods.add(raw);
        var bind=method("bindLegacyRecipeId","("+ID+")V");load(bind,0);load(bind,0);bind.instructions.add(new FieldInsnNode(GETFIELD,node.name,"identifier",ID));load(bind,1);
        call(bind,INVOKESTATIC,H,"bindId","("+ID+ID+")"+ID);bind.instructions.add(new FieldInsnNode(PUTFIELD,node.name,"identifier",ID));end(bind,RETURN);node.methods.add(bind);count(counts,"entry_identity_adapters");
        var values=method("legacyRecipeValues","()[Ljava/lang/Object;");values.instructions.add(new LdcInsnNode(schema.fields().size()));values.instructions.add(new TypeInsnNode(ANEWARRAY,"java/lang/Object"));
        for(int i=0;i<schema.fields().size();i++) {
            var field=schema.fields().get(i);end(values,DUP);values.instructions.add(new LdcInsnNode(i));load(values,0);
            values.instructions.add(new FieldInsnNode(GETFIELD,node.name,field.name(),field.type()));box(values,field.type());end(values,AASTORE);
        }
        end(values,ARETURN);node.methods.add(values);count(counts,"recipe_data_exports");node.interfaces.add(H+"$Data");
        if(node.signature!=null)node.signature=node.signature.replace(INV,INPUT)+"L"+H+"$Data;";
        for(String name:List.of("method_8115","method_8116")) {
            String tail=name.equals("method_8115")?"Lnet/minecraft/class_1937;)Z":LOOKUP+")"+STACK;
            String oldTail=name.equals("method_8115")?tail:OLD_LOOKUP+")"+STACK;
            if(node.methods.stream().noneMatch(m->m.name.equals(name)&&m.desc.equals("("+INV+oldTail)))throw new IllegalStateException("Missing original inventory recipe method");
            var bridge=method(name,"("+INPUT+tail);bridge.access|=ACC_BRIDGE|ACC_SYNTHETIC;
            load(bridge,0);load(bridge,1);call(bridge,INVOKESTATIC,H,"inventory","("+INPUT+")"+INV);load(bridge,2);
            call(bridge,INVOKEVIRTUAL,node.name,name,"("+INV+tail);end(bridge,name.equals("method_8115")?IRETURN:ARETURN);node.methods.add(bridge);count(counts,"native_inventory_bridges");
        }
        for(var m:node.methods)if(m.desc.contains(OLD_LOOKUP))count(counts,"recipe_lookup_declarations");
        var mapped=new ClassNode();node.accept(new ClassRemapper(mapped,new Remapper(){public String map(String name){return name.equals("net/minecraft/class_5455")?"net/minecraft/class_7225$class_7874":name;}}));return mapped;
    }
    static void serializer(ClassNode node,Schema schema,Map<String,Integer> counts) {
        if(!node.interfaces.contains("net/minecraft/class_1865"))throw new IllegalStateException("Unexpected serializer interface");
        var allowed=Set.of("read","write","method_8124","method_8122","method_8121","lambda$read$0","getIngredients","<clinit>");
        for(var m:new ArrayList<>(node.methods))if(!m.name.equals("<init>")) {
            if(!allowed.contains(m.name))throw new IllegalStateException("Unaudited original serializer method "+m.name);
            node.methods.remove(m);count(counts,"legacy_serializer_methods_removed");
        }
        if(node.fields.stream().anyMatch(f->!node.name.endsWith("CurseRecipe$Serializer")||!f.name.equals("TAGLOCK")))throw new IllegalStateException("Unexpected serializer field");
        node.fields.clear();node.interfaces.add(H+"$Factory");node.signature=null;
        for(String name:List.of("method_53736","method_56104")) {
            String result=name.equals("method_53736")?"Lcom/mojang/serialization/MapCodec;":"Lnet/minecraft/class_9139;";
            var m=method(name,"()"+result);m.instructions.add(new LdcInsnNode(schema.kind()));load(m,0);
            call(m,INVOKESTATIC,H,name.equals("method_53736")?"codec":"packetCodec","(IL"+H+"$Factory;)"+result);end(m,ARETURN);node.methods.add(m);count(counts,"native_serializer_codecs");
        }
        var create=method("createRecipe","([Ljava/lang/Object;)Lnet/minecraft/class_1860;");create.instructions.add(new TypeInsnNode(NEW,schema.owner()));end(create,DUP);
        for(int i=0;i<schema.fields().size();i++){load(create,1);create.instructions.add(new LdcInsnNode(i));end(create,AALOAD);unbox(create,schema.fields().get(i).type());}
        call(create,INVOKESPECIAL,schema.owner(),"<init>",schema.ctor());end(create,ARETURN);node.methods.add(create);count(counts,"serializer_factories");
        var registry=method("recipeRegistry","()Lnet/minecraft/class_2378;");
        if(schema.kind()==2||schema.kind()==6)registry.instructions.add(new FieldInsnNode(GETSTATIC,"moriyashiine/bewitchment/common/registry/BWRegistries",schema.kind()==2?"RITUAL_FUNCTION":"CURSE","Lnet/minecraft/class_2378;"));
        else end(registry,ACONST_NULL);
        end(registry,ARETURN);node.methods.add(registry);
        var taglock=method("curseTaglock","()Lnet/minecraft/class_1792;");
        if(schema.kind()==6)taglock.instructions.add(new FieldInsnNode(GETSTATIC,"moriyashiine/bewitchment/common/registry/BWObjects","TAGLOCK","Lnet/minecraft/class_1792;"));
        else end(taglock,ACONST_NULL);
        end(taglock,ARETURN);node.methods.add(taglock);count(counts,"serializer_registry_boundaries");
    }
}
