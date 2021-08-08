package chips;

import com.chipsea.healthscale.CSStandardAlgorithm;
import com.chipsea.healthscale.CsAlgoBuilderEx;
import com.chipsea.healthscale.MeasureResult;

import java.util.Date;

public class CsAlgoHelper {

    public static MeasureResult parseEightResitance(CsAlgoBuilderEx algoBuilderEx, double height, int sex, int age, Date weigthTime, double weight, byte[] arrResistance, MeasureResult prevMeasureResult, boolean isEightR){
        algoBuilderEx.setUserInfo((float) height, (byte) sex, age);
        return  algoBuilderEx.smoothResistance(
                weigthTime,
                (float) weight,
                arrResistance,
                prevMeasureResult,
                isEightR);
    }


    public static MeasureResult restoreMeasureResult(byte[] arrResistance, Date weigthTime, float weight){
        MeasureResult mr = new CSStandardAlgorithm().getMeasureResultfrombResistance(arrResistance);
        mr.MeasureTime = weigthTime;
        mr.Weight = weight;
        return mr;
    }
}
