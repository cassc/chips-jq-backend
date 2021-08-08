(ns chips.models.base-test
  (:require
   [clojure.string :as s]
   [chips.utils :refer [convert-sql-val uuid]]
   [clojure.java.jdbc :as j]
   [criterium.core :as cc]
   [chips.models.base :refer :all]
   [clojure.test :refer :all]))


(defrecord WeighData
    [account_id bone muscle sync_time weight id bmi body_age role_id weight_time water metabolism viscera axunge])


(defn query-with-map
  []
  (j/query (mysql-db) ["select * from csb_role_data"]))

(defn query-with-conversion
  []
  (j/query (mysql-db) ["select * from csb_role_data"] :row-fn convert-sql-val))

(defn query-with-map-conversion
  []
  (let [rs (j/query (mysql-db) ["select * from csb_role_data"])]
    (map convert-sql-val rs)))

(defn query-with-record
  []
  (j/query (mysql-db) ["select * from csb_role_data"] :row-fn map->WeighData))

(defn query-with-map-record
  []
  (let [rs (j/query (mysql-db) ["select * from csb_role_data"])]
    (map map->WeighData rs)))


;; takes quite a bit of time to test
;; (deftest benchmark-test
;;   (testing "Testing benchmarks ..."
;;     (prn (str "Testing db query with " (count (query-with-map)) " rows"))
;;     (cc/with-progress-reporting (cc/bench (last (query-with-map))))
;;     (cc/with-progress-reporting (cc/bench (last (query-with-conversion))))
;;     (cc/with-progress-reporting (cc/bench (last (query-with-map-conversion))))
;;     (cc/with-progress-reporting (cc/bench (last (query-with-record))))
;;     (cc/with-progress-reporting (cc/bench (last (query-with-map-record))))))


(comment
  ;; nested with-db-transaction works
  (letfn [(insert-success [db]
            (j/with-db-transaction [db db]
              (j/insert! db :t_test {:id (uuid) :title "success"})
              (j/insert! db :t_test {:id (uuid) :title "success"})))

          (insert-fail [db]
            (j/insert! db :t_test {:id (uuid) :title "fail"})
            (throw (RuntimeException. "failing")))

          ;; db2 will commit
          (test-nested-with-db-transaction []
            (j/with-db-transaction [db1 (mysql-db)]
              (j/with-db-transaction [db2 (admin-db)]
                (insert-success db1)
                (insert-success db2))
              (throw (RuntimeException. "fail outside"))))]
    (test-nested-with-db-transaction))

  ;; DDL: DON'T TRY THIS in production
  (letfn [(make-commands [sql-patch]
            (filter identity
                    (map
                     (fn [sql]
                       (let [sql (s/lower-case (s/trim sql))]
                         (when-not (or (s/blank? sql) (s/starts-with? sql "use"))
                           (prn sql)
                           sql)))
                     (s/split sql-patch #";"))))]
    (let [sql-drop-mdata 
          sql-patch (slurp "db/043-okok-mdata-table.sql")]
      (apply j/db-do-commands (mysql-db) (cons sql-drop-mdata (make-commands sql-patch)))))

  
  )
