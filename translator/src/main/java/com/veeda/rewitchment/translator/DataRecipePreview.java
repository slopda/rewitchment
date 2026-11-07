package com.veeda.rewitchment.translator;
public final class DataRecipePreview {
    public static void main(String[] args) throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("No arguments supported");
        ArmorPreview.execute(true,true,true,true,true,true);
    }
}
