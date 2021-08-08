(ns chips.open.qq
  "
client doc: http://wiki.open.qq.com/index.php?title=Android_API%E8%B0%83%E7%94%A8%E8%AF%B4%E6%98%8E&=45038
server doc: http://wiki.open.qq.com/wiki/website/OpenAPI%E8%B0%83%E7%94%A8%E8%AF%B4%E6%98%8E_OAuth2.0
response doc: http://wiki.open.qq.com/wiki/website/get_user_infox
https://graph.qq.com/user/get_user_info?access_token=YOUR_ACCESS_TOKEN&oauth_consumer_key=YOUR_APP_ID&openid=YOUR_OPENID

http://wiki.open.qq.com/wiki/faq/website/%E5%BC%80%E5%8F%91%E8%81%94%E8%B0%83%E7%9B%B8%E5%85%B3%E9%97%AE%E9%A2%98#10._access_token.E6.9C.89.E6.95.88.E6.9C.9F.E4.B8.BA.E5.A4.9A.E4.B9.85.EF.BC.9F
access token有效期为90天，每次调用api成功，自动续期90天。
 "
  (:require
   [chips.store.rds :as rds]
   [taoensso.timbre :as t]
   [chips.config :refer [props]]
   [org.httpkit.client    :as http ]
   [clojure.data.json     :refer [read-str write-str]]))



;; APP登录成功后能获取的数据
;; {
;; "ret"          :0,
;; "pay_token"    :"xxxxxxxxxxxxxxxx",
;; "pf"           :"openmobile_android",
;; "expires_in"   :"7776000",
;; "openid"       :"xxxxxxxxxxxxxxxxxxx",
;; "pfkey"        :"xxxxxxxxxxxxxxxxxxx",
;; "msg"          :"sucess",
;; "access_token" :"xxxxxxxxxxxxxxxxxxxxx"
;; }


(def ^:private qq-request-map-hk
  {:insecure? false
   :user-agent (str "cs-chips")
   :timeout 5000})

(def ^:private get-qq-user-info-url
  "https://graph.qq.com/user/get_user_info")

(def qq-token-cache-time (* 3600 90))
(defn- store-key
  [company_id openid]
  (str company_id"qq:" openid))

(defn validate-qq-token
  "Validate open.qq login. Returns user-info if the token and openid is valid"
  [access-token openid & {:keys [company_id]}]
  (let [k (store-key company_id openid)
        appid (props [(keyword (str "open-qq-" company_id)) :appid]
                     {:default (props [:open-qq :appid])})]
    (or (when-let [qq (rds/get-code {:key k})]
          (when (= (:access-token qq) access-token)
            (dissoc qq :access-token)))
        (let [url (str get-qq-user-info-url
                       "?access_token=" access-token
                       "&oauth_consumer_key=" appid
                       "&openid=" openid)
              {:keys [status body error] :as resp}
              @(http/get url qq-request-map-hk)]
          (if (or error (not= 200 status))
            (t/error "Openqq failed, network error? " resp)
            (let [{:keys [ret] :as r} (read-str body :key-fn keyword)
                  qq (select-keys r [:nickname :gender :city])]
              (t/debug "Open qq login returns:" body)
              (when (zero? ret)
                (t/info "Login qq " qq)
                (rds/store-code {:key k :val (assoc qq :access-token access-token) :ttl qq-token-cache-time})
                r)))))))


(comment
  ;; 09-19 11:07:23.088  25191-25191/com.chipsea.btcontrol W/OPENQQ platform:id﹕ 3
  ;; 09-19 11:07:23.088  25191-25191/com.chipsea.btcontrol W/OPENQQ DB:TOKEN﹕ 6FF4BCC2CA0701F965AEE82402B70365
  ;; 09-19 11:07:23.088  25191-25191/com.chipsea.btcontrol W/OPENQQ DB:id﹕ 50CF6CEE7D5D0AC35FEA4955690A1F9F
  ;; 09-19 11:07:23.088  25191-25191/com.chipsea.btcontrol W/OPENQQ DB:username﹕ 凌夷真义
  ;; 09-19 11:07:23.088  25191-25191/com.chipsea.btcontrol W/OPENQQ DB:TOKENSECRET﹕ [ 09-19 11:07:23.088 25191:25191 W/OPENQQ DB:expiretime ]
  ;;     1450408042237
  (validate-qq-token "6FF4BCC2CA0701F965AEE82402B70365" "50CF6CEE7D5D0AC35FEA4955690A1F9F")
  (validate-qq-token "6ff4bcc2ca0701f965aee82402b70365" "50cf6cee7d5d0ac35fea4955690a1f9f")
  (validate-qq-token "60EB5C222365DA6E9A68240B456FD6AC" "DDBEE0852861CD6210C5D89C6AF8AB52")

  ;; doc http://wiki.open.qq.com/wiki/website/get_user_info
  ;; {:ret 0, :figureurl_1 "http://qzapp.qlogo.cn/qzapp/1104591433/50CF6CEE7D5D0AC35FEA4955690A1F9F/50", :figureurl_2 "http://qzapp.qlogo.cn/qzapp/1104591433/50CF6CEE7D5D0AC35FEA4955690A1F9F/100", :nickname "凌夷真义", :city "", :msg "", :is_yellow_vip "0", :figureurl "http://qzapp.qlogo.cn/qzapp/1104591433/50CF6CEE7D5D0AC35FEA4955690A1F9F/30", :level "0", :year "1935", :is_yellow_year_vip "0", :vip "0", :gender "男", :figureurl_qq_1 "http://q.qlogo.cn/qqapp/1104591433/50CF6CEE7D5D0AC35FEA4955690A1F9F/40", :figureurl_qq_2 "http://q.qlogo.cn/qqapp/1104591433/50CF6CEE7D5D0AC35FEA4955690A1F9F/100", :province "", :yellow_vip_level "0", :is_lost 0}
  )
