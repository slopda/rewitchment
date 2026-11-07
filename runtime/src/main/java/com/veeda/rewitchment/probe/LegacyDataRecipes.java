package com.veeda.rewitchment.probe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

public final class LegacyDataRecipes {
    private LegacyDataRecipes() {}
    public interface Data {
        Object[] legacyRecipeValues();
        Identifier legacyRecipeId();
        void bindLegacyRecipeId(Identifier id);
    }
    public interface Factory<R extends Recipe<?>> {
        R createRecipe(Object[] values);
        Registry<?> recipeRegistry();
        Item curseTaglock();
    }
    public record InventoryInput(Inventory inventory) implements RecipeInput {
        public InventoryInput { Objects.requireNonNull(inventory); }
        public ItemStack getStackInSlot(int slot) { return inventory.getStack(slot); }
        public int getSize() { return inventory.size(); }
    }
    public static InventoryInput input(Inventory inventory) { return new InventoryInput(inventory); }
    public static Inventory inventory(RecipeInput input) {
        if(input instanceof InventoryInput wrapped) return wrapped.inventory();
        throw new IllegalArgumentException("Custom inventory recipe requires an InventoryInput view");
    }
    public static Identifier requireId(Identifier id) {
        if(id==null)throw new IllegalStateException("Recipe ID is unavailable until bound to a real RecipeEntry");
        return id;
    }
    public static Identifier bindId(Identifier previous,Identifier actual) {
        Objects.requireNonNull(actual);
        if(previous!=null && !previous.equals(actual))throw new IllegalStateException("One recipe value cannot represent two distinct recipe IDs");
        return actual;
    }
    public static <R extends Recipe<?>> R value(RecipeEntry<R> entry) {
        R recipe=entry.value();
        if(recipe instanceof Data data)data.bindLegacyRecipeId(entry.id());
        return recipe;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static List<?> list(RecipeManager manager,RecipeType<?> type) {
        var result=new ArrayList<Recipe<?>>();
        for(Object raw:manager.listAllOfType((RecipeType)type))result.add(value((RecipeEntry)raw));
        return result;
    }
    public static Optional<Recipe<?>> get(RecipeManager manager,Identifier id) {
        return manager.get(id).map(LegacyDataRecipes::value);
    }
    private record Field(String name,MapCodec<Object> codec) {}
    @SuppressWarnings("unchecked") private static Field field(String name,Codec<?> codec) {
        return new Field(name,(MapCodec<Object>)(MapCodec<?>)codec.fieldOf(name));
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static Field optional(String name,Codec codec,Object fallback) {
        return new Field(name,codec.optionalFieldOf(name,fallback));
    }
    private static Codec<DefaultedList<Ingredient>> ingredients(int maximum) {
        return Ingredient.ALLOW_EMPTY_CODEC.listOf().flatXmap(values->ingredientList(values,maximum),values->ingredientList(values,maximum).map(v->v));
    }
    private static DataResult<DefaultedList<Ingredient>> ingredientList(List<Ingredient> values,int maximum) {
        var filtered=DefaultedList.<Ingredient>of();
        for(var ingredient:values)if(!ingredient.isEmpty())filtered.add(ingredient);
        if(filtered.isEmpty() || filtered.size()>maximum)return DataResult.error(()->"Expected 1.."+maximum+" nonempty ingredients");
        return DataResult.success(filtered);
    }
    private record LegacyOutput(Item item,int count,Optional<Dynamic<?>> data) {}
    private static final Codec<ItemStack> LEGACY_RESULT=RecordCodecBuilder.<LegacyOutput>create(i->i.group(
        Registries.ITEM.getCodec().fieldOf("item").forGetter(LegacyOutput::item),
        Codec.INT.optionalFieldOf("count",1).forGetter(LegacyOutput::count),
        Codec.PASSTHROUGH.optionalFieldOf("data").forGetter(LegacyOutput::data)
    ).apply(i,LegacyOutput::new)).flatXmap(value->{
        if(value.data().isPresent())return DataResult.error(()->"Disallowed data tag found");
        var stack=new ItemStack(value.item(),value.count());
        return stack.isEmpty() || value.count()<1?DataResult.error(()->"Recipe result must be nonempty with a positive count"):DataResult.success(stack);
    },stack->DataResult.success(new LegacyOutput(stack.getItem(),stack.getCount(),Optional.empty())));

    private static final Codec<ItemStack> RESULT=Codec.either(LEGACY_RESULT,ItemStack.CODEC)
        .xmap(e->e.map(v->v,v->v),Either::right);
    private static final Codec<String> INNER=Codec.STRING.validate(value->value.isEmpty()
        ?DataResult.error(()->"Inner circle is empty"):DataResult.success(value));
    private static List<Field> fields(int kind,Factory<?> factory) {
        return switch(kind) {
            case 0 -> List.of(field("log",Registries.BLOCK.getCodec()),field("stripped_log",Registries.BLOCK.getCodec()),field("result",RESULT));
            case 1 -> List.of(field("entity_type",Registries.ENTITY_TYPE.getCodec()),field("result",RESULT),field("chance",Codec.FLOAT));
            case 2 -> List.of(field("ingredients",ingredients(6)),field("inner",INNER),optional("outer",Codec.STRING,""),
                field("ritual_function",Objects.requireNonNull(factory.recipeRegistry()).getCodec()),field("cost",Codec.INT),optional("running_time",Codec.INT,0));
            case 3 -> List.of(field("ingredients",ingredients(4)),field("result",RESULT),field("color",Codec.INT));
            case 4 -> List.of(field("ingredient",Ingredient.ALLOW_EMPTY_CODEC),field("effect",Registries.STATUS_EFFECT.getCodec()),field("time",Codec.INT));
            case 5 -> List.of(field("ingredients",ingredients(4)),field("effect",Registries.STATUS_EFFECT.getCodec()),optional("amplifier",Codec.INT,0));
            case 6 -> List.of(field("ingredients",ingredients(4)),field("curse",Objects.requireNonNull(factory.recipeRegistry()).getCodec()),field("cost",Codec.INT));
            default -> throw new IllegalArgumentException("Unknown recipe kind: "+kind);
        };
    }
    private static DataResult<Object[]> validate(int kind,Factory<?> factory,Object[] values) {
        if(kind==6) {
            var taglock=new ItemStack(Objects.requireNonNull(factory.curseTaglock()));
            boolean found=false;
            for(Object ingredient:(List<?>)values[0])if(((Ingredient)ingredient).test(taglock)){found=true;break;}
            if(!found)return DataResult.error(()->"Taglock not found in curse recipe");
        }
        return DataResult.success(values);
    }
    public static <R extends Recipe<?>> MapCodec<R> codec(int kind,Factory<R> factory) {
        var fields=fields(kind,factory);
        return new MapCodec<>() {
            public <T> Stream<T> keys(DynamicOps<T> ops) { return fields.stream().flatMap(f->f.codec().keys(ops)); }
            public <T> DataResult<R> decode(DynamicOps<T> ops,MapLike<T> input) {
                DataResult<List<Object>> result=DataResult.success(List.of());
                for(var field:fields) result=result.apply2stable((values,value)->{
                    var next=new ArrayList<>(values);next.add(value);return next;
                },field.codec().decode(ops,input));
                return result.flatMap(values->LegacyDataRecipes.validate(kind,factory,values.toArray())).map(factory::createRecipe);
            }
            public <T> RecordBuilder<T> encode(R recipe,DynamicOps<T> ops,RecordBuilder<T> prefix) {
                if(!(recipe instanceof Data data))return prefix.withErrorsFrom(DataResult.error(()->"Recipe lacks native data adapter"));
                Object[] values=data.legacyRecipeValues();
                if(values.length!=fields.size())return prefix.withErrorsFrom(DataResult.error(()->"Recipe schema arity mismatch"));
                prefix=prefix.withErrorsFrom(LegacyDataRecipes.validate(kind,factory,values));
                for(int i=0;i<values.length;i++)prefix=fields.get(i).codec().encode(values[i],ops,prefix);
                return prefix;
            }
        };
    }
    public static <R extends Recipe<?>> PacketCodec<RegistryByteBuf,R> packetCodec(int kind,Factory<R> factory) {
        return PacketCodecs.registryCodec(codec(kind,factory).codec());
    }
}
