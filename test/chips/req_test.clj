(ns chips.req-test
  (:require
   [sparrows.cypher :as cypher :refer [form-encode md5]]
   [clojure.test :refer :all]
   [clj-http.client :as client]
   [clj-http.cookies :refer [get-cookies]]
   [cheshire.core :refer [generate-string parse-string]]))

(def cs-base-url
  (or (.get (System/getenv) "CS_BASE_URL") "https://192.168.0.72"))

(def php-base-url
  (str cs-base-url "/v0dev"))

(def api-base-url
  (str cs-base-url "/v1"))


(defonce cs (clj-http.cookies/cookie-store))

(def ^:dynamic *cs-token* nil)

(def gm-client-options
  {:insecure? true
   :user-agent "CHIPSEA-API.ROUTES.TEST"
   :cookie-store cs
    ; decode response header to detect encoding
   :decode-body-headers true
   :as :auto
   :headers {"cs-app-id" "ebcad75de0d42a844d98a755644e30"
             "cs-token" *cs-token*}
   :socket-timeout 10000
   :conn-timeout 10000})


(defmacro def-httpmethod
  [method]
  `(defn ~method
     ~(str "Issues an client/" method " request which is wrapped in a try-catch block."
           "When 503 or 403 error occurs, will retry in 5 seconds")
     ~'[url params]
     (let [request# ~(symbol (str "client/" (clojure.string/lower-case method)))]
       (request# ~'url ~'params))))

(def-httpmethod GET)
(def-httpmethod POST)

(defn chipsea-request
  [url key data]
  (let [params {key
                (generate-string
                 {:app_id "ebcad75de0d42a844d98a755644e30"
                  :data data})}]
    (prn params)
    (POST
     url
     (assoc  gm-client-options :form-params params))))

(defn chipsea-login
  []
  (chipsea-request
   ;;"http://localhost/Api.php?m=Account&a=login"
   ;;(str (cs-base-url) "/Api.php?m=Account&a=login")
   ;;"http://intl.tookok.com/Api.php?m=Account&a=loginForEmail"
   (str "http://192.168.0.71/btWeigh/Api.php?" (form-encode "m=Account&a=login"))
   ;;"http://www.tookok.cn/OKOK1_3/btWeigh/Api.php?m=Account&a=login"
   :login
   ;; :loginForEmail
   {:account {;;:id 0
              ;; :email "88888888@qq.com " :password "e10adc3949ba59abbe56e057f20f883e"
              :phone "15362739763" :password (cypher/md5 "900816")
              ;;:phone "15915373060" :password (cypher/md5 "123456")
              ;;:email "dsaf@asdf.com"
              ;;:days 0
              ;;:grade_id 0
              ;;:last_login nil
              ;;:sina_blog nil
              }}))



(defn chipsea-reset-password
  []
  (chipsea-request
   ;;"http://intl.tookok.com/Api.php?m=Account&a=loginForEmail"
   ;;"http://192.168.0.71/btWeigh/Api.php?m=Account&a=login"
   "http://www.tookok.cn/OKOK1_3/btWeigh/Api.php?m=Account&a=verifyCode"
   :verifyCode
   {:account {:phone "18575522826" :flag "0"}}))

(defn chipsea-role
  [account_id role_id]
  (GET
   (str (cs-base-url) "/Api.php?m=Role&a=findRole&account_id=" account_id "&role_id=" role_id)
   gm-client-options))

(defn chipsea-sync-cal
  []
  (chipsea-request
   (str (cs-base-url) "/Api.php?m=Profile&a=syncAndCalculate")
   :syncAndCalculate
   {:sync_time "2014-01-11 20:00:00"
    :role_id 70
    :account_id 38}))

;; first time third party login
(defn thirdparty-regist
  []
  (chipsea-request
   (str (cs-base-url) "/Api.php?m=Account&a=regAndLoginOfThird")
   :regAndLoginOfThird
   {:account
    {:qq "dafasfasfas"
     :password "dfaskfa"
     }}))

(defn thirdparty-bound
  []
  (chipsea-request
   (str (cs-base-url) "/Api.php?m=Account&a=boundUnBoundQQ")
   :boundUnBoundQQ
   {:account
    {:qq "dafasfasfas"
     :password "dfaskfa"
     }}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; write test methods for testing local, dev, and remote servers

(defn get-body-as-json
  [resp]
  (when-let [body (:body resp)]
    (if (string? body)
      (try
        (parse-string body true)
        (catch Exception e
          body))
      body)))

(def cs-client-options
  {:insecure? true
   :user-agent "CHIPSEA-API.ROUTES.TEST"
   :cookie-store cs
    ; decode response header to detect encoding
   :decode-body-headers true
   :as :auto
   :headers {"cs-app-id" "ebcad75de0d42a844d98a755644e30"}
   :socket-timeout 10000
   :conn-timeout 10000})


(defn api-login
  [{:keys [uid sina_blog qq password] :as params}]
  {:pre [(or uid sina_blog qq)]}
  (POST
   (str api-base-url "/account/login")
   (assoc cs-client-options
     :content-type :json
     :form-params params)))

(defn extract-cs-token
  "Extract token from login"
  [resp]
  (-> resp :headers (get "Cs-Token")))



(defn api-regist
  [{:keys [uid vericode password company_id] :as params}]
  (POST
   (str api-base-url "/account/regist")
   (assoc cs-client-options
     :content-type :json
     :form-params params)))




(defn api-vericode
  [{:keys [uid flag company_id] :as params}]
  (POST
   (str api-base-url "/vericode")
   (assoc cs-client-options
     :query-params params)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (def resp (api-regist {:uid "18025420265" :password "dddddddd"}))
  (api-regist {:uid "18575522826" :password (cypher/md5 "hello") :vericode "3865"})
  (def resp (api-login {:uid "15915373000" :password "e10adc3949ba59abbe56e057f20f883e"}))
  (api-vericode {:uid "18575522826" :flag 0})
  (api-vericode {:uid "ca7@qq.com" :flag 0})
  (api-vericode {:uid "fakeemailtest@unknown.com" :flag 0})


  )
