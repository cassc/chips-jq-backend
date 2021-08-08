(ns dev-chips.play
  (:require
   [chips.config :refer [props]]
   [chips.open.haier :refer [haiji-push]]
   [chips.models.mdata :refer [add-mdata]]
   [chips.utils :as utils]
   [chips.models.base :refer :all]

   [com.rpl.specter :refer :all]
   [clojure.java.jdbc :as j]
   [clojure.java.io :as io]
   [dk.ative.docjure.spreadsheet :as sp]
   [sparrows.time :refer [long->date-string]]
   [sparrows.misc :refer [str->num get-time-as-string dissoc-nil-val]]
   [clojure.string :as s])
  (:import
   [com.maxmind.geoip2 DatabaseReader DatabaseReader$Builder]
   [com.maxmind.db CHMCache]
   [java.net InetAddress]))

(defn unique-aids
  "Unique count of logged in users at the specified date"
  [f date]
  (->> (line-seq (io/reader f))
       (filter #(s/starts-with? % date))
       (map #(-> (re-seq #"Aid:\s+(\d+)\s+" %) first second))
       distinct
       count
       dec))

(defn ua-list [f-log]
  (->> (line-seq (io/reader f-log))
       (map #(when-let [ua-line (-> (re-seq #"UA:\s+(.*?)\s+Method:" %) first second)]
               (first (s/split ua-line #"/" 2))))
       (filter #(not (s/blank? %)))
       distinct))

(defn comp-stats-by-ua
  [f-ua-list]
  (->> (line-seq (io/reader f-ua-list))
       (map #(-> (s/split % #"/") first))
       (filter identity)
       distinct))

(comment
  (unique-aids "/home/garfield/tmp/remote-chips.log" "16-07-14")
  (ua-list "/home/garfield/tmp/remote-chips.log")
  (comp-stats-by-ua "dev-resources/app-stats.text")
  )

(defn add-fe-as-mtypes []
  (let [okok-users (j/query (mysql-db) ["select id,mtypes from csb_account where company_id=1"])]
    (doseq [{:keys [id mtypes]} okok-users
            :let [mtypes (set (s/split mtypes #","))
                  u-mtypes (conj mtypes "food" "exercise")]]
      (when-not (= mtypes u-mtypes)
        (println "updating mtypes for " id ":" mtypes "->" u-mtypes)
        (j/update! (mysql-db) :csb_account {:mtypes (s/join "," u-mtypes)} ["id=?" id])))))

(defn insert-mdata-by-roleids [filename sheetname]
  (let [[header-row & body-rows] (->>
                                  filename
                                  (sp/load-workbook)
                                  (sp/select-sheet sheetname)
                                  (sp/row-seq)
                                  ;;(remove nil?)
                                  (map sp/cell-seq)
                                  (map #(map sp/read-cell %)))
        headers (map (comp keyword s/lower-case) header-row)]
    (loop [rows body-rows
           roleid nil]
      (when-let [row (first rows)]
        (let [m (zipmap headers row)
              weight (or (:weight m) (+ (rand-int 10) 50))
              roleid (or (first row) roleid)
              aid (:account_id (->entity (mysql-db) :csb_role {:id roleid}))]
          (assert aid)
          (add-mdata {:account_id aid :mdata [(assoc m
                                                     :role_id roleid
                                                     :weight_time (get-time-as-string (- (System/currentTimeMillis) (* (rand-int 7) 24 3600 1000)) :format "yyyy-MM-dd HH:mm:ss")
                                                     :weight weight)]})
          (recur (rest rows) roleid))))))



(defn haiji-push-batch! [csv]
  (with-open [r (io/reader csv)]
    (doseq [{:keys [model userid water bodyfat weight bmi] :as params}
            (->> (line-seq r)
                 (rest)
                 (map (fn [ss] (zipmap [:bodyfat :weight :water :bmi :haier :userid :model] (s/split ss #","))))
                 (filter :userid)
                 ;;(drop-while (fn [{:keys [model]}] (not= model "X228黑")))
                 )]
      (println "haiji-push" params)
      (haiji-push model {:deviceId userid
                         :devType (props [:haiji model :typeid])
                         :devTypeUPlus (props [:haiji model :typeid])
                         :dataType "attr"
                         :args {:attrs [{:name "weight" :value weight}
                                        {:name "bodyFat" :value bodyfat}
                                        {:name "water" :value water}
                                        {:name "bmi" :value bmi}]}}))))

(comment
  (haiji-push-batch! "/home/garfield/projects/clojure/chips/dev-resources/2019年6月26日推送优家数据8168个.csv")
  (haiji-push-batch! "/home/garfield/projects/clojure/chips/dev-resources/2019年1月22日提供给优家APP用户数据.csv")
  (haiji-push-batch! "/home/garfield/projects/clojure/chips/dev-resources/2019年2月21日提供给优家APP用户数据.csv")
  (haiji-push-batch! "/home/garfield/projects/clojure/chips/dev-resources/2019年3月23日提供给优家APP用户数据.csv")
  ;; X228黑
  (haiji-push-batch! "/home/garfield/projects/clojure/chips/dev-resources/2019年4月份提供给优家数据5043个.csv") 
  (insert-mdata-by-roleids (.getAbsolutePath (io/file "/home/garfield/downloads/" "rdata.xlsx")) "Sheet1")

  
  )

;; select count(id) cnt, scale  from csb_last_log group by scale;

(def sin "
| 30985 |                                     |
|   953 | 0,                                  |
| 15939 | 0,0                                 |
|     9 | 0,Chipsea-BLE                       |
|     1 | 0,litchen                           |
|     6 | 0,Q1                                |
|     5 | 0,UNKNOWN                           |
|     1 | 1LS_W_21,0                          |
|     1 | 1LS_W_23,                           |
|     7 | 1LS_W_23,0                          |
|     1 | 1LS_W_23,1LS_W_23                   |
|     1 | 1LS_W_23,Q1                         |
|     4 | 1LS_W_9,0                           |
|     9 | ADV,0                               |
|     2 | ADV,ADV                             |
|     1 | Bracel13-4262,0                     |
|     1 | Braceli5-0461,0                     |
|     1 | Braceli5-2776,0                     |
|     1 | Braceli5-4357,0                     |
|     1 | Braceli5-5302,Braceli5-5302         |
|     1 | Braceli5-8318,0                     |
|     1 | C11,C11                             |
|     5 | C19,0                               |
|     2 | C19,C19                             |
|   304 | Chipsea Cloud Scale,0               |
|    56 | Chipsea-BLE,                        |
|  2362 | Chipsea-BLE,0                       |
|   420 | Chipsea-BLE,Chipsea-BLE             |
|     1 | ED:67:27:3F:40:EC,0                 |
|     1 | ED:67:27:40:49:0E,ED:67:27:40:49:0E |
|     1 | ED:67:27:42:D5:00,0                 |
|     1 | ED:67:27:46:48:D5,ED:67:27:46:48:D5 |
|     1 | ED:67:27:58:95:D3,0                 |
|     1 | ED:67:27:58:99:D0,0                 |
|     1 | ED:67:27:58:BA:73,ED:67:27:58:BA:73 |
|     1 | ED:67:27:59:12:8B,ED:67:27:59:12:8B |
|     1 | ED:67:27:59:72:98,ED:67:27:59:72:98 |
|     1 | ED:67:27:5A:A9:86,ED:67:27:5A:A9:86 |
|     1 | ED:67:27:5A:B0:7F,ED:67:27:5A:B0:7F |
|     1 | ED:67:27:5A:BD:FE,0                 |
|     1 | ED:67:37:29:94:9C,ED:67:37:29:94:9C |
|     1 | ED:67:37:29:BB:57,ED:67:37:29:BB:57 |
|     1 | F2520   ,0                          |
|     2 | HC-C1,0                             |
|    32 | HC-C3,0                             |
|     2 | HC-C3,Chipsea-BLE                   |
|    27 | HC-C3,HC-C3                         |
|     1 | icomon,                             |
|     3 | icomon,0                            |
|     2 | icomon,icomon                       |
|     1 | icomon,Q1                           |
|     1 | LS112-B,0                           |
|     1 | LS212-B,                            |
|     2 | LS212-B,0                           |
|     1 | LS213-B,0                           |
|     1 | LS213-B,Q1                          |
|     1 | LS215-B,0                           |
|     2 | LS_SCA11,0                          |
|     1 | LS_SCA16,Q1                         |
|     1 | PHICOMM S9,0                        |
|    56 | PuTian,                             |
|  2249 | PuTian,0                            |
|     1 | PuTian,Chipsea-BLE                  |
|   367 | PuTian,PuTian                       |
|   199 | Q1,                                 |
| 15975 | Q1,0                                |
|     5 | Q1,Chipsea-BLE                      |
|  4059 | Q1,Q1                               |
|    29 | Q31,                                |
|  1310 | Q31,0                               |
|     2 | Q31,Chipsea-BLE                     |
|   353 | Q31,Q31                             |
|     4 | Q7,                                 |
|   314 | Q7,0                                |
|    87 | Q7,Q7                               |
|     1 | QN-Scale,QN-Scale                   |
|    31 | Sictech_ble,0                       |
|     5 | Sictech_ble,Chipsea-BLE             |
|    14 | Sictech_ble,Sictech_ble             |
|     8 | SWAN,0                              |
|     1 | SWAN,Q1                             |
|     2 | SWAN,SWAN                           |
|   294 | UNKNOWN,                            |
|  1435 | UNKNOWN,0                           |
|     1 | UNKNOWN,Chipsea-BLE                 |
|     1 | UNKNOWN,UNKNOWN                     |
|     7 | weigher,0                           |
")

(defn- increase-scale-cnt [m-cnt scale cnt]
  (if (s/blank? scale)
    (update m-cnt "未声明" (fnil + 0) cnt)
    (update m-cnt scale (fnil + 0) cnt)))

(defn parse-scale-stat [idx sin]
  (let [m-cnt (reduce
               (fn [m line]
                 (let [[_ s-cnt s-scale] (s/split line #"\|")]
                   (if (s/blank? s-cnt)
                     m
                     (let [[h k] (map s/trim (s/split s-scale #","))]
                       (increase-scale-cnt m (nth [h k] idx) (str->num (s/trim s-cnt)))))))
               {}
               (s/split sin #"\n"))
        x-cnt (sort-by second m-cnt)
        wb (sp/create-workbook "Scale"
                               (cons ["Scale" "Count"]
                                     x-cnt))
        sheet (sp/select-sheet "Scale" wb)
        header-row (first (sp/row-seq sheet))]
    (sp/set-row-style! header-row (sp/create-cell-style! wb {:background :yellow,
                                                             :font {:bold true}}))
    (sp/save-workbook! (str (case idx 0 "健康" 1 "厨房") "-scale.xlsx") wb)
    x-cnt))

(comment
  (parse-scale-stat 1 sin) )

;; https://maxmind.github.io/GeoIP2-java/
(defonce geoip-reader 
  (delay
   (.. (DatabaseReader$Builder.
        (io/file "/home/garfield/backup/GeoLite2-City_20190618/GeoLite2-City.mmdb"))
       (withCache (CHMCache.))
       (build))))

;;accountid, roleid, age, sex, register time, last login, last measure, height, weight
(defn all-roles []
  (j/query (mysql-db) ["select account_id aid, id rid, height, sex, birthday, create_time register_time from csb_role"]))

(defn attach-last-log [{:keys [aid] :as role}]
  (if-let [{:keys [ip ua scale ts]} (->entity (mysql-db) :csb_last_log {:aid aid})]
    (assoc role :ip ip :app (second (s/split (or ua "") #"/")) :ua ua :scale (first (s/split (or scale "") #",")) :last_login (java.sql.Date. ts))
    role))

(defn attach-last-measure [{:keys [rid] :as role}]
  (let [qvec ["select weight_time, weight from csb_weight where role_id=? order by weight_time desc limit 1" rid]]
    (if-let [{:keys [weight_time weight]} (first (j/query (mysql-db) qvec))]
      (assoc role :last_measure weight_time :weight weight)
      role)))

(defn convert-birthday-to-age [{:keys [birthday] :as role}]
  (-> role
      (assoc :age (utils/age-from-birthday birthday))
      (dissoc :birthday)))

(defn attach-city [{:keys [ip] :as role}]
  (try
    (let [ip-addr (InetAddress/getByName ip)
          city (.. @geoip-reader
                   (city ip-addr)
                   (getMostSpecificSubdivision)
                   (getName))]
      (assoc role :city city)) 
    (catch Exception e
      role)))

(defn all-user-profile []
  (map
   (fn [row]
     (->> row
          (attach-last-log)
          (attach-last-measure)
          (utils/convert-db-val)
          (convert-birthday-to-age)
          ;;(attach-city)
          ))
   (all-roles)))

(defn all-user-profile-to-xls! []
  (let [x-key [:aid :rid :age :sex :height :weight :last_login :last_measure :register_time :scale :app]
        x-prof (all-user-profile)
        wb (sp/create-workbook "user-profile"
                               (cons (map name x-key)
                                     (map (apply juxt x-key) x-prof)))
        sheet (sp/select-sheet "user-profile" wb)
        header-row (first (sp/row-seq sheet))]
    (sp/set-row-style! header-row (sp/create-cell-style! wb {:background :yellow,
                                                             :font {:bold true}}))
    (sp/save-workbook! (str (long->date-string (System/currentTimeMillis)) "-uers.xlsx") wb)))
