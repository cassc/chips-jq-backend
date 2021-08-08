(ns dev-chips.food
  (:require
   [clojure.string :as s]
   [sparrows.misc :refer [str->num]]
   [sparrows.time :refer [long->date-string]]
   [cheshire.core :refer [parse-string generate-string]]
   [chips.models.mdata :refer [add-mdata]]
   [chips.models.base :refer :all]
   [chips.models.fe :as fe]
   [clojure.java.jdbc    :as j]))

(def food-keys [:niacin :is_liquid :natrium :calory :copper :zinc :selenium :name :type :fat :magnesium :calcium :kalium :brand :iron :cholesterol :weight :status :id :thumb_image_url :fiber_dietary :lactoflavin :health_light :code :vitamin_a :iodine :usedegree :vitamin_e :protein :phosphor :vitamin_c :units :carotene :manganese :carbohydrate :thiamine])

(def exercise-keys [:id :name :met :type :status :usedegree])

(defn import-bh-food []
  (letfn [(save-food [{:keys [ingredient ym_type ym_status ym_usedegree ym_brand] :as bh-food}]
            (let [food (-> bh-food
                           (select-keys food-keys)
                           (assoc :type ym_type :status ym_status :usedegree ym_usedegree :brand ym_brand)
                           (merge (parse-string ingredient keyword)))]
              (j/insert! (get-db :fe-db) :food food)))]
    (dorun
     (map
      save-food
      (j/query (get-db :bh-db) ["select * from bh_food"])))))


(defn import-ym-exercise []
  (letfn [(save-exercise [ym-exercise]
            (let [exercise (select-keys ym-exercise exercise-keys)]
              (j/insert! (get-db :fe-db) :exercise exercise)))]
    (dorun
     (map
      save-exercise
      (j/query (get-db :ym-db) ["select * from ym_exercise"])))))

(comment
  (import-bh-food)
  (import-ym-exercise)
 )

(defn gen-fake-fe-data [roleid]
  (let [{:keys [account_id]} (->entity (mysql-db) :csb_role {:id roleid})
        foods (fe/popular-food)
        exs (fe/popular-exercise)
        rand-date #(long->date-string (- (System/currentTimeMillis) (* 1000 3600 24 (rand-int 60))) {:pattern "yyyyMMdd" :offset "+8"})
        gen-food (fn [{:keys [id units name is_liquid] :as food}]
                   (assoc
                    (if-let [{:keys [amount calory] :as unit} (and units (first (parse-string units keyword)))]
                      {:unit unit :quantity amount :calory (* (str->num amount) (str->num calory))}
                      {:unit (if is_liquid "ml" "g") :quantity 100 :calory (* 100 (str->num (:calory food)))})
                    :food_id id
                    :metabolism (+ (rand-int 100) 1300)
                    :name name
                    :role_id roleid
                    :account_id account_id
                    :mtype :food
                    :date (rand-date)
                    :ftype (rand-nth ["breakfast" "lunch" "dinner" "snacks"])))
        gen-ex (fn [{:keys [id met name] :as ex}]
                 (let [duration (rand-int 120)]
                   {:ex_id id
                    :metabolism (+ (rand-int 100) 1300)
                    :name name
                    :calory (* duration (str->num met))
                    :role_id roleid
                    :account_id account_id
                    :mtype :exercise
                    :date (rand-date)
                    :duration duration}))]
    (j/with-db-transaction [db (mysql-db)]
      (add-mdata db {:mdata (map gen-food foods) :account_id account_id}))
    (j/with-db-transaction [db (mysql-db)]
      (add-mdata db {:mdata (map gen-ex (concat exs exs)) :account_id account_id}))))


(comment
  (first (parse-string (:units (first (fe/popular-food))) keyword))
  (first (fe/popular-exercise))
  (gen-fake-fe-data 1209)
  )


(defn import-units
  "将字符串形式的unit写入`units`表"
  []
  (doseq [{:keys [units id]} (j/query (fe-db) ["select units,id from food"])]
    (doseq [{:keys [unit_id] :as units} (when-not (s/blank? units)
                                          (parse-string units keyword))]
      (try
        (j/with-db-transaction [db (fe-db)]
          (j/insert! db :units units)
          (j/insert! db :food_units {:unit_id unit_id :food_id id}))
        (catch java.sql.SQLException e
          (if (= 1062 (.getErrorCode e))
            (println "ignore units with duplcate id: " units)
            (do
              (.printStackTrace e)
              (println "error adding units: " units)
              (throw e))))))))

;; select f.* from food f, food_units fu, units  u where u.unit='6' and fu.unit_id=u.unit_id and f.id=fu.food_id\Ge

(defn get-food-with-unit [unit]
  (j/query (fe-db) ["select f.* from food f, food_units fu, units  u where u.unit=? and fu.unit_id=u.unit_id and f.id=fu.food_id" unit]))

(defn distinct-unit []
  (map :unit (j/query (fe-db) ["select distinct unit from units"])))


(def unit-replace-map {"100"       "百克"
                       "3片"       "三片"
                       "10"        "十个"
                       "2个"       "两支" ;; 鸡翅
                       "袋装"      "小袋(袋装)" ;; 奶粉
                       "1根"       "一根"
                       "4个"       "四个" ;; 桂圆
                       "中"        "中个" ;; 玉米窝窝头
                       "大房子"    "大盒" ;; 牛奶
                       "6"         "六小个" ;; 卤味鹌鹑蛋
                       "支（小）"  "小支"
                       "快"        "大块" ;; 糟豆腐乳
                       "拳头"      "束" ;; TODO??? 粉条 fentiao 20g
                       "盒子"      "盒" ;; 光明 莫斯利安酸牛奶
                       "晚"        "碗"
                       "泡"        "杯" ;; 铁观音茶
                       "小蝶"      "小碟"
                       "bar"       "条"
                       "饼"        "瓶"
                       "蝶"        "碟"
                       "kuai"      "块"
                       "蓝白瓶"    "碗" ;; 虾皮紫菜汤
                       "分"        "份"
                       "p片"       "片"
                       "（小）碗"  "小碗"
                       "1个"       "个"
                       "想"        "箱"
                       "盒1"       "盒"
                       "盒2"       "盒"
                       "dai"       "袋"
                       "包1"       "包"
                       "包2"       "包"
                       "包3"       "包"
                       "粉"        "根"
                       "袋·"       "袋"
                       "之"        "支"
                       "煲"        "包"
                       "瓶装"      "瓶"
                       "被"        "杯"
                       "B2"        "包"
                       "合"        "盒"
                       "不"        "袋"
                       "各"        "个"
                       "好"        "盒"
                       "杯·"       "杯"
                       "跟"        "根"
                       "快餐饭盒"  "盒(快餐饭盒)"
                       "两 食堂的" "两(食堂的)"
                       "1片"       "片"
                       "一根"      "根"
                       "一块"      "块"
                       "一袋"      "袋"
                       "10粒"      "十粒"
                       "克"        "碗"
                       "盒3"       "盒"
                       "包·"       "包"
                       })

(letfn [(trim-whitespaces [unit]
          (s/trim unit))
        (rename-unit-by-mapping [unit]
          (unit-replace-map unit unit))
        (replace-brackets [unit]
          (->
           unit
           (s/replace "（" "(")
           (s/replace "）" ")")
           (s/replace "　" "")))]
  (defn check-units []
    (doseq [u (keys unit-replace-map)
            :let [f (get-food-with-unit u)]]
      (when (> (count f) 1)
        (println u "affects" (map #(str (:name %) ":" (:code %)) f)))))
  (defn fix-units []
    (doseq [{:keys [unit_id unit] :as m} (j/query (fe-db) ["select unit_id, unit from units"])]
      (let [new-unit (-> unit trim-whitespaces rename-unit-by-mapping replace-brackets)]
        (when-not (= new-unit unit)
          (println unit " -> " new-unit)
          (j/update! (fe-db) :units {:unit new-unit} ["unit_id=?" unit_id]))))))

(defn remake-units-in-food-table
  "根据food, units, food_units表重建food表里的unit缓存"
  []
  (doseq [{:keys [id name units]} (j/query (fe-db) ["select id,units,name from food"])
          :when (not (s/blank? units))]
    (let [new-units (j/query (fe-db) ["select u.* from units u, food_units fu where fu.food_id=? and fu.unit_id=u.unit_id" id])
          units-as-string (when (seq new-units) (generate-string (sort-by :id new-units)))]
      (when-not (= units units-as-string)
        (println name units-as-string)
        (j/update! (fe-db) :food {:units units-as-string} ["id=?" id])))))

(comment
  (remake-units-in-food-table)
  (clojure.pprint/pprint (distinct-unit))
  (check-units)
  (fix-units)
  (get-food-with-unit "袋28")
  (get-food-with-unit "cup")
  (get-food-with-unit "两(食堂的)")
  (get-food-with-unit "拳头")
  (get-food-with-unit "克")
  )
