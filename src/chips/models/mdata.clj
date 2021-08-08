(ns chips.models.mdata
  "增删数据依赖于`002-log-trigger.sql`中的trigger"
  (:require
   [chips.config :refer :all]
   [chips.utils :refer [convert-sql-val exclusive-or super-of? days-before unhexify]]
   [chips.utility :refer [wrap-async restore-mr parse-eight-resitance]]
   [chips.models.base :refer :all]

   [sparrows.cypher :refer :all]
   [sparrows.time :as time :refer [to-sql-time long->datetime-string from-sql-time start-of-week]]
   [sparrows.misc :refer [get-time-as-string dissoc-nil-val wrap-exception]]
   
   [taoensso.timbre :as t]
   [cheshire.core :refer [generate-string parse-string]]
   [clojure.java.jdbc :as j]
   [clojure.string :as s])
  (:import
   [java.text SimpleDateFormat]
   [java.util Calendar Date]
   [java.sql Timestamp]))


(def ^:private mdata-insert-keys
  [:account_id :bone :muscle :weight :bmi :body_age :role_id :weight_time :water :metabolism :viscera :axunge :bw :score :age :sex :height :rn8])

(defn- datestring-to-date [s pattern]
  (time/date-string->long s {:pattern pattern :offset "+8"}))

(defn- weight?
  [mtype]
  (= mtype :weight))

(defn mtype->table
  [mtype]
  (str "csb_" (name mtype)))

(defn mtype->time-key [mtype]
  (cond
    (weight? mtype) "weight_time"
    (#{:food :exercise} mtype) "date"
    :default "measure_time"))

(defn time->mdata
  "`mtype` should be a keyword, i.e., one of :bsl, :bp, :weight"
  [{:keys [role_id mtype measure_time weight_time]}]
  {:pre [role_id mtype]}
  (let [qstr (str "select * from " (mtype->table mtype)
                  " where role_id=? and " (mtype->time-key mtype) " =? limit 1")]
    (first
     (j/query (mysql-db) [qstr role_id (or measure_time weight_time)] {:row-fn convert-sql-val}))))

(defn fix-time
  [time-str]
  (if (or (s/blank? time-str) (s/starts-with? time-str "1970"))
    (do
      (t/warn "fix invalid weight_time" time-str)
      (get-time-as-string (System/currentTimeMillis) :format "yyyy-MM-dd HH:mm:ss"))
    time-str))

(defn- fix-measure-time
  [{:keys [measure_time weight_time] :as m}]
  (cond
    measure_time (assoc m :measure_time (fix-time measure_time))
    weight_time (assoc m :weight_time (fix-time weight_time))
    :else m))

(defn- prev-mr-by-roleid [roleid weight_time]
  (let [qstr "select * from csb_weight where role_id=? and weight_time<? order by weight_time desc limit 1"]
    (when-first [{:keys [weight resistance] :as w} (j/query (mysql-db) [qstr roleid weight_time])]
      (when (and (not (s/blank? resistance)) (pos? weight))
        (restore-mr
         (assoc w :arr-resist ((wrap-exception unhexify) resistance)))))))

(defn attach-for-weight-resitance
  [{:keys [role_id weight_time resistance iseightr sex age height mtype] :as m}]
  (let [iseightr (if (= iseightr "y") "y" "n")
        arr-resist (when-not (s/blank? resistance)
                     ((wrap-exception unhexify) resistance))
        prev-mr (when arr-resist
                  (prev-mr-by-roleid role_id weight_time))
        [_ _ m-readable] (when arr-resist
                           (parse-eight-resitance
                            (assoc m
                                   :weight_time (Date. (time/datetime-string->long weight_time))
                                   :prev-mr prev-mr
                                   :eight-r? (= iseightr "y")
                                   :arr-resist arr-resist)))]
    (if m-readable
      (assoc m :readable m-readable)
      m)))

(defn- movable? [mtype]
  (#{:weight :bp :bsl} mtype))

(defn- mtype->del-table
  [mtype]
  (when (movable? mtype)
    (str "csb_del_" (name mtype))))

(def weigh-keys [:account_id :bone :muscle :upload_time :weight :id :bmi :body_age :role_id :weight_time :water :metabolism :viscera :axunge :scaleweight :scaleproperty :productid :r1 :bw :score :age :sex :height :rn8 :heartbeat]) ;; :resistance  :iseightr
(def w-extra-keys [:wid :BFR :BMR :FC :BodyAge :FM :MC :MSW :PM :SLM :SMM :Score :TF :TFR :VFR :WC :RABFR :RASLM :LABFR :TRBFR :TRSLM :RLBFR :RLSLM :LLBFR :LLSLM :LASLM :WHR :v10_resistance])
(def w-extra-keys-lower (mapv (comp keyword s/lower-case name) [:wid :BFR :BMR :FC :BodyAge :FM :MC :MSW :PM :SLM :SMM :Score :TF :TFR :VFR :WC :RABFR :RASLM :LABFR :TRBFR :TRSLM :RLBFR :RLSLM :LLBFR :LLSLM :LASLM :WHR :v10_resistance]))
(def w-extra-key-map
  (zipmap w-extra-keys-lower w-extra-keys))
(def bp-keys [:account_id :upload_time :id :role_id :measure_time :sys :dia :hb])
(def bsl-keys [:account_id :upload_time :id :role_id :measure_time :bsl :description])
(def food-keys [:account_id :upload_time :id :role_id :date :food_id :name :quantity :unit :calory :ftype :metabolism])
(def exercise-keys [:account_id :upload_time :id :role_id :date :ex_id :name :duration :calory :metabolism])
(def training-keys [:account_id :upload_time :id :role_id :measure_time :calory :metabolism :tid])
(def mtype-keys
  {:weight weigh-keys
   :bp bp-keys
   :bsl bsl-keys
   :food food-keys
   :exercise exercise-keys
   :training training-keys})

(defn mdata->mtype
  [{:keys [mtype]}]
  (if (nil? mtype)
    :weight
    (keyword mtype)))

(defn fix-food-unit
  "convert food unit to string"
  [{:keys [unit mtype] :as m}]
  (if (and (= :food mtype) (not (string? unit)))
    (assoc m :unit (generate-string unit))
    m))

(defn- insert-weight-extra [db row]
  (let [extra (select-keys row w-extra-keys)]
    (when (seq (dissoc extra :wid))
      (j/insert! db :w_extra extra))))

(defn- insert-mdata-row [db-conn m]
  (let [role_id (:role_id m)
        account_id (:account_id m)
        mtype (mdata->mtype m)
        mtable (mtype->table mtype)
        upload_time (to-sql-time (System/currentTimeMillis))
        row (-> m
                ;; TODO assert existence of vals by mtype
                ;; e.g., weight should exist for mtype=weight
                ;; attach-for-weight-resitance
                fix-measure-time
                fix-food-unit
                (assoc :upload_time upload_time))
        measure_time (some row [:measure_time :weight_time :date])]
    (assoc
     (try
       (j/with-db-transaction [db db-conn]
         (let [db-row (select-keys row (mtype-keys mtype))
               id (get-generated-id (insert! db mtable db-row))]
           (when (= mtype :weight)
             (insert-weight-extra db (assoc row :wid id)))
           (insert! db :csb_mdata {:mid id :measure_time measure_time :account_id account_id :role_id role_id :mtype (name mtype)})
           (assoc (select-keys row [:weight_time :measure_time :date :readable]) :role_id role_id :id id)))
       (catch java.sql.SQLException e ;; this wont happen often
         (case (.getErrorCode e)
           1062 ;; duplicate key
           (let [ed (time->mdata (assoc m :mtype mtype))]
             (cond
               (or (weight? mtype)
                   (= :training mtype))
               (assoc ed :exists "t")
               (#{:bp :bsl} mtype) (let [id (:id ed)]
                                     (update! db-conn mtable row ["id=?" id])
                                     (assoc (select-keys row [:weight_time :measure_time]) :role_id role_id :id id :exists "u"))
               :else (throw e)))
           
           1264 ;; data truncation
           (do
             (t/info e)
             (assoc row :invalid "t"))
           
           (do (t/error "add-mdata error" m)
               (throw e)))))
     :mtype mtype)))

;; WARN not atomic
(defn add-mdata
  "Add rows of mdata for this account.
  
  UniqueKey constraint violation will be captured for every db
  insertion."
  ([params]
   (add-mdata (mysql-db) params))
  ([db {:keys [mdata account_id]}]
   {:pre [account_id (seq mdata)]}
   (mapv #(insert-mdata-row db (assoc % :account_id account_id)) mdata)
   ;; (let [out (mapv #(insert-mdata-row db (assoc % :account_id account_id)) mdata)]
   ;;   (topup-score-by-mdata account_id)
   ;;   out)
   ))

;; batch insert: no update/ignore upon duplicate supported
;;(get-batch-generated-ids (apply (partial j/insert! (mysql-db) :csb_weight) mdata))

;; execute insert: wont return autogen id
(defn delete-mdata
  "Delete weight data permanently."
  {:deprecated "0.1.5"}
  [{:keys [mids account_id]}]
  {:pre [account_id (seq mids)]}
  (j/with-db-transaction [db (mysql-db)]
    (doseq [id mids]
      (delete! db :csb_weight ["id=? and account_id=?" id account_id]))))

(defn move-to-del-mdata
  "Move deleted mdata to csb_del_weight table. "
  [{:keys [mids account_id mtype]}]
  {:pre [account_id (seq mids) mtype]}
  (let [n         (count mids)
        ins       (s/join "," (repeat n "?"))
        table     (mtype->table mtype)
        delstr    (str "delete from " table " where account_id=? and id in (" ins ")" )
        delvec    (vec (list* delstr account_id mids))
        del-mdata-str (str "delete from csb_mdata where account_id=? and mtype=? and mid in ("  ins ")" )
        del-mdata-vec    (vec (list* del-mdata-str account_id (name mtype) mids))]
    (j/with-db-transaction [db (mysql-db)]
      #_(when-let [del-table (mtype->del-table mtype)]
        (let [mv-str      (str "insert " del-table
                             " select * from " table " d "
                             "  where d.account_id=? and d.id in (" ins ")")
              mv-vec      (vec (list* mv-str account_id mids))]
          (execute! db mv-vec)))
      (execute! db delvec )
      (execute! db del-mdata-vec))))


(defn last-syncid
  []
  (first
   (j/query
    (mysql-db)
    ["select id from csb_role_data_log order by id desc limit 1"]
    {:row-fn :id})))

(defn- get-mdata-start-time [{:keys [end cnt-by-days account_id role_id mtype all?]}]
  (let [qstr (str "select distinct date_format(measure_time, '%Y-%m-%d') date from csb_mdata where account_id=? "
                  (when role_id " and role_id=?")
                  (when-not all?
                    (str " and mtype in (" (s/join "," (repeat (count mtype) "?")) ")"))
                  " and measure_time < ? "
                  " order by measure_time desc limit ?")
        qvec (vec (filter identity (concat [qstr account_id role_id]
                                           (when-not all?
                                             (map name mtype))
                                           [(Timestamp. end) cnt-by-days])))]
    (some->
     (j/query (mysql-db) qvec)
     last
     :date
     (datestring-to-date "yyyy-MM-dd"))))

(comment
  (get-mdata-start-time {:account_id 566 :cnt-by-days 100 :end (System/currentTimeMillis) :mtype :all})
  )

(defn- transform-extra-keys [weight]
  (reduce-kv
   (fn [m k v]
     (assoc m (or (w-extra-key-map k) k) v))
   {}
   weight))

(defn- attach-w-extra [{:keys [id] :as w}]
  (if-let [extra (->entity (mysql-db) :w_extra {:wid id})]
    (merge w (-> (select-keys extra w-extra-keys-lower)
                 (dissoc :wid)
                 (transform-extra-keys)))
    w))

(defn get-mdata
  "Get mdata list by mtype. Get a list of mdata with mixed types if `mtype=:all`."
  [{:keys [account_id role_id start cnt cnt-by-days end mtype] :as m}]
  {:pre [account_id end mtype]}
  (let [all? (= :all mtype)
        mtype (if (sequential? mtype) mtype [mtype])
        start (or start (when cnt-by-days (get-mdata-start-time (assoc m :mtype mtype :all? all?))))
        qstr (str "select * from csb_mdata where"
                  (if role_id " role_id=?"  " account_id=? ")
                  (when-not all?
                    (str " and mtype in (" (s/join "," (repeat (count mtype) "?")) ")"))
                  " and measure_time < ? "
                  (when start " and measure_time >= ? ")
                  " order by measure_time desc" 
                  (when cnt " limit ?"))
        qlist  (concat [qstr (or role_id account_id)]
                       (when-not all?
                         (map name mtype))
                       [(Timestamp. end) (when start (Timestamp. start)) cnt])
        qvec  (vec (filter identity qlist))]
    (t/info qvec)
    (pmap (fn [{:keys [mid mtype]}]
            (let [mdata (convert-sql-val (assoc (->entity (mysql-db) (mtype->table mtype) {:id mid}) :mtype mtype))]
              (if (= :weight (keyword mtype))
                (attach-w-extra mdata)
                mdata)))
          (j/query (mysql-db) qvec))))

(comment
  (time (last (get-mdata {:account_id 17 :role_id 22 :cnt-by-days 10 :end (System/currentTimeMillis) :mtype :all})))
  (get-mdata {:account_id 17 :role_id 22 :end 1477583999000 :start 1477497601000 :mtype :food})
  (get-mdata {:account_id 23677 :role_id 41700 :end 1480681655000 :mtype '(:food :exercise :bsl :bp :weight) :cnt-by-days 5})
  )

(defn sync-mdata-v2
  [{:keys [account_id role_id lastsync start end mtype]}]
  {:pre [account_id lastsync start end mtype]}
  (let [all?     (= :all mtype)
        qstr     (str
                  "select data_id, opt, mtype from csb_role_data_log where account_id=? "
                  (when role_id " and role_id=?")
                  (cond
                    all? nil
                    (keyword? mtype) " and mtype=?"
                    :seq-mtype (str " and mtype in (" (s/join "," (repeat (count mtype) "?")) ")"))
                  " and ts>? "
                  " and measure_time>=? and measure_time<?")
        qlist    (concat [qstr account_id role_id]
                         (cond
                           all? [nil]
                           (keyword? mtype) [(name mtype)]
                           :seq-mtype (map name mtype))
                         [lastsync (Timestamp. start) (Timestamp. end)])
        qvec     (vec (filter identity qlist))
        _        (t/debug qvec)
        rows     (j/query (mysql-db) qvec)
        sync-map (group-by :opt rows)
        deleted  (set (map (juxt :mtype :data_id) (get sync-map "d")))
        added    (set (map (juxt :mtype :data_id) (get sync-map "i")))]
    {:added   (remove deleted added)
     :role_id role_id
     :deleted deleted}))

(defn sync-mdata-v1
  [{:keys [account_id role_id lastsync start end]}]
  {:pre [account_id lastsync start end]}
  (let [qstr     (str
                  "select data_id, opt from csb_role_data_log where account_id=? "
                  " and mtype=?"
                  (when role_id " and role_id=?")
                  "    and ts>? "
                  "    and measure_time>=? and measure_time<?")
        qlist    (filter identity [account_id "weight" role_id lastsync (Timestamp. start) (Timestamp. end)])
        qvec     (vec (list* qstr qlist))
        _        (t/debug qvec)
        rows     (j/query (mysql-db) qvec)
        sync-map (group-by :opt rows)
        deleted  (set (map :data_id (get sync-map "d")))
        added    (set (map :data_id (get sync-map "i")))]
    {:added   (remove deleted added)
     :role_id role_id
     :deleted deleted}))

(defn mids->mdata
  [{:keys [mids role_id account_id mtype]}]
  {:pre [account_id mids mtype (keyword? mtype)]}
  (let [table (mtype->table mtype)
        qstr (str "select *, '" (name mtype) "' as mtype from " table " where account_id=? and id in ("
                  (s/join "," mids)
                  ") order by " (case mtype
                                  :weight "weight_time"
                                  :food "date"
                                  :exercise "date"
                                  "measure_time")
                  " desc")]
    (j/query (mysql-db) [qstr account_id] {:row-fn convert-sql-val})))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get-mdata stats
(def ^:private avg-weight-keys [:bone :muscle :weight :bmi :body_age :water :metabolism :viscera :axunge])
(def agg-bp-keys [:sys :dia :hb])
(def agg-bsl-keys [:bsl])
(def agg-food-keys [:calory :metabolism])
(def agg-exercise-keys [:calory :metabolism])
(def agg-mtype-keys {:weight avg-weight-keys :bp agg-bp-keys :bsl agg-bsl-keys :food agg-food-keys :exercise agg-exercise-keys})

(defn- format-time
  [tf {:keys [weight_time]}]
  (-> weight_time
      from-sql-time
      (long->datetime-string {:pattern tf :offset "+8"})))

(defn- avg-of [rows k]
  (when-let [vals (seq (filter #(when % (pos? %)) (map k rows)))]
    (/ (Math/round (/ (reduce + 0 vals) (count vals) 0.01)) 100.0)))

(defn- min-max-of [rows k]
  (when-let [vals (seq (filter #(when % (pos? %)) (map k rows)))]
    {:min (apply min vals) :max (apply max vals)}))

(defn- avg
  [rows]
  (zipmap avg-weight-keys (map (partial avg-of rows) avg-weight-keys)))

(defn- sum-of [rows k]
  (when-let [vals (seq (filter #(when % (pos? %)) (map k rows)))]
    (reduce + 0 vals)))

(defn saturday-of-week [ts]
  ;; with Locale/US saturday is day 7 of week
  (start-of-week ts {:week-start 7}))

(defn get-mdata-stats-v1
  "Get mdata stats by role_id, `start` and `end` must be strings with format `yyyyMMdd`"
  [{:keys [start end period role_id] :as params}]
  {:pre [start end role_id period]}
  (t/info "v1 stats of" params)
  (let [pattern-map {:pattern "yyyyMMdd" :offset "+8"}
        [start end] (map #(time/date-string->long % pattern-map) [start end])
        [start end] (case period
                      :week [(time/start-of-week start) (time/start-of-week end)]
                      :day [start end])]
    (when-let [data (seq
                     (j/query
                      (mysql-db)
                      ["select * from csb_weight where role_id=? and weight_time >=? and weight_time<?"
                       role_id (to-sql-time start) (to-sql-time end)]))]
      (let [day-fn (partial format-time "yyyyMMdd")
            week-fn (when (= period :week)
                      (fn [[ds _]]
                        (time/long->string
                         (saturday-of-week
                          (time/string->long ds pattern-map))
                         pattern-map)))
            day-data (reduce
                      (fn [m [k rows]] (assoc m k (avg rows)))
                      {}
                      (group-by day-fn data))]
        (case period
          :day day-data
          :week (reduce
                 (fn [m [k rows]]
                   (assoc m k (avg (map second rows))))
                 {}
                 (group-by week-fn day-data)))))))

(defn- format-time-with
  "Convert weight_time/measure_time to string with format `fmt`"
  [fmt time-key]
  (fn [row]
    (let [mtime (get row (keyword time-key))
          period (time/long->string (from-sql-time mtime) {:pattern fmt :offset "+8"})]
      (assoc row :period period))))

(defn- mean-of [rows k]
  (when-let [vals (seq (filter #(when % (pos? %)) (map k rows)))]
    (let [n (count vals)]
      (nth (sort vals) (int (/ n 2))))))

(let [avg-weight-keys [:bone :muscle :weight :bmi :body_age :water :metabolism :viscera :axunge]
      agg-bp-keys {:sys min-max-of
                   :dia min-max-of
                   :hb min-max-of}
      agg-bsl-keys {:bsl min-max-of}
      agg-food-keys {:calory sum-of :metabolism mean-of}
      agg-exercise-keys {:calory sum-of :metabolism mean-of}
      agg-mtype-keys {:weight avg-weight-keys :bp agg-bp-keys :bsl agg-bsl-keys :food agg-food-keys :exercise agg-exercise-keys}
      default-handler avg-of]
  (defn agg-handler [mtype rows]
    {:pre [(keyword? mtype)]}
    (let [aggs (agg-mtype-keys mtype)
          aggs (if (vector? aggs)
                 (into {} (map vector aggs (repeat default-handler)))
                 aggs)]
      (reduce-kv
       (fn [m key handler]
         (assoc m key (handler rows key)))
       {}
       aggs))))


(defn- aggravate-by-mtype
  [mtype rows]
  (dissoc-nil-val
   (assoc
    (agg-handler mtype rows)
    :nums (count rows))))

(defn- trend-stats-by-mtype
  [{:keys [mtype role_id start end fmt]}]
  (let [table    (name (mtype->table mtype))
        time-key (cond
                   (= mtype :weight) "weight_time"
                   (#{:food :exercise} mtype) "date"
                   :else "measure_time")
        qvec     [(str "select * from " table " where role_id=? and " time-key " >=? and " time-key " <?")
                  role_id (to-sql-time start) (to-sql-time end)]
        _        (t/info qvec)
        raw-rows (->> (j/query (mysql-db) qvec)
                      (map (format-time-with fmt time-key))
                      (group-by :period))]
    {mtype
     (reduce-kv (fn [m period rows]
                  (assoc m period (aggravate-by-mtype mtype rows)))
                {}
                raw-rows)}))

(defn get-mdata-stats-v2
  "Get mdata stats by role_id, `start` and `end` must be strings with format `yyyyMMdd`

  If `mtype=:all`, returns stats for multiple mdata types.

  When `ptype=2`, i.e., grouped by week number of year, `(end-start)`
  time should be less than one year"
  [{:keys [start end ptype role_id mtype] :as params}]
  {:pre [ptype start end role_id mtype]}
  (let [fmt (case ptype
              1 "yyyyMMdd"
              2 "w"
              3 "yyyyMM")
        m {:role_id role_id :start start :end end :fmt fmt}]
    (cond
      (= :all mtype)
      (apply merge {} (map #(trend-stats-by-mtype (assoc m :mtype %)) valid-mtypes))

      (seq? mtype)
      (apply merge {} (map #(trend-stats-by-mtype (assoc m :mtype %)) mtype))

      :single-mtype
      (trend-stats-by-mtype (assoc m :mtype mtype)))))

(comment
  (trend-stats-by-mtype {:mtype :weight :role_id 5170 :start 0 :end 1464243354000 :fmt "yyyyMMdd"})
  (get-mdata-stats-v2 {:mtype :all :role_id 9 :start 0 :end 1464243354000 :ptype 1})
  ;; stats grouped by weeks
  (get-mdata-stats-v2 {:start 1462895999000 :end 1465315199000 :ptype 2 :role_id 1209 :mtype :weight})
  )

(comment
  (get-mdata-stats {:offset 8 :start "20150601" :end "20151010" :role_id 144 :period :day})
  (get-mdata-stats {:offset 8 :start "20150601" :end "20151010" :role_id 144 :period :week})
  (get-mdata-stats-v2 {:start 0 :end (System/currentTimeMillis) :role_id 27 :ptype 1 :mtype :all})
  (get-mdata-stats-v2 {:start 0 :end (System/currentTimeMillis) :role_id 27 :ptype 1 :mtype :bsl})
  (get-mdata-stats-v2 {:start 0 :end (System/currentTimeMillis) :role_id 1209 :ptype 1 :mtype :all})
  (move-to-del-mdata {:account_id 14 :mids [8019 8015 8008]})
  )

(defn set-weight-init-by-roleid
  "Set weight_init for this user"
  [roleid]
  {:pre [roleid]}
  (let [estr "update csb_role set weight_init = (select weight from csb_weight where role_id=? order by weight_time desc limit 1) where id=? and weight_init is null"]
    (j/execute! (mysql-db) [estr roleid roleid])))

(defn weight-init-intialize
  "Set weight_init for v1 users"
  []
  (doseq [role (j/query (mysql-db) ["select r.id from csb_role r where exists (select id from csb_weight w where w.role_id=r.id) and r.weight_init is null"])]
    (set-weight-init-by-roleid (:id role))))


(defn insert-haier-weight [{:keys [readable] :as params}]
  (try
    (j/insert! (mysql-db) :h_weight (assoc params :readable (generate-string readable)))
    (catch java.sql.SQLException e ;; this wont happen often
      (when-not (s/includes? (s/lower-case (or (.getMessage e) ""))
                             "duplicate")
        (throw e)))))

(defn get-haier-mdata [{:keys [aid rid lastts cnt]}]
  {:pre [aid]}
  (let [rid (when-not (s/blank? rid) rid)
        cnt (or cnt 20)
        qstr (str "select * from h_weight where aid=? "
                  (when rid " and rid=?")
                  (when lastts " and ts < ?")
                  " order by ts desc limit ?")
        qvec (filter identity (cons qstr [aid rid lastts cnt]))]
    (map (fn [{:keys [readable] :as m}]
           (assoc m :readable (when-not (s/blank? readable)
                                (parse-string readable true))))
         (j/query (mysql-db) qvec))))

(defn he-prev-weight [{:keys [aid rid before]}]
  {:pre [aid rid]}
  (let [qstr "select * from h_weight where aid=? and rid=? and ts < ? order by ts desc limit 1"
        qvec [qstr aid rid before]]
    (first (j/query (mysql-db) qvec))))
