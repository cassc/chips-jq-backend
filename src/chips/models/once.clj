(ns chips.models.once
  "Disposable one-time codes"
  (:require
   [dk.ative.docjure.spreadsheet :as spd]
   [clojure.string               :as s]
   [sparrows.cypher              :refer [md5]]
   [sparrows.time :as time :refer [to-sql-time long->datetime-string from-sql-time start-of-week now-in-millis datetime-string->long]]
   [sparrows.misc                :refer [str->num get-time-as-string]]
   [taoensso.timbre              :as t]
   [chips.config                 :refer :all]
   [chips.utils                  :refer [md5sum? encrypt]]
   [chips.models.base            :refer :all]
   [clojure.java.io              :as io]
   [clojure.java.jdbc            :as j]
   [clojure.string :as s]
   [chips.models.users           :as u :refer [add-user add-role userid->aid]]
   [chips.models.mdata           :refer [add-mdata mtype->table mtype->time-key]])
  (:import
   [org.apache.poi.xssf.usermodel XSSFWorkbook XSSFFont]
   [java.sql Date Timestamp]))

(defn create-rand-user
  "Create a fake user with phone=11122223333 and import data from 18129930760"
  [& [old new]]
  (let [old   (or old 18129930760)
        phone (or new 11122223333)]
    (when-not (seq (j/query (mysql-db) ["select * from csb_account where phone=? and company_id=?" phone 1]))
      (t/info "Copy user and data for" old "to" phone)
      (let [account (first (j/query (mysql-db) ["select * from csb_account where phone=? and company_id=?" old 1]))
            roles (j/query (mysql-db) ["select * from csb_role where account_id = ? and current_state =?" (:id account) 1])
            data (j/query (mysql-db) ["select * from csb_weight where account_id = ?" (:id account)])]
        (j/with-db-transaction [db (mysql-db)]
          (let [aid        (:aid (add-user db {:uid phone  :password (:password account) :company_id (:company_id account)}))
                role-adder (fn [r] (add-role db (-> r
                                                    (select-keys [:nickname :height :birthday :sex])
                                                    (assoc :account_id aid))))
                nrids      (map role-adder roles)
                ridmap     (zipmap (map :id roles) nrids)
                data-adder (fn [m] (j/insert! db :csb_weight
                                              (-> m
                                                  (assoc :account_id aid :role_id (ridmap (:role_id m)))
                                                  (dissoc :id))))]
            (doall
             (map data-adder data))))))
    (t/info "Done!")))

(defn delete-user!
  "Delete user by db ids."
  [ids]
  (doseq [aid (seq ids)]
    (t/warn "Deleting user with id:" aid)
    (when-let [aid (str->num aid)]
      (j/with-db-transaction [db (mysql-db)]
        (j/delete! db :csb_role_day_data ["account_id=?" aid])
        (j/delete! db :csb_role_week_data ["account_id=?" aid])
        (j/delete! db :csb_role_month_data ["account_id=?" aid])
        (j/delete! db :csb_weight_remind ["account_id=?" aid])
        (j/delete! db :csb_weight ["account_id=?" aid])
        (j/delete! db :csb_role ["account_id=?" aid])
        (j/delete! db :csb_account ["id=?" aid])))))



(defn- invalid-mdata?
  [{:keys [measuretime rweight]}]
  (or
   (s/blank? measuretime) (= measuretime "1970-01-01 08:00:00") (> (read-string (subs measuretime 0 4)) 2016) ;; invalid weight_time
   (not rweight) (> rweight 500) ;; invalid weight
   ))

(def ^:private heshi-db-head-file "heshi-db-previous-head.txt")

(defn slurp-when-exists
  []
  (let [f (io/file heshi-db-head-file)]
    (when (.exists f)
      (map read-string (line-seq (io/reader f))))))

(defn add-heshi-role
  [db {:keys [nickname height birthday sex account_id icon weight_goal] :as params}]
  {:pre [nickname account_id birthday sex height]}
  (t/info "Add role: " params)
  (let [
        role {:account_id account_id
              :nickname   nickname
              :birthday   birthday
              :sex        sex
              :height     height
              :icon_image_path icon
              :weight_goal weight_goal}]
    (get-generated-id (j/insert! db :csb_role role))))

;; (defn import-heshi-user-data
;;   "Import all users not exists in okok db from heshi db "
;;   []
;;   (let [[last-user-id last-record-id] (slurp-when-exists)
;;         registers (j/query (heshi-db) ["select * from t_register"])
;;         users (j/query (heshi-db) ["select * from t_user where id>?" (or last-user-id 0)])
;;         records (j/query (heshi-db) ["select * from t_userweight_record where id>?" (or last-record-id 0)])
;;         records (remove invalid-mdata? records)
;;         users (filter :ucode users)
;;         u-w-roles (group-by :ucode users)
;;         role-data (group-by :userno records)]
;;     (j/with-db-transaction [db (mysql-db)]
;;       (doseq [u registers
;;               :let [login-type-map (heshi-email-open-logins (:email u))]
;;               :when login-type-map]
;;         (if (userid->aid (assoc login-type-map :company_id 18))
;;           (t/warn "ignore existing usr" login-type-map)
;;           (let [aid (add-heshi-user db (merge (dissoc u :email) login-type-map ))
;;               roles (u-w-roles (:ucode u))]
;;           (doseq [role roles
;;                   :let [{:keys [sex birthday unit bingtime username macaddress weight lastuptime targetweight userno height userphoto]} role]]
;;             (let [roleid (add-heshi-role db {:account_id  aid
;;                                              :nickname    username
;;                                              :birthday    birthday
;;                                              :icon        (if (or (s/blank? userphoto) (< (count userphoto) 10))
;;                                                             ""
;;                                                             (subs userphoto 10))
;;                                              :sex         (if (zero? sex) "男" "女")
;;                                              :height      height
;;                                              :weight_goal targetweight})]
;;               (doseq [mdata (role-data userno)
;;                       :let [{:keys [rweight rsfat rifat ucode rmuscale rbone ramr measuretime rbodyage scaletype rbmr issync rbmi id comparelast rvisceralfat rbodyfat userno rbodywater]} mdata]]
;;                 (prn "adding mdata" mdata)
;;                 (add-mdata db {:account_id aid
;;                                :mdata [{:role_id roleid
;;                                         :account_id aid
;;                                         :weight_time measuretime
;;                                         :bone rbone
;;                                         :muscle rmuscale
;;                                         :weight rweight
;;                                         :bmi rbmi
;;                                         :body_age 0
;;                                         :water rbodywater
;;                                         :metabolism nil
;;                                         :viscera (or rvisceralfat 0)
;;                                         :axunge rbodyfat}]})))))))
;;       (spit heshi-db-head-file (str (apply max (map :id users))
;;                                     "\n"
;;                                     (apply max (map :id records)))))))

(defn export-users-as-excel!
  "Users grouped by companyid and saved in a spreadsheet"
  []
  (let [users (j/query (mysql-db) ["select phone,company_id from csb_account where phone>?" 0])
        users-by-company (group-by :company_id users)
        wb (XSSFWorkbook.)]
    (doseq [uc users-by-company
            :let [sheet-name (str "companyid-" (first uc))
                  sheet (spd/add-sheet! wb sheet-name)
                  sheet-data (cons ["Phone"] (map (comp vector :phone) (second uc)))]]
      (spd/add-rows! sheet sheet-data))
    (spd/save-workbook! "all-users.xlsx" wb)))

(comment
  (export-users-as-excel!)
  (import-heshi-user-data)
  )

;; (defn- upgrade-password-encryption []
;;   (let [rows (j/query (mysql-db) ["select id,password from csb_account where password <> '' "])
;;         encrypt-row (fn [{:keys [id password]}]
;;                       (when (md5sum? password)
;;                         (j/update! (mysql-db) :csb_account {:password (encrypt password)} ["id=?" id])))]
;;     (dorun (map encrypt-row rows))))

(defn- log-line-reducer [aid->user line]
  (or
   (when-first [[_ aid] (re-seq #"Aid:\s+(\d+)\s+\w+:" line)]
     (when-let [user (-> aid str->num aid->user)]
       (let [ua (-> (re-seq #"UA:\s+?(.*?)\s+?\w+:" line) first second)]
         (when-not (or (s/blank? ua) (= ua "load-test-chips"))
           (when-not (or (s/includes? (s/lower-case ua) "ios")
                         (s/includes? (s/lower-case ua) "android"))
             (t/info line))
           (assoc aid->user aid (update user :ua conj ua))))))
   aid->user))

(defn- handle-logfile [aid->user log]
  (with-open [r (io/reader log)]
    (reduce log-line-reducer aid->user (line-seq r))))

(defn- ua-combiner [u1 u2]
  (update u1 :ua into (:ua u2)))

(defn- ua->phone [ua]
  (or (-> (re-seq #"^.*\((.*?)\)$" ua) first second) ua))

(defn- ua->app [ua]
  (or (-> (re-seq #"^(.*)\(.*?\)$" ua) first second) ua))

(defn- cid->cname [cid]
  (-> (j/query (mysql-db) ["select name from csb_company where id=?" cid]) first :name))

(defn- cid->nusers [cid]
  (-> (j/query (mysql-db) ["select count(id) n from csb_account where company_id=?" cid]) first :n))

(defn- company-stats-reducer [cidfre]
  (reduce-kv
   (fn [m k v]
     (conj m [k (cid->cname k) v (format "%.2f" (/ v (cid->nusers k) 0.01))]))
   [["cid" "cname" "num" "ratio"]]
   cidfre))

(defn- lists-to-readable-stats [no-mdata-users]
  {:ua-stats (->> no-mdata-users
                  (map :ua)
                  (reduce into [])
                  frequencies
                  (sort-by second)
                  (cons ["ua" "num"]))
   :app-stats (->> no-mdata-users
                   (map :ua)
                   (reduce into [])
                   (map ua->app)
                   frequencies
                   (sort-by second)
                   (cons ["app" "num"]))
   :phone-stats (->> no-mdata-users
                     (map :ua)
                     (reduce into [])
                     (map ua->phone)
                     frequencies
                     (sort-by second)
                     (cons ["phone" "num"]))
   :company-stats (->> no-mdata-users
                       (map :company_id)
                       frequencies
                       company-stats-reducer)})

(defn- to-xls [stats]
  (let [wb (XSSFWorkbook.)]
    (doseq [[stats-key sheet-data] stats
            :let [sheet-name (name stats-key)
                  sheet (spd/add-sheet! wb sheet-name)]]
      
      (spd/add-rows! sheet sheet-data))
    (spd/save-workbook! "no-mdata-users.xlsx" wb)))

(defn users-with-no-mdata
  "Stats of users with no mdata"
  [{:keys [with-phone? log-path]}]
  (let [qstr (str "select distinct a.id,phone,company_id from csb_account a left join csb_mdata m  on a.id=m.account_id where m.account_id is null "
                  (when with-phone? " and phone>0"))
        users (j/query (mysql-db) [qstr])
        aid->user (reduce #(assoc % (:id %2) (assoc %2 :ua #{})) {} users)
        logs (filter #(.isFile %) (file-seq (io/file log-path)))
        no-mdata-users  (->> (apply
                              merge-with
                              ua-combiner
                              (pmap (partial handle-logfile aid->user) logs))
                             vals)]
    (-> no-mdata-users lists-to-readable-stats to-xls)))

(defn phone-with-no-mdata
  "Export users with no measurement"
  [cid]
  (let [qstr "select distinct phone from csb_account a left join csb_mdata m  on a.id=m.account_id where a.company_id=? and m.account_id is null and phone>0"
        users (j/query (mysql-db) [qstr cid])]
    (spit "type-2.txt"
          (s/join "\r\n" (map :phone users)))))

(defn phone-with-no-activity
  "Export users with no measurement since last-access"
  ([cid last-access out]
   (phone-with-no-activity cid last-access -1 out))
  ([cid last-access ignore-after out]
   (let [qstr "select phone, max(measure_time) mtime from csb_mdata m, csb_account a where a.id=m.account_id and a.company_id=? group by a.phone"
         users (j/query (mysql-db) [qstr cid])
         phones (->> users
                     (filter #(< ignore-after (from-sql-time (:mtime %)) last-access))
                     (map :phone))]
     
     (spit out (s/join "\r\n" phones)))))

(comment
  (users-with-no-mdata {:with-phone? true :log-path "/opt/apps/remote-logs/chips/"})
  (phone-with-no-mdata 1)
  (phone-with-no-activity 1 (- (time/now-in-millis) (* 2 7 3600 24 1000)) (- (time/now-in-millis) (* 2 30 3600 24 1000)) "type-week.txt") ;; 2 weeks
  (phone-with-no-activity 1 (- (time/now-in-millis) (* 2 30 3600 24 1000)) "type-month.txt") ;; 30 days
  ;; test to ensure no duplicates
  (let [user-no-data (set (line-seq (io/reader "type-2.txt")))
        user-month-before (set (line-seq (io/reader "type-month.txt")))
        user-week-before (set (line-seq (io/reader "type-week.txt")))]
    (print
     (reduce + (map count [user-no-data user-month-before user-week-before]))
     (clojure.set/intersection user-no-data user-month-before)
     (clojure.set/intersection user-no-data user-week-before)
     (clojure.set/intersection user-week-before user-month-before))))


(defn clean-mdata-with-no-roles
  "Remove csb_mdata entries with no roles"
  []
  (j/with-db-transaction [db (mysql-db)]
    (let [qstr "select distinct m.role_id rid from csb_mdata m left join csb_role r on m.role_id=r.id where r.id is null"
          rids (j/query db [qstr] {:row-fn :rid})]
      (dorun
       (map
        #(j/delete! db :csb_mdata ["role_id=?" %])
        rids)))))

(defn gen-fake-mdata-for
  "Generate `n` fake `mtype` data for the role with id `roleid`. Time starts from 600 ages before the current time"
  [mtype roleid n]
  (let [mtype (keyword mtype)
        {:keys [id account_id]} (->entity (mysql-db) :csb_role {:id roleid})
        now (now-in-millis)
        time-key (keyword (mtype->time-key mtype))]
    (letfn [(rand-bp []
              {:sys (+ 100 (rand-int 40))
               :hb (+ 60 (rand 50))
               :dia (+ 60 (rand-int 30))})
            (rand-bsl []
              {:bsl (+ 2.9 (rand 5))
               :description (inc (rand-int 5))})
            (rand-weight [w]
              {:bone (+ 1 (rand 3))
               :scaleweight w
               :muscle (+ 10 (rand 20))
               :weight w
               :bmi (+ 15 (rand 10))
               :body_age 0
               :water (+ 30 (rand 30))
               :metabolism (+ 800 (rand 800))
               :viscera (rand 5)
               :axunge (rand 30)})
            (rand-data-with-mtype [mtype]
              (assoc (case mtype
                       :bp (rand-bp)
                       :bsl (rand-bsl)
                       :weight (rand-weight (+ 30 (rand 30))))
                     :upload_time (to-sql-time now)
                     time-key (to-sql-time (- now (* 1000 (rand-int 51840000))))))
            (insert-rand-mdata []
              (let [mdata (assoc (rand-data-with-mtype mtype) :role_id id :account_id account_id)]
                (j/with-db-transaction [db (mysql-db)]
                  (let [mid (get-generated-id (j/insert! db (mtype->table mtype) mdata))]
                    (j/insert! db :csb_mdata {:role_id id
                                              :account_id account_id
                                              :mtype (name mtype)
                                              :mid mid
                                              :measure_time (mdata time-key)})))))]
      (dorun
       (repeatedly n insert-rand-mdata)))))


(comment
  (do
    (gen-fake-mdata-for :bp 6889 1000)
    (gen-fake-mdata-for :bsl 6889 1000))
  )

(defn- check-or-fix-consistency-by-mtype [fix? [mtype entries]]
  (let [mids (set (map :mid entries))
        mids-by-data (set
                      (j/query (mysql-db)
                               [(str "select id from " (mtype->table mtype))]
                               {:row-fn :id}))]
    (when (not= mids mids-by-data)
      (when-let [not-in-mdata (seq (clojure.set/difference mids mids-by-data))]
        (prn "csb_mdata - " (mtype->table mtype) not-in-mdata)
        (when fix?
          (j/with-db-transaction [db (mysql-db)]
           (dorun (map #(j/delete! db :csb_mdata ["mid=? and mtype=?" % mtype])
                       not-in-mdata)))))
      (when-let [not-in-mtype (seq (clojure.set/difference mids-by-data mids))]
        (prn (mtype->table mtype) " - csb_mdata" not-in-mtype)
        (when fix?
          (j/with-db-transaction [db (mysql-db)]
            (dorun
             (map (fn [mid]
                    (let [{:keys [weight_time account_id role_id] :as data} (first (j/query db [(str "select * from " (mtype->table mtype)  " where id=?") mid]))]
                      (j/insert! db :csb_mdata {:mid mid :measure_time weight_time :account_id account_id :role_id role_id :mtype mtype})))
                  not-in-mtype))))))))

(defn- check-or-fix-mdata-consistency
  "Check and optionally fix inconsistency between csb_mdata and csb_bp,bsl,weight tables"
  [fix?]
  (let [mdata (j/query (mysql-db) ["select mtype,mid from csb_mdata"])
        mdata-by-mtype (group-by :mtype mdata)]
    (doseq [tm mdata-by-mtype]
      (check-or-fix-consistency-by-mtype fix? tm)))  )

(defn check-mdata-consistency
  []
  (check-or-fix-mdata-consistency false))

(defn fix-mdata-consistency
  "Fix inconsistency between csb_mdata with csb_bp,bsl,weight tables. Mainly caused by importing heshi db"
  []
  (check-or-fix-mdata-consistency true))

;; lein run -m chips.models.once/fix-mdata-consistency

(defn manual-weight-users
  "导出只手动添加过数据，无设备称重的用户"
  []
  (let [users (j/query (mysql-db) ["select id,phone,email,qq,sina_blog,weixin,register_time from csb_account where id in (select distinct a.account_id from (  select account_id from csb_weight where productid=101) a left join (  select account_id from csb_weight where productid!=101 ) b on a.account_id=b.account_id where b.account_id is null) order by register_time desc"]
                       {:row-fn (fn [{:keys [id] :as account}]
                                  (assoc account :recent_weight (-> (j/query (mysql-db) ["select weight_time from csb_weight where account_id=? order by weight_time desc limit 1" id])
                                                                    first
                                                                    :weight_time)))})
        wb (XSSFWorkbook.)
        sheet (spd/add-sheet! wb "无设备称重，但有手动添加数据的用户")
        header-line (map name (keys (first users)))
        vals-line (map vals users)]
    (t/info "Found" (count users) "users")
    (spd/add-rows! sheet (cons header-line vals-line))
    (spd/save-workbook! "manual-mdata-users.xls" wb)))

(defn export-food-sample []
  (let [all-food (j/query (mysql-db) ["select * from fe.food"])
        x-food (repeatedly 10 #(rand-nth all-food))
        wb (XSSFWorkbook.)
        sheet (spd/add-sheet! wb "food")
        header-line (map name (keys (first x-food)))
        vals-line (map vals x-food)]
    (spd/add-rows! sheet (cons header-line vals-line))
    (spd/save-workbook! "food-sample.xls" wb)))

(defn- write-rows-to-xls [title rows]
  (let [wb (XSSFWorkbook.)
        sheet (spd/add-sheet! wb title)
        header-line (map name (keys (first rows)))
        vals-line (map vals rows)]
    (spd/add-rows! sheet (cons header-line vals-line))
    (spd/save-workbook! (str title ".xls") wb)))

(defn export-users-mdata []
  (let [all-aids (j/query (mysql-db) ["select id,haier from csb_account order by id desc"])
        all-weight (filter
                    identity
                    (map
                     (fn [{:keys [id haier]}]
                       (when-first [row (j/query (mysql-db) ["select axunge bodyFat,weight,water,bmi from csb_weight where account_id=? order by id desc limit 1" id])]
                         (assoc row :phone haier)))
                     all-aids))
        wb (XSSFWorkbook.)
        sheet (spd/add-sheet! wb "weight")
        header-line (map name (keys (first all-weight)))
        vals-line (map vals all-weight)]
    (spd/add-rows! sheet (cons header-line vals-line))
    (spd/save-workbook! "weigth.xls" wb)))

(defn import-coupons []
  (with-open [r (io/reader "dev-resources/coupon.txt")]
    (doseq [line (line-seq r)
            :when (not (s/blank? line))]
      (let [[coupon score] (map s/trim (s/split line #"\s+"))]
        (try
          (j/insert! (mysql-db) :j_jifen_coupon {:coupon coupon
                                                 :score (str->num score)
                                                 :ts (System/currentTimeMillis)
                                                 :status "valid"})
          (catch Exception ignored))))))

(defn jifen-stats! []
  (let [x-score (sort-by
                 :score
                 (j/query (mysql-db) ["select sum(score) score, account_id aid from csb_jifen group by account_id"]))]
    (write-rows-to-xls "jifen" x-score)))
