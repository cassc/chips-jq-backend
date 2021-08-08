package com.chipsea.wifi.q81;

public class CsBlueHelper {
    public static CsAlgoBuilder makeBuilder(double height, double weight, int sex, int age, double resistance, String remark){
        CsAlgoBuilder builder = new CsAlgoBuilder((float) height, (float) weight, (byte) sex,age, (float) resistance,remark);

        return builder;
    }
}
