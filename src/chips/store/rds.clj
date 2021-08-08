(ns chips.store.rds
  (:require
   [taoensso.timbre    :as t]
   [taoensso.carmine   :as car :refer (wcar)]
   [clojure.string     :as s]
   [sparrows.misc      :refer [str->num]]
   [sparrows.cypher    :refer [md5 rand-str]]
   [chips.utils  :refer :all]
   [chips.config :refer [props]]))


;; token -> account_id
(defonce token-prefix
  "token:id:")

;; account_id -> set of tokens
(defonce aid-prefix
  "id:tokens:")

;; valid period in secs
(def token-valid-time (* (props :token-valid-hours) 3600))

;; Redis
(def rd-server
  (props :redis))

(defmacro wcar* [& body]
  `(car/wcar rd-server ~@body))

(defn cstoken->aid
  "Convert cs_token to account_id"
  [token]
  (when token
    (str->num (wcar* (car/get (str token-prefix token))))))


(defn compare-token-for-ttl
  "Sort token by ttl descending"
  [t2 t1]
  (- (wcar* (car/ttl (str token-prefix t1))) (wcar* (car/ttl (str token-prefix t2)))))

(defn expunge-cstoken
  "Clears cstoken by aid using the provided token comparator. When
  using the default comparator, old token will be cleared first."
  ([aid]
   (expunge-cstoken compare-token-for-ttl aid))
  ([token-comparator aid]
   (let [aid-key (str aid-prefix aid)
         tokens  (wcar* (car/smembers aid-key))
         max-cnt (props :max-token-count-per-user)]
     (when-let [tk-list (seq (drop max-cnt (sort token-comparator tokens)))]
       (t/info "clearing tokens" tk-list)
       (wcar*
        (car/multi)       
        (doseq [tk tk-list]
          (car/srem aid-key tk)
          (car/del (str token-prefix tk)))
        (car/exec))))))

(defn create-cstoken
  "Create a new cstoken and add to
 - aid->cstokens set
 - cstoken->aid mapping.

  Returns a cstoken map"
  [aid]
  {:pre [aid]}
  (let [token          (str (md5 (rand-bytes 32)) (uuid))
        token-key      (str token-prefix token)
        aid-key        (str aid-prefix aid)
        now            (System/currentTimeMillis)
        expiry-time    (+ now (* token-valid-time 1000))]
    (wcar*
     (car/multi)
     ;; aid->token set
     (car/sadd aid-key token)
     ;;(car/expire aid-key token-valid-time)
     ;; token->aid
     (car/set token-key aid)
     (car/expire token-key token-valid-time)
     (car/exec))
    (expunge-cstoken aid)
    {"cs-token" token "cs-token-expirytime" (str expiry-time) "now" (str now)}))

(defn destroy-cstoken
  "Removes a cstoken from redis"
  [cstoken]
  (when cstoken
    (wcar*
     (car/multi)
     (let [aid (cstoken->aid cstoken)]
       (car/del (str token-prefix cstoken))
       (when aid
         (car/srem (str aid-prefix aid) cstoken)))
     (car/exec))))


(def ^:private code-key-prefix (str (props [:instance-id]) "-sd19sl-"))

(defn store-code
  "Store a `key`-`val` pair. If `val` is nil, a random string
  will be generated. If `ttl`(secs) is nil, the code will not expire.

  Returns the val stored."
  [{:keys [key val ttl]}]
  {:pre [key]}
  (let [key (str code-key-prefix key)
        val (or val (rand-str 8))]
    (wcar*
     (car/multi)
     (car/set key val)
     (when ttl (car/expire key ttl))
     (car/exec))
    val))


(defn get-code
  [{:keys [key]}]
  {:pre [key]}
  (wcar* (car/get (str code-key-prefix key))))

(defn remove-code
  [{:keys [key]}]
  {:pre [key]}
  (wcar* (car/del (str code-key-prefix key))))

(defn ttl
  [{:keys [key]}]
  (wcar* (car/ttl (str code-key-prefix key))))

(defn expired?
  [params]
  (neg? (ttl params)))

(defn touch
  "Touch a key to reset the access time.
  Returns nil if the key is already expired, otherwise returns the value of the key."
  [{:keys [key ttl]}]
  {:pre [key ttl]}
  (first
   (let [key (str code-key-prefix key)]
     (wcar*
      (when (car/get key)
        (car/expire key ttl))))))


(defonce wx-access-token-key "okok-health-wx-access-token-key")

(defn store-wx-access-token
  [{:keys [expires_in] :as c}]
  (wcar*
   (car/set wx-access-token-key c :EX expires_in)))

(defn get-wx-access-token
  []
  (wcar* (car/get wx-access-token-key)))
