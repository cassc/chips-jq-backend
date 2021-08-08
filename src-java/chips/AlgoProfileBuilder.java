package chips;

/**
 * Created by lixun on 2016/4/18.
 */
public class AlgoProfileBuilder {
    private static final short ERROR = -1;
    private static final double[] WATER_MOD_ARY = {750, 750, 750, 749, 746, 743, 740, 738, 736, 734, 732, 730};
    private static final double[] AF_FEMALE = {1.0,1.14,1.27,1.45};
    private static final double[] AF_MALE = {1.0,1.12,1.27,1.54};
    private static final float RESISTANCE_VAR=8.5f;

    private double age=-1;
    private double height=-1;
    private double weight=-1;
    private SexEnum sex;
    private double resistance=-1;
    private int exerciseLevel=-1;



    public static AlgoProfileBuilder newProfileBuilder(){
        return new AlgoProfileBuilder();
    }

    public AlgoProfileBuilder withSex(SexEnum sex){
        this.sex=sex;
        return this;
    }

    public AlgoProfileBuilder withAge(double age){
        this.age = age;
        return this;
    }
    public AlgoProfileBuilder withHeight(double height){
        this.height = height;
        return this;
    }
    public AlgoProfileBuilder withWeight(double weight){
        this.weight = weight;
        return this;
    }
    public AlgoProfileBuilder withResistance(double resistance){
        this.resistance = resistance;
        return this;
    }
    public AlgoProfileBuilder withExerciselevel(int exerciseLevel){
        if(exerciseLevel>3 || exerciseLevel<0){
            throw new IllegalArgumentException("Illegal exercise level: " + exerciseLevel);
        }
        this.exerciseLevel = exerciseLevel;
        return this;
    }

    public AlgoProfile build(){
        checkParams();
        return new AlgoProfileBuilder.AlgoProfile(this);
    }


    private void checkParams() {
        if(age<0 || height<0 && weight<0 && sex==null && resistance<0 && exerciseLevel<0){
            throw new IllegalStateException("Set params before calculating scores." );
        }
    }

    public class AlgoProfile {
        private double age=-1;
        private double height=-1;
        private double weight=-1;
        private SexEnum sex;
        private double resistance=-1;
        private int exerciseLevel=-1;

        private double fatScore = -2;
        private double visceraScore = -2;
        private double waterScore = -2;
        private double boneScore = -2;
        private double musScore = -2;
        private double dciScore = -2;
        private double bmrScore = -2;
        private double bmiScore = -2;

        public AlgoProfile(AlgoProfileBuilder builder) {
            this(builder.age,builder.height,builder.weight,builder.sex,builder.resistance,builder.exerciseLevel);
        }

        public AlgoProfile(double age, double height, double weight, SexEnum sex, double resistance, int exerciseLevel){
            this.age=age;
            this.height=height;
            this.weight=weight;
            this.sex=sex;
            this.resistance=resistance;
            this.exerciseLevel=exerciseLevel;
        }

        public double fatScore (){
            double fat = fatScore;
            if(fat < ERROR){
                checkParams();
                double y;
                double x = (4.57 / (1.1442- (0.0801 * (weight / 100.0) / (height * height/(resistance*RESISTANCE_VAR)))+ 0.000042 * (resistance / 10.0)- 0.00022 * weight / 100.0)- 4.142) * 100;
                x = (x<=0) ? 0 : x;
                x = (sex==SexEnum.F) ? (x+3.0) : x;
                x += (age<18) ? (18 - age) : ((age - 18)/10);

                if(sex==SexEnum.F && x>35){
                    x = (x-35)/2+35;
                }

                if(x>77 || x<0.5){
                    fat= ERROR;
                    fatScore = fat;
                    return fat;
                }

                if(exerciseLevel==0) {
                    fat = (x*10)+0.5;
                    fatScore = fat;
                    return fat;
                }

                double ageMod =  0.24*(height*height/(resistance*RESISTANCE_VAR))+0.67*(weight/100.0)+0.5;
                ageMod = (1-(ageMod/(weight/100)))*100;
                if(ageMod < 0 ){
                    fat = ERROR;
                    fatScore = fat;
                    return fat;
                }

                if(sex==SexEnum.F){
                    y = (x>30)? 0.1 : 0.3;
                }else {
                    y = (x>30) ? 0.5 : 1;
                }

                fat= ((ageMod * y + x * (1 - y)) * (exerciseLevel - 1) / 3 + x * (4 - exerciseLevel) / 3) * 10 + 0.5;
                fatScore = fat;
            }
            return fat;
        }

        public double visceraScore(){
            double vis = visceraScore;
            if(vis<ERROR){
                checkParams();
                double fat = fatScore;
                if(fat<0){
                    vis=ERROR;
                }else{
                    vis = fatScore() * resistance * resistance;
                    vis /= (sex==SexEnum.F) ? 6000 : 24000;
                    vis /= 10;
                }
                visceraScore = vis;
            }
            return vis;
        }

        public double waterScore(){
            double water = waterScore;
            if(water< ERROR){
                checkParams();
                int x = (age<=10) ? 10 : 21;
                x -= 10;
                water = (1000-visceraScore())*WATER_MOD_ARY[x]/1000+0.5;
                waterScore = water;
            }
            return water;
        }

        public double boneScore(){
            double bone = boneScore;
            if(bone<ERROR){
                checkParams();
                double fat = fatScore();
                if(fat<0){
                    bone = ERROR;
                }else {
                    bone = weight * 0.05 * (1000 - fatScore()) / 1000 + 0.5;
                }
                boneScore = bone;
            }
            return bone;
        }

        public double musScore(){
            double mus = musScore;
            if(mus<ERROR){
                checkParams();
                mus = (sex==SexEnum.F) ?
                        ((height*height/(resistance*RESISTANCE_VAR)*0.401+age*0.071+5.102)*100+0.5)/10
                        :
                        ((height*height/(resistance*RESISTANCE_VAR)*0.25+age*0.071+13)*100+0.5)/10;
                musScore = mus;
            }
            return mus;
        }

        public double dciScore(){
            double dci = dciScore;
            if(dci<ERROR){
                checkParams();
                dci = (sex==SexEnum.F) ?
                        (height*16.78/2.54+weight*4.95*2.2046/100)*AF_FEMALE[exerciseLevel]+387-age*7.31+0.5
                        :
                        (height*12.8/2.54+weight*6.46*2.2046/100)*AF_MALE[exerciseLevel]+864-age*9.72+0.5;
                dciScore = dci;
            }
            return dci;
        }

        public double bmrScore(){
            double bmr= bmrScore;
            if(bmr<ERROR){
                checkParams();
                bmr = (sex==SexEnum.F)?
                        655+weight*9.6/100+height*1.8-age*4.7+0.5
                        :
                        66+weight*13.7/100+height*5-age*6.8+0.5;
                bmrScore = bmr;
            }
            return bmr;
        }

        public double bmiScore(){
            double bmi = bmiScore;
            if(bmi<ERROR){
                checkParams();
                bmi = weight/height/height*1000+0.5;
                bmiScore = bmi;
            }
            return bmi;
        }
    }
}