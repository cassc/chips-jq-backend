(ns chips.routes.mblog
  (:require
    [chips.codes :refer [cds]]
    [chips.models.mdata :refer :all]
    [chips.models.base :refer :all]
    [chips.models.fe :refer :all]
    [chips.models.mblog :as b :refer [attach-pics]]
    [chips.models.users :refer [accountid->account]]
    [chips.utils :as cu :refer [uid->type super-of? maybe-vals->int uuid write-icon-file]]
    [chips.store.rds :as rds]
    [chips.routes.validators.mblog :refer :all]
    [clojure.java.io :as io]
    [clojure.string :as s]
    [sparrows.misc :as sm :refer [str->num dissoc-nil-val]]
    [sparrows.time :refer [now-in-secs]]
    [sparrows.system :as ss]
    [noir.validation :as v]
    [taoensso.timbre :as t]
    [noir.response :as r]
    [chips.routes.base :refer :all]
    [compojure.core :refer [defroutes GET POST PUT DELETE]]
    [noir.util.route :refer [def-restricted-routes]]
    [com.climate.claypoole :as cp]
    [rpc.client :refer [mok-call]])
  (:import
    [java.io File FileInputStream]
    [javax.imageio ImageIO]))

(defn handle-get-mblog [{:keys [params aid]}]
  (let [{:keys [account_id lastid parent_id reply_only id]} params
        account_id (or (str->num account_id) aid)
        lastid (str->num lastid)
        parent_id (str->num parent_id)
        reply_only (= reply_only "y")
        id (str->num id)]
    (r/json
     (success
      (b/get-mblog
       {:aid aid :account_id account_id :lastid lastid :parent_id parent_id :reply_only reply_only :id id :hidden (when-not (= aid account_id) "n")})))))

(defn handle-delete-mblog [{:keys [params aid]}]
  (r/json
   (if-let [id (str->num (:id params))]
     (success-no-return (b/delete-mblog {:id id :aid aid}))
     (cds :required-param-not-exist))))

(defn handle-put-mblog [{:keys [params aid]}]
  (let [{:keys [act_id tag pic pics]} params
        act_id (str->num act_id)
        tag (when-not (s/blank? tag) (s/trim tag))
        params (-> params
                   (assoc :account_id aid :act_id act_id :tag tag)
                   (attach-pics))]
    (validate-put-mblog params)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (success (b/insert-mblog params))))))

(defn handle-post-mblog [{:keys [params aid]}]
  (r/json
   (success-no-return
    (b/update-mblog (-> params
                        (assoc :account_id aid)
                        (attach-pics))))))


(defn handle-put-follow [{:keys [params aid]}]
  (r/json
   (let [follow_id (str->num (:follow_id params))]
     ;;todo validate
     (success-no-return
      (when (not= follow_id aid)
        (b/follow follow_id aid))))))

(defn handle-delete-follow [{:keys [params aid]}]
  (r/json
   (let [follow_id (str->num (:follow_id params))]
     (success-no-return
      (b/unfollow follow_id aid)))))

(defn handle-get-followers [{:keys [aid params]}]
  (r/json
   (let [page (or (str->num (:page params)) 1)
         cnt (str->num (:cnt params))
         account_id (or (str->num (:aid params)) aid)]
     (success (b/get-followers {:page page :me aid :account_id account_id :cnt cnt})))))

(defn handle-get-following [{:keys [aid params]}]
  (r/json
   (let [page (or (str->num (:page params)) 1)
         cnt (str->num (:cnt params))
         account_id (or (str->num (:aid params)) aid)]
     (success (b/get-following {:page page :account_id account_id :cnt cnt :me aid})))))

(defn handle-get-recommend-users [{:keys [aid params]}]
  (r/json
   (let [page (or (str->num (:page params)) 1)
         cnt (or (str->num (:cnt params)) 10)
         cid (get-companyid)]
     (success (b/get-recommend-users {:page page :account_id aid :company_id cid :cnt cnt})))))

(defn handle-get-mblog-moments [{:keys [aid params]}]
  (r/json
   (let [page (str->num (:page params))
         cid (get-companyid)]
     (success (b/get-mblog-moments {:account_id aid :company_id cid :page page})))))

(defn handle-put-resource [{:keys [body content-length]}]
  (let [len (or content-length 0)
        tmp (File/createTempFile (uuid) "chips-res")]
    (t/info "uploading res with : " len)
    (io/copy body tmp)
    (let [img (ImageIO/read tmp)
          w (.getWidth img)
          h (.getHeight img)]
      (r/json
        (merge (success (write-icon-file tmp)) {:size (format "%d,%d" w h)})))))

(defn handle-toggle-mblog-like [{:keys [aid params]}]
  (r/json
   (success-no-return
    (b/toggle-mblog-like {:account_id aid :id (str->num (:id params))}))))

(defn handle-get-received-likes [{:keys [aid params]}]
  (let [account_id (or (str->num (:account_id params)) aid)]
    (r/json
     (success
      (b/get-mblog-like
       (assoc params
              :account_id account_id
              :hidden (when-not (= aid account_id) "n")
              :mblog_id (str->num (:mblog_id params))))))))

(defn handle-get-received-replies [{:keys [params aid]}]
  (r/json
   (success
    (b/get-received-replies (assoc params :account_id aid :hide-self-reply? true)))))

(defn handle-get-mblog-replies [{:keys [params aid]}]
  (r/json
   (success
    (when-let [pid (str->num (:parent_id params))]
      (b/get-mblog-replies {:parent_id pid :no-hide-for aid})))))

(defn handle-put-mblog-report [{:keys [params aid]}]
  (let [mid (:mid params)
        mblog (b/id->blog mid)
        target_aid (:account_id mblog)
        report {:aid aid
                :target_aid target_aid
                :uid (:haier (accountid->account aid))
                :target_uid (:haier (accountid->account target_aid))
                :mid mid
                :type (if (:parent_id mblog) "reply" "post")
                :result "pending"
                :ts (System/currentTimeMillis)}]
    (r/json
     (success-no-return (mok-call :add-report report)))))

(defn handle-get-mblog-following [{:keys [params aid]}]
  (let [{:keys [lastid cnt]} params
        [lastid cnt] (map str->num [lastid cnt])]
    (r/json
     (success (b/get-mblog-following {:aid aid :lastid lastid :cnt cnt})))))


(def-restricted-routes mblog-routes
  (PUT "/resource" req (handle-put-resource req))
  ;; mblog CRUD
  (GET "/mblog" req (handle-get-mblog req))
  (DELETE "/mblog" req (handle-delete-mblog req))
  (PUT "/mblog" req (handle-put-mblog req))
  (POST "/mblog" req (handle-post-mblog req))
  (GET "/mblog/replies" req (handle-get-mblog-replies req))
  (GET "/mblog/following" req (handle-get-mblog-following req))

  ;; mblog fav CRD
  ;; (PUT "/mblog/fav" req (handle-put-mblog-fav req))
  ;; (DELETE "/mblog/fav" req (handle-delete-mblog-fav req))
  ;; (GET "/mblog/fav" req (handle-get-mblog-fav req))

  ;; list of mblogs
  (GET "/mblog/moments" req (handle-get-mblog-moments req))
  
  ;; relations
  (PUT ["/follow/:follow_id" :follow_id #"\d+"] req (handle-put-follow req))
  (DELETE ["/follow/:follow_id" :follow_id #"\d+"] req (handle-delete-follow req))
  (GET "/followers" req (handle-get-followers req))
  (GET "/following" req (handle-get-following req))
  (GET "/recommend/users" req (handle-get-recommend-users req))
  
  (GET ["/mblog/like/toggle/:id" :id #"\d+"] req (handle-toggle-mblog-like req))
  (GET "/received/likes" req (handle-get-received-likes req))
  (GET "/received/replies" req (handle-get-received-replies req))
  
  (PUT "/mblog/report" req (handle-put-mblog-report req)) 

  )
