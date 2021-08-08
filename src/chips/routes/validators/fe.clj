(ns chips.routes.validators.fe
  (:require
   [cheshire.core :refer [generate-string parse-string]]
   [taoensso.timbre    :as t]
   [noir.validation    :as v]
   [noir.util.route    :refer [restricted]]
   [clojure.string     :as s]
   [clojure.java.io    :as io]
   [clojure.data.json  :refer [write-str]]
   [sparrows.misc      :refer [str->num lowercase-trim]]
   [sparrows.cypher    :refer [md5]]
   [sparrows.system    :refer [get-mime]]
   [chips.routes.base  :refer [return-code-with-alt]]
   [chips.utils        :as vu :refer [role-enabled? uid->type cid->signature valid-date?]]
   [chips.utility :refer [wrap-async]]
   [chips.models.base :refer [all-company-ids]]
   [chips.models.users :as mu]
   [chips.config       :refer :all]
   [chips.store.rds    :as rds :refer [store-code]]
   [chips.codes        :refer [cds]]))

(def meal-types #{"breakfast" "lunch" "dinner" "snacks"})

(defn valid-meal? [{:keys [food_id name quantity unit calory type date]}]
  (let [[food_id quantity calory] (map str->num [food_id quantity calory])]
    (and
     (v/rule
      (and food_id name quantity unit calory type)
      [:error (cds :required-param-not-exist {:alt "food_id name quantity unit calory type"})])
     (v/rule
      (meal-types type)
      [:error (cds :invalid-param {:alt "type"})])
     (v/rule
      (pos? quantity)
      [:error (cds :invalid-param {:alt "quantity"})])
     (v/rule
      (pos? calory)
      [:error (cds :invalid-param {:alt "calory"})])
     (v/rule
      (valid-date? date)
      [:error (cds :invalid-param {:alt "date"})]))))

(defn validate-put-record-meal [{:keys [account_id role_id meal] :as params}]
  {:pre [account_id]}
  (and
   (v/rule
    (and role_id (seq meal))
    [:error (cds :required-param-not-exist {:alt "role_id or meal"})])
   (every? valid-meal? meal)
   (v/rule
    (= (:account_id (mu/get-role {:id role_id}))
       account_id)
    [:error (cds :auth-failed)])))

(defn valid-exercise? [{:keys [ex_id name duration calory date]}]
  (let [[ex_id duration calory] (map str->num [ex_id duration calory])]
    (and
     (v/rule
      (and ex_id name duration calory date)
      [:error (cds :required-param-not-exist {:alt "ex_id name duration calory date"})])
     (v/rule
      (pos? duration)
      [:error (cds :invalid-param {:alt "duration"})])
     (v/rule
      (pos? calory)
      [:error (cds :invalid-param {:alt "calory"})])
     (v/rule
      (valid-date? date)
      [:error (cds :invalid-param {:alt "date"})]))))

(defn validate-put-record-exercise [{:keys [account_id role_id exercises]}]
  {:pre [account_id]}
  (and
   (v/rule
    (and role_id (seq exercises))
    [:error (cds :required-param-not-exist {:alt "role_id or exercises"})])
   (every? valid-exercise? exercises)
   (v/rule
    (= (:account_id (mu/get-role {:id role_id}))
       account_id)
    [:error (cds :auth-failed)])))
