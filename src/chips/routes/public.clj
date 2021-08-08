(ns chips.routes.public
  (:require
   [chips.oss :as oss]
   [chips.models.admin :as admin]
   [chips.models.tutorial :as tt]
   [chips.models.shop :as shop]
   [chips.service :refer [add-pv-store]]
   [chips.store.rds :as rds]
   [chips.routes.base :refer :all]
   [chips.models.once :refer [gen-fake-mdata-for]]
   [chips.config :refer [props wx-event-chan iotwx-event-chan]]
   [chips.codes :refer [cds cds-map]]
   [chips.models.users :as mu]
   [chips.models.base :refer [wrap-companyid get-public-account topup-score]]
   [chips.models.data :refer :all]
   [chips.models.mblog :as b]
   [chips.utils :refer [id->pool low-version-ios? up-version-okok? b64-dec->bytes make-log-file android?
                        ;;cs-algorithm-utils  calculate-profile-scores
                        ]]
   [chips.routes.validators.users :refer :all]
   [chips.kjt :as kjt]
   [chips.yl :as yl]
   
   [clojure.core.async :as async :refer [thread]]
   [clojure.java.io :as io]
   [sparrows.misc :as sm :refer [lowercase-trim wrap-exception str->num]]
   [clojure.string :as s]
   [sparrows.cypher :refer [sha1 base64-encode base64-decode]]
   [noir.validation :as v]
   
   [taoensso.timbre :as timbre]
   [noir.response :as r]
   
   [compojure.core :refer [defroutes GET POST ANY PUT OPTIONS]]
   [noir.util.route :refer [def-restricted-routes]]
   [compojure.route :refer [not-found]]
   [com.climate.claypoole :as cp] 
   [rpc.client :refer [mok-call]]
   [taoensso.timbre :as t]
   [clojure.edn :as edn])
  (:import
   [java.io File]))

(timbre/refer-timbre)


(defn- fix-weight-unit
  [data]
  (info data)
  (let [weight_unit (get-in data [:account :weight_unit])]
    (if (= 1403 (str->num weight_unit))
      (assoc-in data [:account :weight_unit] 1400)
      data)))

(defn- hide-fe-mtypes [data]
  (update-in data [:account :mtypes] #(when %
                                        (->> (s/split % #",")
                                             (remove #{"food" "exercise"})
                                             (s/join ",")))))

(defn- login
  [params]
  (if-let [data (mu/login-user params)]
    (let [aid       (-> data :account :id)
          data (cond
                 (and
                  (android? params)
                  (not (up-version-okok? params))) (hide-fe-mtypes data)
                 :else                             data)
          token-map (rds/create-cstoken aid)]
      (cp/future
        (id->pool :shared-pool) (mu/update-last-login {:id aid})
        (id->pool :shared-pool) (topup-score {:source :first-login :aid aid}))
      (r/set-headers
       token-map
       (r/json (assoc (cds :success) :data data))))
    (r/json (cds :account_password_error))))

(defn- check-server [server]
  (#{"chips" "hawks"} server))

(defn- redirect-to-latest-apk-link [{:keys [server companyid]}]
  (when-let [url (latest-app-for {:server server :region "china" :platform "android" :companyid companyid})]
    (r/redirect (str "http://www.tookok.cn/downloads/apk/" (:url url)))))

(defroutes dev-routes
  "Dev routes which should be used only in dev env."
  (GET "/doc" []
       (render-markdown "README.md"))

  (GET ["/doc/:md" :md "[a-zA-Z0-9]+"] [md]
       (try
         (render-markdown (str (s/upper-case md) ".md"))
         (catch Exception e
           (not-found (.getMessage e)))))
  (GET "/gen/:mtype/for/:roleid" [mtype roleid cnt]
       (try
         (do
           (gen-fake-mdata-for mtype roleid (or (str->num cnt) 1000))
           "Success")
         (catch Exception e
           (.getMessage e))))
  )

(defn- handle-put-log-app [{:keys [body headers params aid]}]
  (info (:logtype params) headers)
  (let [out (make-log-file headers)]
    (io/copy body out)
    (thread
      (try
        (parse-and-save-log {:deviceid (headers "cs-device-id")
                             :aid aid}
                            out)
        (catch Throwable e
          (error "handle log from app failed: " e)
          (error e))))
    (r/json (success))))

(defn- handle-get-config-app [req]
  (success))

(defn- handle-login [{{:keys [haier wdata access_token company_id] :as params} :params headers :headers}]
  (debug "login" params)
  (wrap-companyid
   (str->num company_id)
   (let [haier  (sm/trim haier)
         cid (str->num company_id)
         params         (assoc params :haier haier :company_id cid)]
     (validate-post-account-login params)
     (if-let [err (first (v/get-errors))]
       (r/json err)
       (login (assoc params :headers headers))))))

(defn handle-get-public-account [{:keys [aids]}]
  (r/json
   (success (get-public-account (set (map str->num (s/split aids #",")))))))

(defn handle-get-banner [_]
  (r/json
   (success (b/get-banner-list))))

(defn handle-get-activity [_]
  (r/json
   (success (b/get-activity-list))))

(defn get-jifen-list [req]
  (r/json
   (if-let [aid (or (str->num (get-in req [:params :account_id]))
                    (:aid req))]
     (let [{:keys [cnt lastid]} (:params req)
           [cnt lastid] (map str->num [cnt lastid])]
       (success (mu/get-jifen-list {:aid aid :lastid (or lastid (Long/MAX_VALUE)) :cnt (or cnt 10)})))
     (cds :required-param-not-exist))))

(defn get-jifen [req]
  (r/json
   (if-let [aid (or (str->num (get-in req [:params :account_id]))
                    (:aid req))]
     (success (mu/get-jifen aid))
     (cds :required-param-not-exist))))

(defn handle-get-pop-mblog [{:keys [params aid] :as req}]
  (let [cnt (str->num (get-in req [:params :cnt]))
        page (str->num (get-in req [:params :page]))
        account_id (str->num (get-in req [:param :account_id]))]
    (r/json (success (b/get-pop-mblog {:cnt cnt :page page :aid aid :account_id account_id})))))

(defn handle-get-mblog-by-actid [{:keys [params aid]}]
  (let [{:keys [actid lastid cnt]} params
        [actid lastid cnt] (map str->num [actid lastid cnt])]
    (r/json (success (b/get-mblog-by-actid {:actid actid :lastid lastid :cnt cnt :aid aid})))))

(defn get-preview-msg [phone]
  (let [root (io/file (props :article-root) phone)]
    (when (.exists root)
      (->> (file-seq root)
           (map (fn [f]
                  (when (= (.getName f) "preview.meta")
                    (edn/read-string (slurp f)))))
           (filter identity)))))

(defn handle-get-broadcast-list [req]
  (let [{{:keys [sex end cnt categories] :as params} :params aid :aid} req]
    ;;(t/info "get-broadcast-list" params)
    (r/json
     (success
      (let [x-preview-msg (some-> aid mu/accountid->account :haier get-preview-msg)
            bd-list (admin/get-broadcasts params)]
        (concat
         x-preview-msg
         (if-let [fav-ids (and aid (seq (map :id (admin/get-fav (assoc params :aid aid :page 1 :cnt (Integer/MAX_VALUE))))))]
           (map (fn [{:keys [id] :as bd}] (assoc bd :fav (if (some (partial = id) fav-ids) "y" "n"))) bd-list)
           bd-list)))))))

(defn handle-get-article [req]
  (let [res-path (get-in req [:params :res-path])
        res (io/file (props :article-root) res-path)]
    (t/info "handle-get-article" res-path)
    (if (.exists res)
      (do
        (when (and res-path (s/ends-with? res-path ".html"))
          (admin/inc-pv-by-path res-path))
        res)
      (not-found "Not found!"))))

(defn attach-faq [{:keys [tag]} x-video-tutorial]
  (if (= tag "发现")
    (concat x-video-tutorial [{:calory 0
                               :videos (props :faq-html)
                               :dir ""
                               :fav "n"
                               :rqdev "n"
                               :cover ""
                               :duration ""
                               :advice "无"
                               :title "常见问题"
                               :ts 0
                               :warning "无"
                               :id 0
                               :audience "所有"
                               :tag tag}])
    x-video-tutorial))

(defn handle-get-tutorial [{:keys [params aid]}]
  (r/json (success (tt/get-tutorial (assoc params :aid aid))
           ;;(->> (attach-faq params))
                   )))

(defn handle-get-tutorial-categories [_]
  (r/json (success (tt/get-tutorial-categories))))

(defn handle-get-res [{{:keys [title]} :params query-string :query-string :as req}]
  ;;(t/info req)
  (let [base (props :static-resources)]
    (r/redirect (str base title "?" query-string))))

(defn handle-post-tutorial-fav [{{:keys [fav tid]} :params aid :aid}]
  (let [tid (str->num tid)
        fav (#{"y" "n"} fav)]
    (r/json
     (if (and tid fav)
       (if (pos? tid)
         (success (tt/update-tutorial-fav {:aid aid :tid tid :fav? (= fav "y")}))
         (success))
       (cds :invalid-param)))))

(defn handle-get-icon [{{:keys [checksum]} :params query-string :query-string :as req}]
  (let [img (io/file (props :user-logos) checksum)]
    (if (.exists img)
      img
      (let [base (props :static-resources)]
        (r/redirect (str base checksum "?" query-string))))))

;; should return
;; {"StatusCode":"200","AccessKeyId":"STS.NJHEAFsuZAdrYFwH9oZJXs1pL","AccessKeySecret":"7i3NUu4q53RvRhFk1owQ3MXEGA96T9NTVWeLzRyYrGfD","SecurityToken":"CAISggJ1q6Ft5B2yfSjIr4n9Dvvynqp79qaZW2DGrDk6VsV0nPTbrjz2IHpKdXFpBukev/g/mGxR5/sflqB6T55OSAmcNZIoEn+fKejkMeT7oMWQweEuuv/MQBquaXPS2MvVfJ+OLrf0ceusbFbpjzJ6xaCAGxypQ12iN+/m6/Ngdc9FHHP7D1x8CcxROxFppeIDKHLVLozNCBPxhXfKB0ca3WgZgGhku6Ok2Z/euFiMzn+Ck7ZM+NqqecH6Ppg3Yc8nC+3YhrImKvDztwdL8AVP+atMi6hJxCzKpNn1ASMKuUvXa7OLq4U+cF8lOvFrRvNezv/njrh9s+ramInnO+vw1T2yxU0agAEYo3n6Z34AoK/peZ5iSj+GKRwmQjhAnkzENgI4bTzDynh3Sf09ecLOR/U7ncjW294xAekaB5zbidy69gLcZMGGOiN4EnFdvizdgpgCacGgQ3CDKjnupZB/848g3cLoNfhF0EdFmLZLI/F7ofC7LCvNe7O0TPZX96W8iisunEZPYQ==","Expiration":"2019-01-10T04:32:00Z"}
(defn handle-get-oss-sts [{:keys [aid]}]
  (r/json
   (oss/acs-request aid)))


;; 19-03-02 11:23:43.532 +0800 iZm5e6qzvrrju3blpn1rneZ INFO [chips.middleware:114] -  UA: Apache-HttpClient/4.3.1 (java 1.5)  Method: :post  Req: /pub/kjt/notify  Aid: nil  From: 183.136.222.142  Params: {:sign "iXk0M0vX3U6d36IdzwKhQwDEsRPKsv8NxQCSpXiWZatbQkOZS3A9Dv6RT07LUyaoB8M1WlkylwJ6gwofMP90wgLIHpdixPqDD+XUqn+rkZSHxeo68uU04mXRQLysFyQnGb00NvLf7iJPj0MJrZNR/UTlKWXo3SZpA62eFBCXIhbWPHT5zXEItqrF1rkHvgq4K5HjX4bw9zpMP6sd2CmraLKeVm19bLSm7gebPXVrfDJ4mnNurtREKmk/lYiQumkt8GmbhnvBUPuQJi++LLfDq10YfmDhfnaGILAOYbTpia4Mhvahlw/j2VJeHkWue35isZfn6RIpwT+ZynZCKK0jBA==", :outer_trade_no "20190302112250521", :notify_type "trade_status_sync", :sign_type "RSA", :trade_amount "0.10", :notify_id "dbf60459ae1d4ad98daf0907cef3b693", :inner_trade_no "101149697088363186133", :_input_charset "UTF-8", :gmt_payment "20190302112343", :trade_status "TRADE_SUCCESS", :gmt_create "20190302112250", :version "1.0", :notify_time "20190302112343"}  Time: 107
(defn handle-any-pub-kjt-notify [{:keys [params] :as req}]
  (t/info "kjt-notify received:" req)
  (r/json
   (if (kjt/rsa-verify params)
     (success-no-return (shop/handle-kjt-notify params))
     (cds :auth-failed))))

;; 19-03-02 11:23:48.385 +0800 iZm5e6qzvrrju3blpn1rneZ INFO [chips.routes.public:243] - kjt-paysuccess page {:access-rules [{:uris ["/*"], :rule #object[chips.rules$user_page 0x2e026345 "chips.rules$user_page@2e026345"], :on-fail #object[chips.core$denied 0x58a1d055 "chips.core$denied@58a1d055"]}], :cookies {}, :remote-addr "101.233.157.236", :params {}, :flash nil, :route-params {}, :headers {"host" "47.105.225.141:8080", "user-agent" "Mozilla/5.0 (Linux; Android 7.1.2; Hisense A2T Build/N2G47H; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/66.0.3359.126 MQQBrowser/6.2 TBS/044504 Mobile Safari/537.36 MMWEBID/2972 MicroMessenger/7.0.3.1400(0x2700033A) Process/tools NetType/WIFI Language/zh_CN", "connection" "close", "upgrade-insecure-requests" "1", "accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,image/wxpic,image/sharpp,image/apng,image/tpg,*/*;q=0.8", "accept-language" "zh-CN,zh-CN;q=0.9,en-US;q=0.8", "x-forwarded-for" "101.233.157.236", "accept-encoding" "gzip, deflate", "x-scheme" "http", "x-real-ip" "101.233.157.236"}, :async-channel #object[org.httpkit.server.AsyncChannel 0x4be9376c "/127.0.0.1:3333<->/127.0.0.1:46286"], :server-port 8080, :content-length 0, :form-params {}, :compojure/route [:any "/pub/kjt/paysuccess"], :websocket? false, :session/key nil, :query-params {}, :content-type nil, :character-encoding "utf8", :cstoken nil, :uri "/pub/kjt/paysuccess", :aid nil, :server-name "47.105.225.141", :query-string nil, :body nil, :multipart-params {}, :scheme :http, :request-method :get, :session {}}
(defn handle-any-pub-kjt-paysuccess-html [req]
  (t/info "kjt-paysuccess page" req)
  pay-success-html)


;; {:bankCardNo "622203*********1563", :tid "00000001", :invoiceAmount "1", :buyerPayAmount "1", :mid "898310148160568", :sign "0F39D3D7902BB8411C422B1773047044", :billFunds "现金:1", :targetOrderId "321903291727318870008", :subInst "104000", :payTime "2019-03-29 17:27:31", :orderDesc "全渠道", :merOrderId "319463330120190329172731300", :totalAmount "1", :targetSys "UAC", :seqId "00476000422N", :billFundsDesc "现金支付0.01元。", :zT "iDbP", :status "TRADE_SUCCESS", :notifyId "1d8b9201-5281-4945-9df6-014991260602", :settleDate "2019-03-29", :couponAmount "0"}
(defn handle-any-pub-yl-notify [{:keys [params] :as req}]
  (t/info "yl-notify" req)
  (if (yl/check-sign params)
    (do
      (shop/handle-yl-notify params)
      "SUCCESS")
    "FAILED"))

(defroutes public-routes
  (OPTIONS ["/:any" :any #".*"] [] (r/json (success)))
  (GET "/mblog/byactid" req (handle-get-mblog-by-actid req))
  (GET "/pop/mblog" req (handle-get-pop-mblog req))
  (GET "/jifen/list" req (get-jifen-list req))
  (GET "/jifen" req (get-jifen req))
  (GET "/docmodified/:lastchange" [lastchange]
       (let [f (io/file "README.md")
             lm (.lastModified f)
             lc (or (str->num lastchange) 0)
             newer? (> lm lc)]
         (r/json
          (success (when newer? {:last_change lm})))))
  ;; (GET "/prof/score" {:keys [params]}
  ;;      (r/json
  ;;       (calculate-profile-scores params)))

  ;; (GET "/csalgo" {:keys [params]}
  ;;      (r/json
  ;;       (cs-algorithm-utils params)))
  
  (GET "/" [] "chips")

  (GET "/public/account" [& params] (handle-get-public-account params))

  ;; rewrite from downloads/apk/btWeigh.apk
  ;; redirect to non-ssl download link.
  ;; should be called by website only
  (GET "/apkdl/:server/:companyid" [server companyid]
       (if (check-server server)
         (redirect-to-latest-apk-link {:server server :companyid companyid})
         (not-found "")))

  (GET "/apkdl" [pkg platform]
       (if-let [app (mok-call :latest-app-for {:platform platform :pkg pkg})]
         (r/redirect (str "http://www.tookok.cn/downloads/apk/" (:url app)))
         (not-found "")))
  
  (GET ["/company/:companyid" :companyid #"[1-9]+[0-9]*"] [companyid]
       (r/json
        (success
         (dissoc (get-company {:id companyid}) :boot_img_path) )))

  (GET ["/product/:productid/:lang" :productid #"[0-9]+[0-9]*"] [productid lang]
       (r/json
        (success
         (get-product {:product_id productid :lang lang}))))

  (GET ["/product/:productid" :productid #"[0-9]+[0-9]*"] [productid]
       (r/json
        (success
         (get-product {:product_id productid}))))

  (GET "/cds" [] (r/json (success cds-map)))

  ;; TODO should cache openid validation, to reduce login response time
  (POST "/account/login" req (handle-login req)) 

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; BROADCAST
  (GET "/pv/buid/:buid" [buid]
       (do
         (add-pv-store buid)
         (r/json (success))))
  (GET ["/article/:res-path" :res-path #".*"] req handle-get-article)
  (GET "/public/bdcategories" [appid]
       (r/json
        (success (mok-call :broadcast-categories {:appid (or appid (props :bd-appid))}))))
  
  (GET ["/broadcastlist/:companyid" :companyid #"[1-9]+[0-9]*"] req handle-get-broadcast-list)
  
  (GET ["/ncomments/:bid" :bid #"\d+"] [bid]
       (r/json
        (success (mok-call :get-num-of-comments {:bid bid}))))

  (GET ["/nlikes/:bid" :bid #"\d+"] [bid]
       (r/json
        (success (mok-call :get-num-of-likes {:bid bid}))))

  (GET ["/acomment/:bid" :bid #"\d+"] [bid lastid cnt]
       (r/json
        (success (mok-call :get-comments {:bid bid :lastid (str->num lastid) :cnt (str->num cnt)}))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; APP UPDATE CHECK
  (GET "/latestapp" {:keys [params]}
       (r/json
        (let [{:keys [platform pkg version]} params]
          (if (and platform pkg)
            (if-let [app (mok-call :get-app-update params)]
              (success app)
              (cds :software_version_newest))
            (cds :required-param-not-exist)))))
  
  (PUT "/log/app/:logtype" req (handle-put-log-app req))
  (GET "/config/app" req (handle-get-config-app req))

  ;; banner
  (GET "/banner" req (handle-get-banner req))
  ;; activities
  (GET "/activity" req (handle-get-activity req))

  (GET "/tutorial" req handle-get-tutorial)
  (GET "/tutorial/categories" req handle-get-tutorial-categories)

  (GET "/res/:title" req handle-get-res)

  (POST "/tutorial/fav" req handle-post-tutorial-fav)

  (GET "/icon/:checksum" req handle-get-icon)

  (ANY "/oss/sts" req handle-get-oss-sts)

  ;; kjt async notify
  (ANY "/pub/kjt/notify" req handle-any-pub-kjt-notify)
  (ANY "/pub/kjt/paysuccess" req handle-any-pub-kjt-paysuccess-html)

  (ANY "/pub/yl/notify" req handle-any-pub-yl-notify)
  )
