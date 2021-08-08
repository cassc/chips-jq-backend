(ns chips.open.haier
  (:require
   [chips.config :refer :all]
   [sparrows.cypher :refer [sha256 md5]]
   [sparrows.misc :refer [uuid]]
   [sparrows.http :as http :refer [async-request]]
   [cheshire.core :refer [parse-string generate-string]]
   [taoensso.timbre :as t]
   [clojure.string :as s]
   [clojure.core.memoize :as memo]
   ))


(defonce access-token-store (atom {:access-token nil
                                   :expires-at 0
                                   :token-type nil}))

(defn load-access-token! []
  (let [{:keys [body status]}
        @(async-request {:method :post
                         :insecure? true
                         :url (str (props [:open-haier :base-url]) "/oauth/token")
                         :form-params {:client_id (props [:open-haier :client-id])
                                       :client_secret (props [:open-haier :client-secret])
                                       :grant_type "client_credentials"}})]
    (if-not (= 200 status)
      (t/error "haier load-access-token! failed" status body)
      (let [{:keys [access_token expires_in token_type] :as resp} (parse-string body keyword)]
        (t/info "haier load-access-token!" resp)
        (reset! access-token-store {:access-token access_token
                                    :expires-at (+ (System/currentTimeMillis) (* 1000 expires_in))
                                    :token-type token_type})))))

(defn touch-access-token []
  (let [{:keys [expires-at]} @access-token-store]
    ;; reload token if it expires in one hour
    (when (< (- expires-at (System/currentTimeMillis)) (* 3600 1000))
      (load-access-token!))
    (:access-token @access-token-store)))

;; {:access_token "5a1a632e-d2ad-4189-a10d-d663b3085997", :token_type "bearer", :refresh_token "c6558230-0a02-4e9f-89e9-0f68e87ed215", :expires_in 863999, :scope "users.admin openid clients.admin"}
(defn login-by-sms [{:keys [username password] :as params}]
  (let [{:keys [body status error]}
        @(async-request {:method :post
                         :insecure? true
                         :url (str (props [:open-haier :base-url]) "/oauth/token")
                         :form-params (merge
                                       {:client_id (props [:open-haier :client-id])
                                        :client_secret (props [:open-haier :client-secret])
                                        :grant_type "password"
                                        :connection "sms"}
                                       params)})]
    (t/info body status error)
    (if (= 200 status)
      (parse-string body keyword)
      (t/error status body error))))


(defn send-vericode [{:keys [phone scenario]}]
  (let [{:keys [body status error]}
        @(async-request {:method :post
                         :insecure? true
                         :headers {"Authorization" (str "Bearer " (touch-access-token))
                                   "Content-Type" "application/json"}
                         :url (str (props [:open-haier :base-url]) "/v1/sms-verification-code/send")
                         :body (generate-string {:phone_number phone :scenario scenario})})]
    (if (= 200 status)
      (parse-string body keyword)
      (t/error status body error))))

;; {:user_id 1000030013, :phone_number "18575522826", :phone_number_verified true, :created_at 1499005189000, :updated_at 1499005189000} 
(defn user-info [access-token]
  (let [{:keys [body status error]}
        @(async-request {:method :get
                         :insecure? true
                         :headers {"Authorization" (str "Bearer " access-token)}
                         :url (str (props [:open-haier :base-url]) "/userinfo")})]
    (t/info status body error)
    (if (= 200 status)
      (parse-string body keyword)
      (t/error status body error))))

(def memo-user-info
  (memo/ttl user-info :ttl/threshold 30000))

(defn access-token->username [access-token]
  (try
    (when-let [{:keys [username phone_number email]} (memo-user-info access-token)]
      (or phone_number email username))
    (catch Exception e)))

(comment
  (send-vericode {:phone 18575522826 :scenario "login"})
  (login-by-sms {:username 18575522826
                 :password "900572"})
  (user-info "abf6a98d-3a33-4e2a-a2f5-f328c5adc0af")
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; haiji
(defn- sign-for-headers [model timestamp body]
  (let [sign (-> body
                 (s/replace #"\s" "")
                 (str (props [:haiji model :sys-id]) (props [:haiji model :sys-key]) timestamp)
                 (sha256))]
    {"sysId" (props [:haiji model :sys-id])
     "sysVersion" (props [:haiji model :sys-version])
     "timestamp" timestamp
     "sign" sign
     "content-type" "application/json"}))

(defn haiji-push [model data]
  (let [timestamp (System/currentTimeMillis)
        params (assoc data :timestamp timestamp)
        body (generate-string params)
        headers (sign-for-headers model timestamp body)
        {:keys [body error] :as request}
        @(async-request {:method :post
                         :url (props [:haiji model :data-gateway])
                         :headers headers
                         :body body})
        resp (parse-string body true)]
    (when-not (= "00000" (:retCode resp))
      (t/warn "haiji-push fail" request))))

(comment
  (haiji-push "Q7"
              {:deviceId "001122334455"
               :devType (props [:haiji "Q7" :typeid])
               :devTypeUPlus (props [:haiji "Q7" :typeid])
               :dataType "attr"
               :args {:attrs [{:name "weight" :value 62.2}
                              {:name "bodyFat" :value 12.2}
                              {:name "water" :value 70.2}
                              {:name "bmi" :value 23.2}]}})

  
  
  )
