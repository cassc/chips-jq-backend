(ns chips.routes.validators.mdata
  (:require
   [chips.routes.base  :refer [return-code-with-alt]]
   [chips.models.users :as mu :refer [get-role]]
   [chips.config       :refer :all]
   [chips.store.rds    :as rds :refer [store-code]]
   [chips.codes        :refer [cds]]
   [chips.utils :refer [unhexify]]
   
   [taoensso.timbre          :as t]
   [noir.validation          :as v]
   [noir.util.route          :refer [restricted]]
   [clojure.string           :as s]
   [clojure.java.io          :as io]
   [sparrows.cypher          :refer [md5 base64-decode]]
   [sparrows.system          :refer [get-mime]]
   [sparrows.misc          :refer [str->num wrap-exception]]))


(defn validate-upload-mdata
  [{:keys [mdata account_id]}]
  {:pre [account_id]}
  (and
   (v/rule
    (every? #(or (nil? (:mtype %)) (valid-mtypes (keyword (:mtype %)))) mdata)
    [:error (cds :invalid-mtype)])
   (v/rule
    (try (seq mdata) (catch Exception e))
    [:error (cds :invalid-param)])

   (let [roleids (mapv :role_id mdata)]
     (and
      (v/rule
       (= (count mdata) (count roleids))
       [:error (cds :required-param-not-exist)])
      (v/rule
       (let [rids (distinct roleids)]
         (every? (fn [rid] (get-role {:id rid :account_id account_id})) rids))
       [:error (cds :auth-failed)])))))


(defn validate-weight-parse [{:keys [account_id role_id sex age height resistance iseightr]}]
  {:pre [account_id]}
  (and
   (v/rule
    (and role_id sex age height)
    [:error (cds :required-param-not-exist)])
   (v/rule
    (and role_id (get-role {:id role_id :account_id account_id}))
    [:error (cds :auth-failed)])
   (v/rule
    (or (s/blank? resistance) ((wrap-exception unhexify) resistance))
    [:error (cds :invalid-param {:resistance resistance})])))

(defn validate-wifi-weight-parse [{:keys [aid rid weight ts age sex height r1 rn8]}]
  {:pre [aid]}
  (and
   (v/rule
    (and rid weight ts age sex height r1)
    [:error (cds :required-param-not-exist)])
   (v/rule
    (every? pos? [r1 weight age height])
    [:error (cds :invalid-param)])
   (v/rule
    (#{0 1} sex)
    [:error (cds :invalid-param {:sex sex})])
   (v/rule
    (or (s/blank? rn8) (s/starts-with? rn8 "1:"))
    [:error (cds :invalid-param {:rn8 rn8})])))

(defn validate-delete-mdata
  [{:keys [account_id mids mtype]}]
  {:pre [account_id]}
  (and
   (v/rule
    (valid-mtypes mtype)
    [:error (cds :invalid-mtype)])
   (v/rule
    (try (seq mids) (catch Exception e))
    [:error (cds :invalid-param)])))

(defn validate-get-mdata
  [{:keys [account_id role_id cnt end start mtype cnt-by-days]}]
  {:pre [account_id end]}
  (and
   (v/rule
    (or start cnt cnt-by-days)
    [:error (cds :required-param-not-exist {:alt "start/cnt/cnt_by_days"})])

   (v/rule
    (if (seq? mtype)
      (every? (conj valid-mtypes :all) mtype)
      ((conj valid-mtypes :all) mtype))
    [:error (cds :invalid-mtype)])

   (v/rule
    (if role_id
      (and role_id
           (get-role {:id role_id :account_id account_id}))
      't)
    [:error (cds :auth-failed)])

   (v/rule
    (= (count (filter identity [cnt-by-days cnt start])) 1)
    [:error (assoc (cds :params-conflict) :msg "cnt/start/cnt_by_days不能同时使用！")])

   (v/rule
    (if start
      (< 0 start end (Long/MAX_VALUE))
      (< 0 (or cnt cnt-by-days) end (Long/MAX_VALUE)))
    [:error (cds :invalid-param)])))


(defn validate-mdata-sync
  [{:keys [lastsync role_id account_id start end mtype]}]
  {:pre [account_id mtype]}
  (and
   (v/rule
    (and start end lastsync)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (if (seq? mtype)
      (every? (conj valid-mtypes :all) mtype)
      ((conj valid-mtypes :all) mtype))
    [:error (cds :invalid-mtype)])
   
   (v/rule
    (if role_id
      (get-role {:id role_id :account_id account_id :current_state 1})
      't)
    [:error (cds :role-not-exist)])))

(defn validate-mdata-sync-download
  [{:keys [mids role_id account_id mtype]}]
  (and
   (v/rule
    mids
    [:error (cds :required-param-not-exist)])
   (v/rule
    (valid-mtypes mtype)
    [:error (cds :invalid-mtype)])))


(defn validate-mdata-stats
  [{:keys [offset start end period role_id account_id]}]
  {:pre [account_id]}
  (and
   (v/rule
    (and offset start end period role_id)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (#{:week :day} period)
    [:error (assoc (cds :invalid-param) :msg "period区间格式不正确")])

   (v/rule
    (< -13 offset 13)
    [:error (assoc (cds :invalid-param) :msg "时区无效")])

   (v/rule
    (if role_id
      (get-role {:id role_id :account_id account_id :current_state 1})
      't)
    [:error (cds :role-not-exist)])))

(defn validate-mdata-stats-v2
  [{:keys [start end mtype ptype role_id account_id]}]
  {:pre [account_id]}
  (and
   (v/rule
    (and start end role_id mtype ptype)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (if (seq? mtype)
      (every? (conj valid-mtypes :all) mtype)
      ((conj valid-mtypes :all) mtype))
    [:error (cds :invalid-mtype)])

   (v/rule
    (#{1 2 3} ptype)
    [:error (cds :invalid-ptype)])
   
   (v/rule
    (get-role {:id role_id :account_id account_id :current_state 1})
    [:error (cds :role-not-exist)])))

(defn validate-get-wifi-weight [{:keys [aid rid lastts cnt]}]
  {:pre [aid]}
  (and
   (v/rule
    (or (not lastts) (and (integer? lastts) (pos? lastts)))
    [:error (cds :invalid-param {:lastts lastts})])

   (v/rule
    (or (not cnt) (and (integer? cnt) (pos? cnt)))
    [:error (cds :invalid-param {:cnt cnt})])))
