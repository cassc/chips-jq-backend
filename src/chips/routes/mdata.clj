(ns chips.routes.mdata
  "Android client config: `btWeigh/src/com/chipsea/btcontrol/model/ServerConfigInfo.java`"
  (:require
   [chips.open.haier :refer [haiji-push]]
   [chips.mqtt :as mqtt]
   [chips.config :refer [props]]
   [chips.codes                   :refer [cds]]
   [chips.models.mdata            :refer :all]
   [chips.models.base :refer [aid->first-haier-role]]
   [chips.models.fe            :refer :all]
   [chips.utils                   :as cu :refer [uid->type super-of? maybe-vals->int age-from-birthday]]
   [chips.utility :as utility]
   [chips.routes.validators.mdata :refer :all]
   
   [clojure.string                :as s]
   [sparrows.misc                 :as sm :refer [str->num dissoc-nil-val]]
   [sparrows.time                 :refer [now-in-secs]]
   [sparrows.system               :as ss]
   [noir.validation               :as v]
   [chips.store.rds               :as rds]
   [taoensso.timbre               :as t]
   [noir.response                 :as r]
   [chips.routes.base             :refer :all]
   [compojure.core                :refer [defroutes GET POST PUT DELETE]]
   [noir.util.route               :refer [def-restricted-routes]]
   [com.climate.claypoole         :as cp])
  (:import
   [java.sql SQLDataException]))

(defn- handle-delete-mdata-by-ids
  [{:keys [account_id midstr mtype] :as m}]
  (let [mids (s/split midstr #"\-")
        params (assoc m :mids mids)]
    (validate-delete-mdata params)
    (if-let [err (first (v/get-errors))]
      err
      (do
        (when (= :weight mtype)
          (mqtt/async-push (assoc params :event :delete)))
        (move-to-del-mdata params)
        (success)))))

(def ^{:doc "convert mtype or a list of mtype to keywords"}
  transform-mtypes
  (letfn [(helper [mt]
            (cond
              (s/blank? mt) :weight
              (s/includes? mt ",") (seq (set (map helper (s/split mt #","))))
              :else (keyword mt)))]
    (memoize helper)))

(defn- v1-trendstats [{{:keys [offset start end period role_id ptype] :as params} :params aid :aid}]
  (let [params (assoc params :account_id aid :period (keyword period) :offset (str->num offset))]
    (validate-mdata-stats params)
    (if-let [err (first (v/get-errors))]
      err
      (success (get-mdata-stats-v1 params)))))

(defn- v2-trendstats [{{:keys [offset start end period role_id mtype ptype] :as params} :params aid :aid}]
  (let [params (assoc params
                      :account_id aid :mtype (transform-mtypes mtype) :ptype (str->num ptype)
                      :start (str->num start) :end (str->num end) :role_id (str->num role_id))]
    (validate-mdata-stats-v2 params)
    (if-let [err (first (v/get-errors))]
      err
      (success (get-mdata-stats-v2 params)))))

(defn- handle-post-mdata [{{:keys [lastsync role_id start end action mids mtype] :as params} :params aid :aid headers :headers}]
  (r/json
   (case action
     "sync"
     (let [next-sync-ts (dec (now-in-secs))
           params (maybe-vals->int params)
           params {:account_id aid
                   :role_id    (str->num role_id)
                   :lastsync   (str->num lastsync)
                   :start      (str->num start)
                   :mtype      (transform-mtypes mtype)
                   :end        (str->num end)}]
       (validate-mdata-sync params)
       (if-let [err (first (v/get-errors))]
         err
         (success (assoc (sync-mdata-v2 params)
                         :lastsync next-sync-ts))))
     "download"
     (let [params (maybe-vals->int params)
           params {:account_id aid
                   :role_id    (str->num role_id)
                   :mids       (seq (map str->num mids))
                   :mtype      (transform-mtypes mtype)}]
       (validate-mdata-sync-download params)
       (if-let [err (first (v/get-errors))]
         err
         (success (mids->mdata params))))
     (cds :unknown-action))))

(defn- handle-get-mdata [{{:keys [role_id cnt start end mtype cnt_by_days] :as params} :params aid :aid}]
  (r/json
   (let [next-sync-ts (dec (now-in-secs))
         params {:account_id  aid
                 :mtype       (transform-mtypes mtype)
                 :role_id     (str->num role_id)
                 :cnt         (str->num cnt)
                 :cnt-by-days (str->num cnt_by_days)
                 :start       (str->num start)
                 :end         (or (str->num end) (System/currentTimeMillis))}]
     (validate-get-mdata params)
     (if-let [err (first (v/get-errors))]
       err
       (if-let [rows (seq (get-mdata params))]
         (success (dissoc-nil-val
                   {:mdata    rows
                    :role_id  role_id
                    :lastsync next-sync-ts
                    :mtype    mtype}))
         (success))))))

(defn- async-internal-push [model aid mdata r-mdata]
  (let [pool (cu/id->pool :internal-push-pool)]
    (cp/future
      pool
      (try
        (let [{:keys [haier role_id]} (aid->first-haier-role aid)
              p-mdata (->>  r-mdata
                            (map (fn [a b] (merge a b)) mdata)
                            (filter (fn [m] (and
                                             (= (:role_id m) role_id)
                                             (= :weight (:mtype m :weight))))))]
          (when (seq p-mdata)
            (when (props :mdata-push-enabled {:default nil})
              (mqtt/async-push {:account_id aid :mdata p-mdata :haier haier :event :add}))
            (if (props [:haiji model] {:default nil})
              (do
                (t/info "haiji-push aid" aid "model:" model)
                (doseq [row p-mdata]
                  (haiji-push model {:deviceId (or (:mac row) (:productid row) "0")
                                     :devType (props [:haiji model :typeid])
                                     :devTypeUPlus (props [:haiji model :typeid])
                                     :dataType "attr"
                                     :args {:attrs [{:name "weight" :value (:weight row 0)}
                                                    {:name "bodyFat" :value (:axunge row 0)}
                                                    {:name "water" :value (:water row 0)}
                                                    {:name "bmi" :value (:bmi row 0)}]}})))
              (t/warn "haiji-push ignore aid" aid "cs-scale:" model))))
        (catch Throwable e
          (t/error "async-internal-push error")
          (t/error e)))))) 

(defn- handle-put-mdata [{{:keys [mdata] :as params} :params aid :aid headers :headers}]
  (let [mdata   (map #(-> %
                          (assoc :account_id aid)
                          (dissoc :id))
                     mdata)
        params  {:account_id aid :mdata mdata}]
    (validate-upload-mdata params)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (let [r-mdata (add-mdata params)
             model (->
                    (get headers "cs-scale" "")
                    (s/split #",")
                    first)]
         (async-internal-push model aid mdata r-mdata)
         (success r-mdata))))))

(defn handle-get-food [{:keys [params]}]
  (r/json
   (success (get-food params))))

(defn handle-food-search [{:keys [params]}]
  (r/json
   (success (food-search params))))

(defn handle-exercise-search [{:keys [params]}]
  (r/json
   (success (exercise-search params))))

(defn handle-get-popular-food [req]
  (r/json
   (success (popular-food))))

(defn handle-get-popular-exercise [req]
  (r/json
   (success (popular-exercise))))

(defn handle-post-weight-parse [{:keys [params aid] :as req}]
  (let [m-weight (-> params
                     (assoc :account_id aid)
                     (dissoc :id))]
    (validate-weight-parse m-weight)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (success (attach-for-weight-resitance m-weight))))))

(defn handle-post-wifi-weight-parse [{:keys [params aid] :as req}]
  (let [x-key [:rid :weight :ts :age :sex :height :r1 :rn8]
        save? (= "y" (:save params))
        birthday (:birthday params)
        age (or (:age params)
                (age-from-birthday birthday))
        m-weight (-> params
                     (select-keys x-key)
                     (assoc :aid aid :age age))]
    (validate-wifi-weight-parse m-weight)
    (let [prev-weight (he-prev-weight {:aid aid :rid (:rid params) :before (:ts params)})
          readable (utility/parse-wifi-weight m-weight prev-weight)
          row (assoc m-weight :readable (assoc readable :birthday birthday))]
      (when save?
        (insert-haier-weight row))
      (r/json
       (if-let [err (first (v/get-errors))]
         err
         (success row))))))


(defn handle-get-wifi-weight [{:keys [aid params]}]
  (r/json
   (let [{:keys [rid lastts cnt]} params
         [lastts cnt] (map str->num [lastts cnt])
         params {:aid aid
                 :rid rid
                 :lastts lastts
                 :cnt cnt}]
     (validate-get-wifi-weight params)
     (if-let [err (first (v/get-errors))]
       err
       (success (get-haier-mdata params))))))

(def-restricted-routes mdata-routes
  (PUT "/mdata" req handle-put-mdata)

  (DELETE ["/mdata/:midstr" :midstr #"([0-9]+\-)*[0-9]+$"] {{:keys [midstr] :as params} :params aid :aid}
          (r/json
           (handle-delete-mdata-by-ids {:account_id aid :midstr midstr :mtype :weight})))

  (DELETE ["/mdata/:mtype/:midstr" :mtype #"\w+" :midstr #"(\d+\-)*\d+"] {:keys [params aid] :as req} 
          (t/info "delete /mdata" req)
          (r/json
           (handle-delete-mdata-by-ids (assoc params :account_id aid :mtype (keyword (:mtype params))))))

  (GET "/mdata" req (handle-get-mdata req))

  (POST "/mdata" req (handle-post-mdata req))


  (GET "/mdata/stats" req
       (r/json
        (v2-trendstats req)))

  ;; V10 bluetooth for IOS
  (POST "/weight/parse" req handle-post-weight-parse)
  ;; Q81 WIFI
  (POST "/wifi/weight/parse" req handle-post-wifi-weight-parse)
  (GET "/wifi/weight" req handle-get-wifi-weight)
  
  ;; fe
  (GET    "/search/food" req (handle-food-search req))
  (GET    "/food" req (handle-get-food req))
  (GET    "/search/exercise" req (handle-exercise-search req))
  (GET    "/popular/food" req (handle-get-popular-food req))
  (GET    "/popular/exercise" req (handle-get-popular-exercise req)))
