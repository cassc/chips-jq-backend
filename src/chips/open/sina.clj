(ns chips.open.sina
  "Sina API docs: 
  http://open.weibo.com/wiki/%E5%BE%AE%E5%8D%9AAPI#.E7.94.A8.E6.88.B7

  About validation: 
  http://open.weibo.com/wiki/%E6%8E%88%E6%9D%83%E6%9C%BA%E5%88%B6%E8%AF%B4%E6%98%8E#.E6.8E.88.E6.9D.83.E6.9C.89.E6.95.88.E6.9C.9F
  微博开放平台的OAuth2.0授权机制下，第三方获取到的access_token是有过期时间的，通常过期时间为7天。
  授权级别和OAuth2.0 access_token有效期对应表：
  授权级别	测试	普通
  授权有效期	1天	7天
  注：
  1、只有未过审核的应用才处于测试级别。
  2、应用所属开发者授权应用时，有效期为5年。
"
  (:require
   [taoensso.timbre :as t]
   [chips.store.rds :as rds]
   [org.httpkit.client    :as http ]
   [clojure.data.json     :refer [read-str write-str]]))


(def ^:private sina-request-map-hk
  {:insecure? false
   :user-agent (str "cs-chips")
   :timeout 5000})

(def ^:private get-sina-user-info-url
  "https://api.weibo.com/oauth2/get_token_info")



;; 09-19 11:38:51.488  14130-14130/com.chipsea.btcontrol W/OPENSINA platform:id﹕ 1
;; 09-19 11:38:51.488  14130-14130/com.chipsea.btcontrol W/OPENSINA DB:TOKEN﹕ 2.00vargHC16r6lBb38944e0a31wTg4B
;; 09-19 11:38:51.489  14130-14130/com.chipsea.btcontrol W/OPENSINA DB:id﹕ 1945915813
;; 09-19 11:38:51.489  14130-14130/com.chipsea.btcontrol W/OPENSINA DB:username﹕ chencassc
;; 09-19 11:38:51.489  14130-14130/com.chipsea.btcontrol W/OPENSINA DB:TOKENSECRET﹕ [
;; 09-19 11:38:51.489 14130:14130 W/OPENSINA DB:expiretime ] 1443294003080

;; doc http://open.weibo.com/wiki/Oauth2/get_token_info
(defn- store-key
  [openid]
  (str "sina:" openid))

(defn validate-sina-token
  "When token is valid, returns
  `{:uid 1945915813, :appkey 1616784126, :scope follow_app_official_microblog, :create_at 1442633927, :expire_in 657725}` "
  [access-token openid]
  (let [k (store-key openid)]
    (or (and access-token (= (rds/get-code {:key k}) access-token))
        (let [{:keys [status body error] :as resp}
              @(http/post
                get-sina-user-info-url
                (assoc sina-request-map-hk
                       :form-params {:access_token access-token}))]
          (if (or error (not= 200 status))
            (t/warn "Open.sina failed? " resp)
            (let [{:keys [error uid expire_in] :as r} (read-str body :key-fn keyword)]
              (t/debug "Open sina login returns:" body)
              (when (and uid (= (str openid) (str uid)) (pos? expire_in))
                (rds/store-code {:key k :val access-token :ttl expire_in})
                r)))))))

(defn user-info
  [access-token openid]
  (let [{:keys [status body error] :as resp}
        @(http/get
          "https://api.weibo.com/2/users/show.json"
          (assoc sina-request-map-hk
                 :query-params {:access_token access-token
                                :uid openid}))]
    (if (or error (not= 200 status))
      (t/warn "Open.sina.user.info failed? " resp)
      (-> body (read-str :key-fn keyword)))))



(defn user-email
  "Get user email. This requires user authentication"
  [access-token]
  (let [{:keys [status body error] :as resp}
        @(http/get
          "https://api.weibo.com/2/account/profile/email.json"
          (assoc sina-request-map-hk
                 :query-params {:access_token access-token}))]
    (if (or error (not= 200 status))
      (t/warn "Open.sina.user.info failed? " resp)
      (-> body (read-str :key-fn keyword)))))

(comment
  (validate-sina-token "2.00vargHC16r6lBb38944e0a31wTg4B" 1945915813)
  (validate-sina-token "2.00varghc16r6lbb38944e0a31wtg4b" 1945915813)
  (validate-sina-token "2.00aqEVDG16r6lB1a5f2f56b15aBJJC" 5548532804)
  (user-info "2.00aqEVDG16r6lB1a5f2f56b15aBJJC" 5548532804)
  (user-info "2.00NXN8EC16r6lB67c5cdef6eyoWmCC" "1897857895")
  (validate-sina-token "2.00NXN8EC16r6lB67c5cdef6eyoWmCC" "1897857895")
  )
