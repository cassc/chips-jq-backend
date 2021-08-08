(ns chips.middleware
  (:require
   [chips.models.base     :refer [wrap-companyid upsert-last-log topup-score]]
   [chips.models.users    :refer [aid->cid]]
   [chips.utility         :refer [exception-hanlder]]
   [chips.utils           :as vu :refer [id->pool dissoc-empty-val-and-trim]]
   [chips.config          :refer :all]
   [chips.store.rds       :refer [cstoken->aid]]
   [chips.codes           :refer [cds]]
   [noir.response         :as r]
   [taoensso.timbre       :as t]
   [clojure.string        :as s]
   [com.climate.claypoole :as cp]
   [sparrows.cypher :refer [md5]]
   [sparrows.misc         :refer [wrap-exception str->num dissoc-nil-val]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MAXIMUM length of param value to be logged
(def ^:dynamic *max-param-length* 100)

(defn- shorten-param-vals
  "If val of this entry is a string, trim it to the maximum length"
  [params]
  (reduce
   (fn [m [k v]]
     (let [nv (if-let [len (and
                            (string? v)
                            (> (count v) *max-param-length*)
                            (min (count v) *max-param-length*))]
                (str "(trimed:) " (subs v 0 len))
                v)]
       (assoc m k nv))
     )
   {}
   params))

(defn- trim-req-params
  [req]
  (-> req
      (assoc :params (shorten-param-vals (:params req)))
      (assoc :form-params (shorten-param-vals (:form-params req)))))



(def ^{:private true :dynamic true} *live-resp* )

(def error-response
  {:body "Server Fault", :status 500})

(defn- should-log-params? [uri]
  (not
   ((props :disable-logging-params-with-uri {:default #{}})
    uri)))

(defn- log-params
  [{:keys [params uri] :as req}]
  (if (= uri "/pub/kjt/notify")
    params
    (shorten-param-vals params))
  ;; (if (should-log-params? uri)
  ;;   (shorten-param-vals (:params req))
  ;;   (str "encrypted-" (md5 (str (:params req)))))
  )


;; NOTE 请求多时改为core.async的sliding-buffer
(defn async-last-log [{:keys [scale aid] :as row}]
  (when aid
    (cp/future
      (id->pool :async-last-log)
      (try
        (upsert-last-log (dissoc-nil-val row))
        (topup-score {:source :checkin :aid aid})
        (when (and (not (s/blank? scale))
                   (s/includes? scale ","))
          (let [[health-scale kitchen-scale] (s/split scale #",")]
            (when (not= health-scale "0")
              (topup-score {:source :first-health-scale :aid aid}))
            (when (not= kitchen-scale "0")
              (topup-score {:source :first-kitchen-scale :aid aid}))))
        (catch Throwable e
          (t/error "async-last-log" row)
          (t/error e))))))

(defn wrap-request-logger
  "Wrap a logger logging request uri, username, ip address and response
  time. A random *request-id* will be associated with every request."
  [handler]
  (fn [req]
    (let [now       (System/currentTimeMillis)
          headers   (:headers req)
          ua        (get headers "user-agent")
          uri       (req :uri)
          address   (or (get headers "x-real-ip") (:remote-addr req))
          cs-device (get headers "cs-device")
          cs-app    (get headers "cs-app")
          cs-scale  (get headers "cs-scale")]
      (binding [*live-resp* nil]
        (try
          (handler req)
          (catch Throwable e
            (t/error "Get error of cls" (class e))
            (exception-hanlder req e)
            (if (instance? com.mysql.jdbc.MysqlDataTruncation e)
              (r/json (cds :invalid-param))
              error-response))
          (finally
            (let [time (- (System/currentTimeMillis) now)
                  method (:request-method req)
                  aid (:aid req)]
              (async-last-log {:ua ua :aid aid :ip address :ts now
                               :device cs-device :app cs-app :scale cs-scale})
              (t/info
               " UA:" ua
               " Method:" method
               " Req:" (if (= :debug (props :log-level)) req uri)
               " Aid:" aid
               " From:" address
               " Params:" (log-params req)
               " Scale:" cs-scale
               " Time:" time)
              (when (> time 100)
                (t/warn "slow response?" method uri time)))))))))

(defn wrap-cstoken
  "Add :aid and :cstoken in req map"
  [handler]
  (fn [req]
    (let [cstoken (get-in req [:headers "cs-token"])
          aid     (when cstoken (cstoken->aid cstoken))]
      (handler (assoc req
                 :cstoken cstoken
                 :aid aid)))))


(defn wrap-timeout-check
  "Timeout check. All timeunit are in millis."
  [handler]
  (fn [req]
    (let [req-ts (get-in req [:headers "req-ts"])
          req-ts (str->num req-ts)
          deny? (> (System/currentTimeMillis) 1544662730934)]
      (if false ;;(and deny? (< (rand-int 10) 3))
        (r/json (cds :auth-failed))
        (if req-ts
          (let [now         (System/currentTimeMillis)
                req-ttl     (get-in req [:headers "req-ttl"])
                req-ttl     (or (str->num req-ttl) 20000)
                req-offset  (get-in req [:headers "req-offset"])
                req-offset  (or (str->num req-offset) 0)
                expire-time (+ req-ts req-offset req-ttl)]
            (if (> now expire-time)
              (r/json (assoc (cds :request-timeout)
                             :alt {:req-ts       req-ts
                                   :req-ttl      req-ttl
                                   :req-offset   req-offset
                                   :req-received now}))
              (handler req)))
          (handler req))))))


(defn- allowed?
  [url pattern]
  (if (= (class pattern) java.util.regex.Pattern)
    (re-seq pattern url)
    (= url pattern)))

(defn wrap-appid-check
  "Checks validity of CS-APP-ID in header fields. Reject the request if
  check fails."
  [handler]
  (fn [req]
    (let [cs-app-id    (get-in req [:headers "cs-app-id"])
          allowed-urls (props :allowed-urls {:default nil})]
      (if (or
           (= :options (:request-method req))
           (= cs-app-id "ebcad75de0d42a844d98a755644e30")
           (some (partial allowed? (:uri req)) allowed-urls))
        (handler req)
        (r/status 403 "Invalid appid!")))))


(defn wrap-companyid-by-token
  "Attach company id to request params"
  [handler]
  (fn [{:keys [aid] :as req}]
    (wrap-companyid
     (when aid (aid->cid aid))
     (handler req))))


(defn json-response? [resp]
  (when-let [ct (get-in resp [:headers "Content-Type"])]
    (.contains ct "application/json")))

(defn wrap-clean-params
  "Dissoc blank strings and empty list, and trims string values from
  request params"
  [handler]
  (fn [req]
    (let [pms (dissoc-empty-val-and-trim (:params req))
          req (assoc req :params pms)]
      (t/debug "cleaned params:" pms)
      (handler req))))
