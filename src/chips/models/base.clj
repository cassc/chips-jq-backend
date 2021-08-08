(ns chips.models.base
  (:require
   [chips.utils :refer [now-as-long today-as-string n-days-before-as-string]]
   [rpc.client :refer [mok-call]]
   [clojure.java.io      :as io]
   [clojure.core.async   :refer [go-loop timeout <!]]
   [clojure.string       :as s]
   [clojure.core.memoize :refer [memo-clear!] :as memo]
   [taoensso.timbre      :as t]
   [sparrows.misc        :refer [dissoc-nil-val str->num wrap-exception]]
   [sparrows.time :refer [long->date-string]]
   [chips.config         :refer [props cache-most-active-users]]
   [clojure.java.jdbc    :as j])
  (:import
   [java.text SimpleDateFormat]
   [java.util Date]
   [java.io File]
   [com.zaxxer.hikari HikariDataSource HikariConfig]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; db pool
;; https://github.com/brettwooldridge/HikariCP
(defn- pool
  "Create database pool with a db configuration map."
  [spec]
  (let [config
        (doto (HikariConfig.)
          (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
          (.setUsername (:user spec))
          (.setPassword (:password spec))
          (.setIdleTimeout (or (:idle-timeout spec) 30000))
          (.setMaximumPoolSize (or (:max-pool-size spec) 10))
          (.setConnectionInitSql (:connection-init-sql spec))
          (.addDataSourceProperty "cachePrepStmts" "true")
          (.addDataSourceProperty "prepStmtCacheSize" "250")
          (.addDataSourceProperty "prepStmtCacheSqlLimit" "2048")
          (.setValidationTimeout 5000))]
    {:datasource (HikariDataSource. config)}))

(def ^:private db
  (letfn [(creator [m key]
            (assoc m key (delay (pool (props key)))))]
    (reduce creator {} [:core-db :fe-db :bh-db :ym-db :admin-db])))

(defn get-db [key]
  @(db key))

(defn mysql-db [] (get-db :core-db))

(defn fe-db [] (get-db :fe-db))

(defn admin-db [] (get-db :admin-db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DB ops hooks
;; WARN: when auto_incremented primary key `id` is not
;; provided, DONOT use execute! to perform insertion. 
(defonce db-hook-store (atom {}))

(def ^:private hooked-db-actions #{"insert-multi!" "insert!" "delete!" "update!" "execute!" "query"} )

(defn register-db-hook!
  [action hook-map]
  {:pre [(hooked-db-actions action)]}
  (swap! db-hook-store update-in [action] conj hook-map))

(letfn [(hooks-for-action [hook-type-keyword action]
          (filter identity (map hook-type-keyword (@db-hook-store action))))]
  (def pre-hook-for-action (partial hooks-for-action :pre-hook))
  (def post-hook-for-action (partial hooks-for-action :post-hook)))

(defmacro wrap-db-hooks
  [db-action]
  ;; (assert (#{"insert!" "delete!" "update!" "execute!"} db-action))
  `(defn ~(symbol db-action) [db# & args#]
     (doseq [pre-hook# (pre-hook-for-action ~db-action)]
       (pre-hook# db# args#))
     (let [resp# (apply ~(symbol "clojure.java.jdbc" db-action) db# args#)]
       (doseq [post-hook# (post-hook-for-action ~db-action)]
         (post-hook# db# resp# args#))
       resp#)))

(wrap-db-hooks "insert!")
(wrap-db-hooks "insert-multi!")
(wrap-db-hooks "delete!")
(wrap-db-hooks "update!")
(wrap-db-hooks "execute!")
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- get-id
  [r]
  (or (:generated_key r)
      (:insert_id r)))

(defn get-generated-id
  "Get the generated id from the response of a db insert operation"
  [resp]
  (get-id (first resp)))


(defn get-batch-generated-ids
  "Get a seq of the generated ids from the response of a db batch-insert operation"
  [resp]
  (map get-id resp))

(defn ->entity
  "Get one entity matching the required condition"
  [db table qmap]
  {:pre [db table (seq qmap)]}
  (let [qvec
        (let [ks    (map name (keys qmap))
              qstr  (reduce str
                            (str "select * from " (name table) " where 1=1 ")
                            (map #(str " and " % "=? ") ks))
              qstr (str qstr " limit 1")
              qargs (mapv #(qmap (keyword %)) ks)]
          (vec (list* qstr qargs)))]
    (first (j/query db qvec {:row-fn dissoc-nil-val}))))

(defn ->entities
  "Query table using map `qmap` as condition. Each key of `qmap` must
  map exactly to one column name in table.

  Returns a list of matching entities.
  Returns all entries if `qmap` is empty."
  [db table qmap]
  {:pre [db table]}
  (let [qvec
        (if (seq qmap)
          (let [ks    (map name (keys qmap))
                qstr  (reduce str
                              (str "select * from " (name table) " where 1=1 ")
                              (map #(str " and " % "=? ") ks))
                qargs (mapv #(qmap (keyword %)) ks)]
            (vec (list* qstr qargs)))
          [(str "select * from " (name table))])]
    (j/query db qvec)))

(defn get-all-company-ids
  []
  (j/query (mysql-db) ["select id from csb_company"] {:row-fn :id}))

(def  all-company-ids
  (memo/ttl get-all-company-ids :ttl/threshold 600000))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dynamic bind companyid
;; TODO maybe move out of db
(def ^{:dynamic true :private true} *companyid* nil)

(defmacro wrap-companyid [cid & body]
  `(binding [*companyid* ~cid]
     ~@body))

(defn get-companyid []
  (str->num *companyid*))


(comment
  (register-db-hook! "query" {:pre-hook (partial print "pre-query")})
  (register-db-hook! "query" {:post-hook (partial print "post-query")})
  (wrap-db-hooks "query"))

(defn aid->first-role
  [aid]
  (first (j/query (mysql-db) ["select * from csb_role where account_id =? order by create_time asc limit 1" aid])))

(defn -aid->first-haier-role
  [aid]
  (first (j/query (mysql-db) ["select haier,r.id role_id, r.nickname, a.id account_id from csb_account a, csb_role r where a.id =? and a.id=r.account_id order by r.create_time asc limit 1" aid])))

(def aid->first-haier-role (memo/ttl -aid->first-haier-role :ttl/threshold 60000))

(defn aid->p-account [{:keys [account_id] :as m}]
  (merge m
         (first (j/query (mysql-db) ["select signature from csb_account where id =?" account_id]))))


(defn- make-commands-from-sql-script
  "Create a seq of commands which can be consumed by `jdbc/db-do-commands`.  

  Input is normally a DDL file. When `ignore-db-change?` is set to
  true, change db will be quietly ignored."
  [sql-patch & [ignore-db-change?]]
  (filter identity
          (map
           (fn [sql]
             (let [sql (s/lower-case (s/trim sql))]
               (cond
                 (s/starts-with? sql "use") (when-not ignore-db-change? (throw (RuntimeException. "Change database not allowed!")))
                 (not (s/blank? sql)) sql)))
           (s/split sql-patch #";"))))

(defn execute-sql-script [{:keys [db script-file ignore-db-change? pre-command]
                           :or {ignore-db-change? false}}]
  (let [sql-script (slurp script-file)
        commands (make-commands-from-sql-script sql-script ignore-db-change?)]
    (j/db-do-commands db (if pre-command (cons pre-command commands) commands))))


(defn start-sql-patcher [period]
  (go-loop []
    (<! (timeout period))
    (let [f (io/file "patch.sql")]
      (when (.exists f)
        (try
          (execute-sql-script {:db (mysql-db)
                               :script-file f
                               :ignore-db-change? true})
          (.renameTo f (io/file "patch.success.sql"))
          (catch Throwable e
            (t/error "Apply sql patch failed" (.getMessage e))
            (t/error e)
            (.renameTo f (io/file "patch.fail.sql"))))))
    (recur)))

(defn wildcard [term]
  (str "%" term "%"))


(def follower-stats
  (memo/ttl
   (fn [aid]
     (let [follower_cnt (:cnt
                          (first
                           (j/query (mysql-db) ["select count(id) cnt from csb_relation where account_id =?" aid])))
           following_cnt (:cnt
                          (first
                           (j/query (mysql-db) ["select count(id) cnt from csb_relation where follower_id =?" aid])))]
       {:follower_cnt follower_cnt :following_cnt following_cnt}))
   :ttl/threshold 30000))

(def aid->info
  (memo/ttl
   (fn [aid]
     {:pre [aid]}
     (-> aid
         aid->first-role
         aid->p-account
         (select-keys [:account_id :nickname :sex :icon_image_path :signature])
         (merge (follower-stats aid))
         (assoc :afav_cnt (or ((wrap-exception mok-call) :count-fav {:aid aid}) 0))))
   :ttl/threshold 30000))

(defn get-public-account [aids]
  (map aid->info aids))

(defn- reload-active-users [cid]
  (let [cnt 100
        users (get-public-account
               (map
                :id
                (j/query
                 (mysql-db)
                 ["select distinct a.id from csb_account a, csb_mdata m where company_id=? and a.id=m.account_id order by m.id desc limit ?" cid 100])))]
    (cache-most-active-users cid users)))

(defn refresh-most-active-users []
  (run!
   reload-active-users
   (props :refresh-active-users-for {:default nil})))

(defn last-log
  ([aid]
   (last-log (mysql-db) aid))
  ([db aid]
   (first (j/query db ["select * from csb_last_log where aid=? limit 1" aid]))))

(defn upsert-last-log [{:keys [aid] :as row}]
  (j/with-db-transaction [db (mysql-db)]
    (if (last-log db aid)
      (j/update! db :csb_last_log (dissoc row :aid) ["aid=?" aid])
      (j/insert! db :csb_last_log row))))

(defn haider->status [haier]
  (:status (->entity (mysql-db) :csb_account {:haier haier})))

(defn allow-post-mblog? [aid]
  (let [status (:status (->entity (mysql-db) :csb_account {:id aid}))]
    (= 1 status)))


(defmulti topup-score :source)

(defn- add-jifen
  ([aid source score]
   (add-jifen aid source score nil))
  ([aid source score note]
   {:pre [aid source score]}
   (t/info "add jifen" aid source score note)
   (try
     (j/insert! (mysql-db) :csb_jifen {:score score :source source :date (today-as-string) :ts (now-as-long) :account_id aid :note note})
     (catch java.sql.SQLException e
       (when-not (= 1062 (.getErrorCode e))
         (throw e))))))


;; (defmethod topup-score :weight
;;   [{:keys [aid]}]
;;   (add-jifen aid 1 1))

;; (defmethod topup-score :mblog
;;   [{:keys [aid]}]
;;   (add-jifen aid 2 1))

;; (defmethod topup-score :weight-7-days
;;   [{:keys [aid]}]
;;   (add-jifen aid 3 7))

;; (defmethod topup-score :weight-21-days
;;   [{:keys [aid]}]
;;   (add-jifen aid 4 20))

;; (defmethod topup-score :weight-100-days
;;   [{:keys [aid]}]
;;   (add-jifen aid 5 50))

;; (defmethod topup-score :mblog-pop
;;   [{:keys [aid]}]
;;   (add-jifen aid 6 10))

;; (defmethod topup-score :mblog-activity
;;   [{:keys [aid]}]
;;   (add-jifen aid 7 1))

;; (defmethod topup-score :invite-regist
;;   [{:keys [aid]}]
;;   (add-jifen aid 8 5))

;; (defn topup-score-by-mdata [aid]
;;   (let [wms (j/query (mysql-db) ["select distinct DATE_FORMAT(weight_time, '%Y-%m-%d') date from csb_weight where account_id=? order by weight_time desc limit 100" aid] {:row-fn :date})
;;         seven-day-before (n-days-before-as-string 7)
;;         twentyone-day-before (n-days-before-as-string 21)
;;         hundred-day-before (n-days-before-as-string 100)]
;;     (when (= (first wms) (today-as-string))
;;       (topup-score {:source :weight :aid aid})
;;       (when (= (nth wms 6 nil) seven-day-before)
;;         (topup-score {:source :weight-7-days :aid aid}))
;;       (when (= (nth wms 20 nil) twentyone-day-before)
;;         (topup-score {:source :weight-21-days :aid aid}))
;;       (when (= (last wms) hundred-day-before)
;;         (topup-score {:source :weight-100-days :aid aid})))))


(defmethod topup-score :first-login
  [{:keys [aid]}]
  (when-not (->entity (mysql-db) :csb_jifen {:account_id aid :source 101})
    (add-jifen aid 101 20)))

(defmethod topup-score :first-health-scale
  [{:keys [aid]}]
  (when-not (->entity (mysql-db) :csb_jifen {:account_id aid :source 102})
    (add-jifen aid 102 20)))

(defmethod topup-score :first-kitchen-scale
  [{:keys [aid]}]
  (when-not (->entity (mysql-db) :csb_jifen {:account_id aid :source 103})
    (add-jifen aid 103 20)))

(defn week-of-year-from-millis [ms]
  (long->date-string ms {:pattern "Yw" :offset "+8"}))

(defn ymd-from-millis [ms]
  (long->date-string ms {:pattern "YMMdd" :offset "+8"}))

(defn- topup-score-in-one-week [source score {:keys [aid]}]
  (let [qvec ["select * from csb_jifen where account_id=? and source=? order by id desc limit 1" aid source]
        {:keys [ts]} (first (j/query (mysql-db) qvec))]
    (when (not= (week-of-year-from-millis (System/currentTimeMillis))
                (week-of-year-from-millis (or ts 0)))
      (add-jifen aid source score))))


(defn- topup-score-in-one-day [source score {:keys [aid]}]
  (let [qvec ["select * from csb_jifen where account_id=? and source=? order by id desc limit 1" aid source]
        {:keys [ts]} (first (j/query (mysql-db) qvec))]
    (when (not= (ymd-from-millis (System/currentTimeMillis))
                (ymd-from-millis (or ts 0)))
      (add-jifen aid source score))))

(defmethod topup-score :mblog-no-image
  [params]
  (topup-score-in-one-week 105 20 params))

(defmethod topup-score :mblog-with-image
  [params]
  (topup-score-in-one-week 106 30 params))

(defmethod topup-score :reply-any
  [params]
  (topup-score-in-one-week 107 10 params))

(defmethod topup-score :video-upload
  [params]
  (topup-score-in-one-week 108 30 params))

(defmethod topup-score :purchase-confirm
  [{:keys [aid oid]}]
  (when-not (->entity (mysql-db) :csb_jifen {:account_id aid :note (str oid) :source 109})
    (add-jifen aid 109 50)))

(defmethod topup-score :consume-by-order
  [{:keys [aid oid score]}]
  {:pre [(neg? score)]}
  (let [note (str oid)]
    (when-not (->entity (mysql-db) :csb_jifen {:account_id aid :note note :source 111})
      (add-jifen aid 111 score note))))

(defmethod topup-score :checkin
  [params]
  (topup-score-in-one-day 104 5 params))
