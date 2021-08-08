(ns dev-chips.jdbc-time
  (:require
   [sparrows.time :as time]
   [taoensso.timbre :as t]
   [clojure.java.jdbc :as j]
   [chips.utils :refer [convert-sql-val to-sql-date]]
   [chips.models.base :refer :all])
  (:import
   [java.sql Date Timestamp]
   [java.util TimeZone]))


(defn set-timezone []
  (TimeZone/setDefault (TimeZone/getTimeZone "Asia/Shanghai")))

(defn get-timezone []
  (TimeZone/getDefault))

;; Try older version of mysql-connector-java, e.g., 5.1.39 or 5.1.38
;; https://mvnrepository.com/artifact/mysql/mysql-connector-java

;; create database t_test ;
;; create table t_play (id int primary key auto_increment, t datetime not null, d date not null);

(defn test-time-sql []
  (t/info (get-timezone))
  (let [now (System/currentTimeMillis)
        id (get-generated-id (j/insert! (mysql-db) :t_play {:t (Timestamp. now) :d (Date. now)}))]
    (t/info now)
    (t/info (first (j/query (mysql-db) ["SELECT @@global.time_zone, @@session.time_zone"])))
    (t/info (first (j/query (mysql-db) ["SELECT @@system_time_zone"])))
    (t/info (convert-sql-val (->entity (mysql-db) :t_play {:id id})))
    (t/info (.getTime (:t (->entity (mysql-db) :t_play {:id id}))))
    (t/info (convert-sql-val (first (j/query (mysql-db) ["select * from t_play where t=? order by id desc limit 1" (Timestamp. now)]))))
    (t/info (convert-sql-val (->entity (mysql-db) :t_play {:t (Timestamp. now)})))
    (t/info (mapv convert-sql-val (j/query (mysql-db) ["select * from t_play where t<=?" (Timestamp. (+ now 1000))])))))


;; local
;; 16-08-09 14:57:56 tp INFO [dev-chips.jdbc-time:19] - 1470754676690
;; 16-08-09 14:57:56 tp INFO [dev-chips.jdbc-time:20] - {:@@global.time_zone "SYSTEM", :@@session.time_zone "SYSTEM"}
;; 16-08-09 14:57:56 tp INFO [dev-chips.jdbc-time:21] - {:@@system_time_zone "HKT"}
;; 16-08-09 14:57:56 tp INFO [dev-chips.jdbc-time:22] - {:id 6, :t "2016-08-09 22:57:56", :d "2016-08-09"}
;; 16-08-09 14:57:56 tp INFO [dev-chips.jdbc-time:23] - {:id 6, :t "2016-08-09 22:57:56", :d "2016-08-09"}

;; on ucmain
;; 16-08-09 14:57:21 localhost INFO [dev-chips.jdbc-time:19] - 1470754641280
;; 16-08-09 14:57:21 localhost INFO [dev-chips.jdbc-time:20] - {:@@global.time_zone "SYSTEM", :@@session.time_zone "SYSTEM"}
;; 16-08-09 14:57:21 localhost INFO [dev-chips.jdbc-time:21] - {:@@system_time_zone "CST"}
;; 16-08-09 14:57:21 localhost INFO [dev-chips.jdbc-time:22] - {:id 6, :t "2016-08-09 22:57:21", :d "2016-08-09"}
;; 16-08-09 14:57:21 localhost INFO [dev-chips.jdbc-time:23] - nil


;; TODO test date query/insertion

(defn test-sql-date-comparison []
  (let [ts0 (time/date-string->long "2016-10-20")
        ts1 (System/currentTimeMillis)
        d0 (to-sql-date ts0)
        ;;t0 (to-sql-time ts0)
        d1 (to-sql-date ts1)
        ;;t1 (to-sql-time ts1)
        ]
    ;; (j/insert! (mysql-db) :t_play {:t d0 :d d0})
    ;; (j/insert! (mysql-db) :t_play {:t d1 :d d1})
    (j/query (mysql-db) ["select * from t_play where d=?" (to-sql-date (time/date-string->long "2016-10-20"))])
    ))

(comment
  (test-sql-date-comparison)
  )

