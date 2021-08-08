(ns chips.models.fe
  (:require
   [cheshire.core :refer [generate-string]]
   [chips.config         :refer [props]]
   [chips.models.base    :refer :all]
   [chips.store.rds      :as rds]
   [chips.utils          :as vu :refer [convert-sql-val write-icon-file to-sql-date]]
   [chips.utility        :refer [internal-exception-hanlder]]
   [clojure.core.memoize :as memo]
   [clojure.java.jdbc    :as j]
   [clojure.string       :as s]
   [sparrows.misc        :refer [dissoc-nil-val wrap-exception str->num]]
   [taoensso.timbre      :as t]))

(defn get-food [{:keys [id]}]
  {:pre [id]}
  (->entity (fe-db) :food {:id id}))

(defn wildcard-search [{:keys [table term cnt lastid]}]
  (when term
    (let [cnt (str->num cnt)
          lastid (str->num lastid)
          qstr (str "select * from " table " where 1=1 "
                    (when lastid " and id>? ")
                    " and `name` like ? limit ?")
          qvec (filter identity [qstr lastid (wildcard term) (cond 
                                                               (nil? cnt) 10
                                                               (< 0 cnt 101) cnt
                                                               :default 10)])]
      (j/query (fe-db) qvec))))

(defn food-search
  [params]
  (wildcard-search (assoc params :table "food")))

(defn exercise-search [params]
  (wildcard-search (assoc params :table "exercise")))

(defn popular-food []
  (j/query (fe-db) ["select * from food where usedegree>?" 0]))

(defn popular-exercise []
  (j/query (fe-db) ["select * from exercise where usedegree>?" 0]))
