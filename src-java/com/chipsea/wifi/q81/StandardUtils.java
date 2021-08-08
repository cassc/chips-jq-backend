package com.chipsea.wifi.q81;


import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Edit from com/chipsea/code/util/StandardUtil.java
 */
public class StandardUtils {
    private static final int MAN = 1;
    private static final int WOMAN = 0;
    private int age;
    private Integer sex;

    public StandardUtils(int age, int sex) {
        this.age = age;
        this.sex = sex;
    }

    /**
     * 获得的体重标准范围
     */
    public float[] getWeightStandard(float height) {
        float[] standardRange = new float[2];
        standardRange[0] = getWeightFromBmi(height, 18.5f);
        standardRange[1] = getWeightFromBmi(height, 23.9f);
        return standardRange;
    }

    public float getWeightFromBmi(float height, float bmi) {
        return (float) (height * height / 10000.0f * bmi);
    }

    /**
     * 获得的脂肪率标准范围
     */
    public float[] getAxungeStandard() {
        float[] standardRange = new float[2];
        if (sex.equals(MAN)) { // 
            if (age <= 30) {
                standardRange[0] = 17.1f;
                standardRange[1] = 22.0f;
            } else {
                standardRange[0] = 19.1f;
                standardRange[1] = 24.0f;
            }
        } else {
            if (age <= 30) {
                standardRange[0] = 23.1f;
                standardRange[1] = 28.0f;
            } else {
                standardRange[0] = 23.1f;
                standardRange[1] = 30.0f;
            }
        }
        return standardRange;
    }

    /**
     * 获得的骨骼含量标准范围
     */
    public float[] getBoneStandard() {
        float[] standardRange = new float[2];
        standardRange[0] = 1.7f;
        if (sex.equals(MAN)) { // 
            if (age <= 54) {
                standardRange[1] = 2.4f;
            } else if (age >= 55 && age <= 75) {
                standardRange[1] = 2.8f;
            } else {
                standardRange[1] = 3.1f;
            }
        } else {
            if (age <= 39) {
                standardRange[1] = 1.7f;
            } else if (age >= 40 && age <= 60) {
                standardRange[1] = 2.1f;
            } else {
                standardRange[1] = 2.4f;
            }
        }

        // return standardRange;
        return standardRange;
    }

    /**
     * 获得的肌肉率标准范围
     */
    public float[] getMuscleStandard() {
        float[] standardRange = new float[2];
        if (sex.equals(MAN)) { // 
            standardRange[0] = 31.0f;
            standardRange[1] = 34.0f;
        } else {
            standardRange[0] = 25.0f;
            standardRange[1] = 27.0f;
        }
        return standardRange;
    }

    /**
     * 获得的水分率标准范围
     */
    public float[] getWaterStandard() {
        float[] standardRange = new float[2];
        if (sex.equals(MAN)) { // 
            if (age <= 30) {
                standardRange[0] = 53.6f;
                standardRange[1] = 57.0f;
            } else {
                standardRange[0] = 52.3f;
                standardRange[1] = 55.6f;
            }
        } else {
            if (age <= 30) {
                standardRange[0] = 49.5f;
                standardRange[1] = 52.9f;
            } else {
                standardRange[0] = 48.1f;
                standardRange[1] = 51.5f;
            }
        }
        return standardRange;
    }

    /**
     * 获得的基础代谢标准范围
     */
        public float[] getBMRStandard() {
        int tmpvalue = 0;
        if (sex==1) {
            if (age <= 2) {
                tmpvalue = 700;
            } else if (age <= 5) {
                tmpvalue = 900;
            } else if (age <= 8) {
                tmpvalue = 1090;
            } else if (age <= 11) {
                tmpvalue = 1290;
            } else if (age <= 14) {
                tmpvalue = 1480;
            } else if (age <= 17) {
                tmpvalue = 1610;
            } else if (age <= 29) {
                tmpvalue = 1550;
            } else if (age <= 49) {
                tmpvalue = 1500;
            } else if (age <= 69) {
                tmpvalue = 1350;
            } else {
                tmpvalue = 1220;
            }
        } else {
            if (age <= 2) {
                tmpvalue = 700;
            } else if (age <= 5) {
                tmpvalue = 860;
            } else if (age <= 8) {
                tmpvalue = 1000;
            } else if (age <= 11) {
                tmpvalue = 1180;
            } else if (age <= 14) {
                tmpvalue = 1340;
            } else if (age <= 17) {
                tmpvalue = 1300;
            } else if (age <= 29) {
                tmpvalue = 1210;
            } else if (age <= 49) {
                tmpvalue = 1170;
            } else if (age <= 69) {
                tmpvalue = 1110;
            } else {
                tmpvalue = 1010;
            }
        }
        return new float[]{tmpvalue * 0.6f, tmpvalue, tmpvalue * 2f};
    }


    /**
     * 获得内脏脂肪标准范围
     */
    public float[] getUVIStandard() {

        return new float[]{1.0f, 9.0f};
    }

    /**
     * 获得BMI标准范围
     */
    public float[] getBMIStandard() {

        return new float[]{18.5f, 23.9f};
    }

    /**
     * 获得身体年龄
     */
    public float[] getBodyAgeStandard() {
        float[] standardRange = new float[2];
        if (sex.equals(MAN)) { // 
            if (age <= 30) {
                standardRange[0] = 53.6f;
                standardRange[1] = 57.0f;
            } else {
                standardRange[0] = 52.3f;
                standardRange[1] = 55.6f;
            }
        } else {
            if (age <= 30) {
                standardRange[0] = 49.5f;
                standardRange[1] = 52.9f;
            } else {
                standardRange[0] = 48.1f;
                standardRange[1] = 51.5f;
            }
        }
        return null;
    }

    /**
     * 获得的男女宝宝体重标准范围
     */
    public float[] getBabyWeightStandard(int ageRange) {

        float[] standardRange = new float[25];
        float birthWeight = 3.3f;

        if (sex.equals(MAN)) { // 
            birthWeight += 0.4f;
        } else {
            birthWeight += 0.3f;
        }
        float tmpweight = birthWeight; // 当月标准体重

        // 0-6个月
        for (int i = 1; i <= 6; i++) {
            standardRange[i] = (float) (birthWeight + i * 0.6);

        }
        // tmpweight = standardRange[6];
        // 7-12个月
        for (int i = 7; i <= 12; i++) {
            standardRange[i] = (float) (birthWeight + i * 0.5);

        }
        tmpweight = standardRange[12];
        // 1-2岁
        for (int i = 13; i <= 24; i++) {
            standardRange[i] = tmpweight + (2.5f / 12) * (i - 12);
        }
        tmpweight += 2.5f;

        // 2 - 4岁
        if (ageRange == 1) {
            for (int i = 1; i <= 24; i++) {
                standardRange[i] = tmpweight + (2.0f / 12) * i;
            }
        }

        // 4-6岁
        if (ageRange == 2) {
            tmpweight += 4.0f;
            for (int i = 1; i <= 24; i++) {
                standardRange[i] = tmpweight + (2.0f / 12) * i;
            }
        }
        return standardRange;
    }

    /**
     * 获得的准妈妈的体重标准增长范围
     */
    public float[] getMotherWeightStandard() {

        float[] standardRange = new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0.5f,
                0.7f, 0.9f, 1.1f, 1.4f, 1.7f, 2.0f, 2.3f, 2.7f, 3.0f, 3.4f,
                3.8f, 4.3f, 4.7f, 5.1f, 5.5f, 5.9f, 6.4f, 6.8f, 7.2f, 7.4f,
                7.7f, 8.1f, 8.4f, 8.8f, 9.1f, 9.5f, 10.0f, 10.4f, 10.5f, 11f,
                11.3f};
        return standardRange;
    }

    /**
     * 获得bmi的数值
     *
     * @param height cm级别 如 170cm
     * @param weight
     */
    public float getBMI(int height, double weight) {
        if (height == 0 || (int) weight == 0) {
            return 0;
        }
        float bmi_result;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        double height_double = height;
        height_double = height_double / 100;
        bmi_result = Float.valueOf(new DecimalFormat("##0.0", symbols)
                .format((weight / (height_double * height_double))));
        return bmi_result;
    }

    /**
     * 获得当前bmi的参数的等级
     */
    public static int getBmiLevel(float bmi) {
        float[] levelNums = {18.5f, 24, 28};
        int bmiLevel = 0;
        for (int i = 0; i < levelNums.length; i++) {
            if (bmi < levelNums[i]) {
                break;
            } else {
                bmiLevel++;
            }
        }
        // 因为调用的地方都减1
        return bmiLevel + 1;
    }

    /**
     * 获得bmi分数
     *
     * @param info
     * @return
     */
    public static float getBmiCode(float bmi) {
        float code;
        float[] bmiStandardRange = getBMIStandardRange();
        if (bmi <= 0) {
            code = 5;
        } else if (bmi <= bmiStandardRange[0]) {
            code = 1;
        } else if (bmi < bmiStandardRange[1]) {
            code = (4 * (bmi - bmiStandardRange[0]) / (bmiStandardRange[1] - bmiStandardRange[0])) + 1;
        } else if (bmi <= bmiStandardRange[2]) {
            code = 5;
        } else if (bmi <= bmiStandardRange[3]) {
            code = (2 * (bmiStandardRange[3] - bmi) / (bmiStandardRange[3] - bmiStandardRange[2])) + 3;
        } else if (bmi <= bmiStandardRange[4]) {
            code = (2 * (bmiStandardRange[4] - bmi) / (bmiStandardRange[4] - bmiStandardRange[3])) + 1;
        } else {
            code = 1;
        }
        return code / 5 * (100 / 10);
    }


    /**
     * 获取BMI标准范围值
     *
     * @return
     */
    public static float[] getBMIStandardRange() {
        return new float[]{14f, 18.5f, 23.9f, 28f, 40f};
    }

    /**
     * 获得当前Axunge的参数的等级
     */
    public  int getAxungeLevel(float axunge) {
        float[] axungeStandardRange = getAxungeStandardRange();
        if (axunge < axungeStandardRange[1]) {
            return 1;
        } else if (axunge < axungeStandardRange[2]) {
            return 2;
        } else if (axunge < axungeStandardRange[3]) {
            return 3;
        } else {
            return 4;
        }
    }

    /**
     * 获取脂肪标准范围值
     *
     * @return
     */
    public float[] getAxungeStandardRange() {
        float[] standardRange = null;
        if (sex==1) {
            if (age <= 39) {
                standardRange = new float[]{5f, 11f, 22f, 27f, 45f};
            } else if (age <= 59) {
                standardRange = new float[]{5f, 12f, 23f, 28f, 45.0f};
            } else {
                standardRange = new float[]{5f, 14f, 25f, 30f, 45.0f};
            }
        } else {
            if (age <= 39) {
                standardRange = new float[]{5.0f, 21f, 35f, 40f, 45.0f};
            } else if (age <= 59) {
                standardRange = new float[]{5.0f, 22f, 36f, 41f, 45.0f};
            } else {
                standardRange = new float[]{5.0f, 23f, 37f, 42f, 45.0f};
            }
        }
        return standardRange;
    }


    public  float getAxungeCode( float axunge) {
        float code;
        float[] axungeStandardRange = getAxungeStandardRange();
        if (axunge <= 0) {
            code = 5;
        } else if (axunge <= axungeStandardRange[0]) {
            code = 1;
        } else if (axunge < axungeStandardRange[1]) {
            code = (4 * (axunge - axungeStandardRange[0]) / (axungeStandardRange[1] - axungeStandardRange[0])) + 1;
        } else if (axunge <= axungeStandardRange[2]) {
            code = 5;
        } else if (axunge <= axungeStandardRange[3]) {
            code = (2 * (axungeStandardRange[3] - axunge) / (axungeStandardRange[3] - axungeStandardRange[2])) + 3;
        } else if (axunge <= axungeStandardRange[4]) {
            code = (2 * (axungeStandardRange[4] - axunge) / (axungeStandardRange[4] - axungeStandardRange[3])) + 1;
        } else {
            code = 1;
        }
        return code / 5 * (100 / 10);
    }

    /**
     * 获得当前水分的参数的等级
     */
    public  int getWaterLevel( float water) {
        float[] waterStandardRange = getWaterStandardRange();
        if (water < waterStandardRange[1]) {
            return 1;
        } else if (water <= waterStandardRange[2]) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * 获得当前水分的分数
     */
    public float getWaterCode( float water) {
        float[] waterStandardRange = getWaterStandardRange();
        float code = 1;
        if (water <= 0) {
            code = 5;
        } else if (water <= waterStandardRange[0]) {
            code = 1;
        } else if (water < waterStandardRange[1]) {
            code = (4 * (water - waterStandardRange[0]) / (waterStandardRange[1] - waterStandardRange[0])) + 1;
        } else {
            code = 5;
        }
        return code / 5 * (100 / 10);
    }


    /**
     * 获取水分标准范围值
     *
     * @return
     */
    public  float[] getWaterStandardRange() {
        float[] range = null;
        if (sex==1) {
            if (age <= 30) {
                range = new float[]{37.8f, 53.6f, 57.0f, 66.0f};
            } else {
                range = new float[]{37.8f, 52.3f, 55.6f, 66.0f};
            }
        } else {
            if (age <= 30) {
                range = new float[]{37.8f, 49.5f, 52.9f, 66.0f};
            } else {
                range = new float[]{37.8f, 48.1f, 51.5f, 66.0f};
            }
        }
        return range;
    }



    /**
     * 获得当前肌肉的参数的等级
     */
    public int getMuscleLevel(float muscle) {
        float[] muscleStandardRange = getMuscleStandardRange();
        if (muscle < muscleStandardRange[1]) {
            return 1;
        } else if (muscle <= muscleStandardRange[2]) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * 获得当前肌肉的分数
     */
    public float getMuscleCode( float muscle) {
        float[] muscleStandardRange = getMuscleStandardRange();
        float code = 1;
        if (muscle <= 0) {
            code = 5;
        } else if (muscle <= muscleStandardRange[0]) {
            code = 1;
        } else if (muscle < muscleStandardRange[1]) {
            code = (4 * (muscle - muscleStandardRange[0]) / (muscleStandardRange[1] - muscleStandardRange[0])) + 1;
        } else {
            code = 5;
        }
        return code / 5 * (100 / 10);
    }


    /**
     * 获取肌肉标准范围值
     *
     * @return
     */
    public float[] getMuscleStandardRange() {
        float[] ranges = null;
        if (sex==1) {
            ranges = new float[]{26f, 31f, 39f, 45.0f};
        } else {
            ranges = new float[]{21f, 25f, 30f, 35f};
        }
        return ranges;
    }


    /**
     * 获得当前内脏的参数的等级
     */
    public static int getVisceraLevel(double value) {
        if (value < 1) {
            return 1;
        } else if (value <= 9) {
            return 2;
        } else if (value <= 14) {
            return 3;
        } else {
            return 4;
        }
    }

    /**
     * 获得当前内脏的参数的等级
     */
    public static float getVisceraCode(float vis) {
        return getStandardCode4(0, 1, 9, 14, 30, vis);
    }

    /**
     * 获得新城代谢标准 1 低 2 正常 3 高
     */
    public int getMetabolismLevel(float metabo) {
        if (metabo < getBMRStandard()[1]) {
            return 1;
        } else {
            return 2;
        }
    }


    /**
     * 获得新城代谢分数
     */
    /**
     * 获得新城代谢标准 1 低 2 正常 3 高
     */
    public float getMetabolismCode(float metabo) {
        if (sex.equals(MAN)) { // 
            if (age <= 2) {
                return getStandardCode2(700 * 0.8f, 700 * 0.95f, metabo);
            } else if (age <= 5) {
                return getStandardCode2(900 * 0.8f, 900 * 0.95f, metabo);
            } else if (age <= 8) {
                return getStandardCode2(1090 * 0.8f, 1090 * 0.95f, metabo);
            } else if (age < 11) {
                return getStandardCode2(1290 * 0.8f, 1290 * 0.95f, metabo);
            } else if (age < 14) {
                return getStandardCode2(1480 * 0.8f, 1480 * 0.95f, metabo);
            } else if (age < 17) {
                return getStandardCode2(1610 * 0.8f, 1610 * 0.95f, metabo);
            } else if (age < 29) {
                return getStandardCode2(1550 * 0.8f, 1550 * 0.95f, metabo);
            } else if (age < 49) {
                return getStandardCode2(1500 * 0.8f, 1500 * 0.95f, metabo);
            } else if (age < 69) {
                return getStandardCode2(1350 * 0.8f, 1350 * 0.95f, metabo);
            } else {
                return getStandardCode2(1220 * 0.8f, 1220 * 0.95f, metabo);
            }
        } else {
            if (age <= 2) {
                return getStandardCode2(700 * 0.8f, 700 * 0.95f, metabo);
            } else if (age <= 5) {
                return getStandardCode2(860 * 0.8f, 860 * 0.95f, metabo);
            } else if (age <= 8) {
                return getStandardCode2(1000 * 0.8f, 1000 * 0.95f, metabo);
            } else if (age < 11) {
                return getStandardCode2(1180 * 0.8f, 1180 * 0.95f, metabo);
            } else if (age < 14) {
                return getStandardCode2(1340 * 0.8f, 1340 * 0.95f, metabo);
            } else if (age < 17) {
                return getStandardCode2(1300 * 0.8f, 1300 * 0.95f, metabo);
            } else if (age < 29) {
                return getStandardCode2(1210 * 0.8f, 1210 * 0.95f, metabo);
            } else if (age < 49) {
                return getStandardCode2(1170 * 0.8f, 1170 * 0.95f, metabo);
            } else if (age < 69) {
                return getStandardCode2(1110 * 0.8f, 1110 * 0.95f, metabo);
            } else {
                return getStandardCode2(1010 * 0.8f, 1010 * 0.95f, metabo);
            }
        }
    }


    /**
     * 获取骨量标准范围值
     *
     * @return
     */
    public float[] getBoneStandardRange() {
        float tmpValue = 1;
        if (sex==1) {
            if (age <= 54) {
                tmpValue = 2.4f;
            } else if (age < 75) {
                tmpValue = 2.8f;
            } else if (age >= 75) {
                tmpValue = 3.1f;
            }
        } else  {
            if (age <= 39) {
                tmpValue = 1.7f;
            } else if (age <= 60) {
                tmpValue = 2.1f;
            } else if (age > 60) {
                tmpValue = 2.4f;
            }
        }
        return new float[]{tmpValue * 0.7f, tmpValue, tmpValue * 1.3f, 5f};
    }

    public int getBoneLevel(float bone) {
        float[] boneStandardRange = getBoneStandardRange();
        if (bone < boneStandardRange[1]) {
            return 1;
        } else if (bone <= boneStandardRange[2]) {
            return 2;
        } else {
            return 3;
        }
    }

    public float getBoneCode(float bone) {
        float[] boneStandardRange = getBoneStandardRange();
        float code = 1;
        if (bone <= 0) {
            code = 5;
        } else if (bone <= boneStandardRange[0]) {
            code = 1;
        } else if (bone < boneStandardRange[1]) {
            code = (4 * (bone - boneStandardRange[0]) / (boneStandardRange[1] - boneStandardRange[0])) + 1;
        } else {
            code = 5;
        }
        return code / 5 * (100 / 10);
    }

    private static float getStandardCode2(float lev1, float lev2, float value) {
        float code = 1;
        if (value <= lev1) {
            code = 1;
        } else if (value < lev2) {
            code = (4 * (value - lev1) / (lev2 - lev1)) + 1;
        } else {
            code = 5;
        }
        if (value == 0) {
            code = 5;
        }
        return code / 5 * (100 / 10);
    }

    private static float getStandardCode3(float lev1, float lev2, float lev3,
                                          float lev4, float value) {
        float code = 1;
        if (value <= lev1 || value > lev4) {
            code = 1;
        } else if (value <= lev2) {
            code = (4 * (value - lev1) / (lev2 - lev1)) + 1;
        } else if (value <= lev3) {
            code = 5;
        } else if (value <= lev4) {
            code = (4 * (lev4 - value) / (lev4 - lev3)) + 1;
        }
        if (value == 0) {
            code = 5;
        }
        return code / 5 * (100 / 10);
    }

    private static float getStandardCode4(float lev1, float lev2, float lev3,
                                          float lev4, float lev5, float value) {
        float standard = (lev3 + lev2) / 2;
        float code = 1;
        if (value <= lev1 || value > lev5) {
            code = 1;
        } else if (value <= standard) {
            code = (4 * (value - lev1) / (standard - lev1)) + 1;
        } else if (value <= lev5) {
            code = (4 * (lev5 - value) / (lev5 - standard)) + 1;
        }
        if (value == 0) {
            code = 5;
        }
        return code / 5 * (100 / 10);
    }


}

