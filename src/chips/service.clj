(ns chips.service
  "Misc services running as daemons"
  (:require
   [chips.config       :refer [props bj-timezone merge-circle-chan]]
   [chips.models.mdata :refer [weight-init-intialize]]
   [chips.models.users :as u :refer [clean-disabled-roles]]
   [chips.models.mblog :refer [merge-circle]]
   [chips.models.base :refer [refresh-most-active-users]]
   [chips.models.admin :as admin]
   [chips.models.shop :as shop]
   [chips.utils        :refer [id->pool]]
   [clojure.core.async :as a :refer [go-loop <! timeout thread]]
   [com.climate.claypoole :as cp]
   [sparrows.misc :refer [wrap-exception]]
   [taoensso.timbre    :as t])
  (:import [java.util.concurrent Executors TimeUnit]
           org.joda.time.MutableDateTime))

(defn exec-refund! []
  (thread
    (doseq [{:keys [ouid orig-payment product quantity] :as req} (shop/x-refund-request)]
      (try
        (t/info "prepare to refund" ouid (:title product) quantity)
        (if (and orig-payment product)
          (shop/refund! req)
          (t/error "Invalid state for refund, ignoring ouid:"
                   ouid "orig-payment:" orig-payment "product:" product))
        (catch Throwable e
          (t/error "Refund error for" ouid)
          (t/error e))
        (finally
          (Thread/sleep 60000))))))


(comment
  (exec-refund!)
  )

(defonce common-tasks
  [(when-let [{:keys [hour minute]} (props :refund-time {:default nil})]
     {:job (wrap-exception exec-refund!)
      :id :exec-refund!
      :period 86400000
      :hour hour
      :minute minute})
   {:job (wrap-exception weight-init-intialize)
    :id :weight-init-intialize
    :hour 0
    :minute 10
    :period 86400000}
   (when (props :refresh-active-users-for {:default nil})
     {:job (wrap-exception refresh-most-active-users)
      :id :refresh-most-active-users
      :delay 6000
      :period 120000})])

(defn calculate-delay
  [{:keys [sec minute hour period]}]
  {:pre [hour period]}
  (let [now (System/currentTimeMillis)
        mdt (.getMillis (doto (MutableDateTime. now bj-timezone)
                          (.setHourOfDay hour)
                          (.setMinuteOfHour (or minute 0))
                          (.setSecondOfMinute (or sec 0))))
        diff (- mdt now)]
    (if (pos? diff) diff (+ period diff))))

(defn start-common-jobs
  []
  (let [exec (Executors/newSingleThreadScheduledExecutor)]
    (doseq [task common-tasks
            :let [{:keys [id job delay period hour minute]} task]
            :when id]
      (let [delay (or delay (calculate-delay task))]
        (t/info "starting job" id "in" delay "[ms]")
        (.scheduleAtFixedRate exec job delay period TimeUnit/MILLISECONDS)))))


(defn start-circle-merger []
  (go-loop []
    (let [rel (<! merge-circle-chan)]
      (cp/future
        (id->pool :circle-merger)
        (try
          (merge-circle rel)
          (catch Throwable e
            (t/error "Error: merge-circle rel" rel)
            (t/error e)))))
    (recur)))


(defonce buid-inc-store (atom {}))

(defn add-pv-store [buid]
  (swap! buid-inc-store update-in [buid] #(inc (or % 0))))

(defn dec-pv-store [buid n-dec]
  (swap! buid-inc-store (fn [old-state]
                          (let [n (- (get old-state buid 0) n-dec)]
                            (if (pos? n)
                              (assoc old-state buid n)
                              (dissoc old-state buid))))))

(defn start-pv-db-adder []
  (go-loop [m-buid @buid-inc-store]
    (when (seq m-buid)
      (doseq [e-buid m-buid
              :let [[buid n-inc] e-buid]]
        (admin/add-pv buid n-inc)
        (dec-pv-store buid n-inc)))
    (<! (timeout 5000))
    (recur @buid-inc-store)))


