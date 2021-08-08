(ns chips.routes.users
  (:require [chips
             [config :refer [props]]
             [codes :refer [cds]]
             [utils :refer [dissoc-empty-val b64-dec->bytes phone?]]]
            [chips.models
             [data :refer [update-account-units update-reminders update-account-mtypes update-account-by-id
                           update-flash-reminders]]
             [users :as mu :refer [aid->cid]]
             [base :refer [wrap-companyid aid->first-role topup-score]]]
            [chips.routes.base :refer :all]
            [chips.routes.validators.users :refer :all]
            [clojure.string :as s]
            [compojure.core :refer [defroutes DELETE GET POST PUT]]
            [noir
             [response :as r]
             [validation :as v]]
            [noir.util.route :refer [def-restricted-routes]]
            [sparrows.misc :as sm :refer [dissoc-nil-val str->num]]
            [taoensso.timbre :as timbre]
            [rpc.client :refer [mok-call]]))

(timbre/refer-timbre)

(defn handle-post-account [{:keys [params aid]}]
  (let [{:keys [length_unit weight_unit mtypes signature appbg]} params
        [length_unit weight_unit] (map str->num [length_unit weight_unit])
        mtypes (when mtypes (s/split mtypes #","))
        appbg (when-not (s/blank? appbg) appbg)
        account-conf {:length_unit length_unit :weight_unit weight_unit :mtypes mtypes :appbg appbg :signature signature :aid aid}]
    (validate-update-account account-conf)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (do
         (mu/update-account (dissoc-nil-val account-conf))
         (success))))))

(defn handle-get-afav [{:keys [aid params]}]
  (r/json
   (success (mok-call :get-fav (assoc params :aid aid)))))

(defn handle-get-afav-count [{:keys [aid params]}]
  (let [account_id (or (str->num (:account_id params)) aid)]
    (r/json
     (success (mok-call :count-fav {:aid account_id})))))

(defn handle-put-afav [{:keys [aid params]}]
  (r/json
   (success (mok-call :add-fav (assoc params :aid aid)))))
(defn handle-delete-afav [{:keys [aid params]}]
  (r/json
   (success (mok-call :delete-fav (assoc params :aid aid)))))

(defn handle-get-account [{:keys [aid]}]
  (r/json (success (mu/accountid->account aid))))

(defn handle-get-app-msg [{:keys [aid params]}]
  (let [status (:status params)]
    (r/json
     (success
      (mu/get-msg aid (when-not (s/blank? status) status))))))

(defn handle-delete-app-msg [{:keys [aid params]}]
  (let [id (str->num (:id params))]
    (r/json
     (if id
       (success-no-return
        (mu/delete-msg {:mid id :aid aid}))
       (cds :required-param-not-exist)))))

(defn handle-post-app-msg [{:keys [aid params]}]
  (let [{:keys [status id]} params]
    (r/json
     (if (and (#{"read" "unread"} status) id)
       (success-no-return
        (mu/update-msg {:aid aid :mid id :status status}))
       (cds :invalid-param)))))

(defn handle-post-reminder [{{:keys [reminders] :as params} :params aid :aid}]
  (r/json
   (let [reminders (map #(assoc % :account_id aid) reminders)]
     (validate-reminder-weight reminders)
     (if-let [err (first (v/get-errors))]
       err
       (do
         (update-reminders reminders)
         (success))))))

(defn handle-post-flash-reminder [{{:keys [reminders] :as params} :params aid :aid}]
  (r/json
   (let [reminders (map #(assoc % :account_id aid) reminders)]
     (validate-reminder-weight reminders)
     (if-let [err (first (v/get-errors))]
       err
       (do
         (update-flash-reminders reminders)
         (success))))))

(defn handle-put-jifen-coupon [{:keys [aid params]}]
  (let [coupon (:coupon params)
        coupon (when-not (s/blank? coupon)
                 (s/trim coupon))
        {:keys [score id]} (mu/get-jifen-coupon-if-valid coupon)]
    (r/json
     (if (and id (mu/add-coupon {:aid aid :coupon coupon :score score}))
       (success)
       (cds :coupon-exists)))))

(defn handle-get-jifen-coupon [{:keys [aid]}]
  (r/json
   (success (mu/get-coupon-by-aid aid))))

(defn- save-feedback [{{:keys [feedback_content content contact fid]} :params aid :aid headers :headers}]
  (if (s/blank? (or feedback_content content))
    (cds :required-param-not-exist)
    (let [contact (if (phone? contact)
                    contact
                    (when aid
                      (:haier (mu/accountid->account aid))))
          fd {:content (or feedback_content content)
              :contact contact
              :appid (b64-dec->bytes (props :bd-appid))
              :aid aid
              :rtype "client"
              :fid (str->num fid)
              :ua (get headers "user-agent")}]
      (success (mok-call :feedback fd)))))


(def-restricted-routes user-routes
  (POST "/account" req (handle-post-account req))
  (GET "/account" req (handle-get-account req))

  ;; Roles/Members APIs
  ;; addRole
  ;; See https://github.com/weavejester/compojure/issues/88
  (PUT ["/role/:nickname" :nickname #".*"] {{:keys [nickname height birthday sex icon role_type] :as params} :params aid :aid}
       (let [params (dissoc-nil-val params)
             params (assoc params :account_id aid :icon (when (map? icon) (:tempfile icon)) :role_type (str->num role_type))]
         (validate-add-role params)
         (r/json
          (if-let [err (first (v/get-errors))]
            err
            (success (mu/add-role params))))))

  ;; updateRole, partialUpdateRole
  (POST "/role/:roleid" {{:keys [roleid nickname height birthday sex icon weight_goal role_type] :as params} :params aid :aid}
        (let [params (assoc (dissoc-nil-val params) :account_id aid :icon (when (map? icon) (:tempfile icon)) :role_type (str->num role_type))]
          (validate-update-role params)
          (r/json
           (if-let [err (first (v/get-errors))]
             err
             (success (mu/update-role params))))))

  ;; deleteRole
  ;; OLD: Change state from 1(enabled) to 0(disabled)
  ;; NEW: move this role to csb_del_role table. A log trigger will log this action
  (DELETE "/role/:roleid" {{:keys [roleid] :as params} :params aid :aid}
          (let [params (assoc (dissoc-nil-val params) :account_id aid)]
            (validate-delete-role params)
            (r/json
             (if-let [err (first (v/get-errors))]
               err
               (do
                 (mu/move-to-del-role roleid)
                 (success))))))

  ;; findRole
  (GET "/role/:roleid" {{:keys [roleid] :as params} :params aid :aid}
       (r/json
        (if-let [role (mu/get-role {:id roleid :account_id aid :current_state 1})]
          (success role)
          (cds :role-not-exist))))


  (GET "/roles" {aid :aid}
       (r/json
        (success (mu/accountid->roles aid))))

  ;; update weigh reminder by id
  (POST "/config/reminder" req handle-post-reminder)

  (POST "/config/flash/reminder" req handle-post-flash-reminder)

  (POST "/config/unit" {{:keys [length weight] :as params} :params aid :aid}
        (validate-update-units params)
        (r/json
         (if-let [err (first (v/get-errors))]
           err
           (success-no-return (update-account-units (assoc params :id aid))))))


  ;; override mtypes
  (PUT "/config/mtypes" {{:keys [mtypes] :as params} :params aid :aid}
       (let [mtypes (when-not (s/blank? mtypes) (set (s/split mtypes #",")))
             params {:mtypes mtypes :id aid}]
         (validate-put-mtypes params)
         (r/json
          (if-let [err (first (v/get-errors))]
            err
            (success-no-return (update-account-mtypes params))))))

  (PUT "/config/signature" {{:keys [signature] :as params} :params aid :aid}
       (r/json (success-no-return (update-account-by-id {:id aid :signature signature}))))

  (GET "/feedback" {:keys [params] aid :aid}
       (r/json
        (success (mok-call :->feedbacks (assoc params
                                               :aid aid
                                               :fid (str->num (:fid params))
                                               :appid (b64-dec->bytes (props :bd-appid)))))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; ARTICLES
  ;; article likes
  (GET ["/alike/toggle/:bid" :bid #"\d+"] {:keys [params aid]}
       (r/json
        (success-no-return
         (when (pos? (or (str->num (:bid params)) 0))
           (mok-call :toggle-like (assoc params :aid aid))))))

  (GET ["/alike/:bid" :bid #"\d+"] {:keys [params aid]}
       (r/json
        (success (mok-call :liked-by? (assoc params :aid aid)))))

  ;; article favorites 
  (PUT ["/afav/:bid" :bid #"\d+"] req (handle-put-afav req))
  (GET "/afav" req (handle-get-afav req))
  (GET "/afav/count" req (handle-get-afav-count req))
  (DELETE ["/afav/:bid" :bid #"\d+"] req (handle-delete-afav req))

  ;; article comments
  (PUT ["/acomment/:bid" :bid #"\d+"] {:keys [params aid]}
       (r/json
        (if (s/blank? (:content params))
          (cds :emtpy-comment-content)
          (let [rid (:id (aid->first-role aid))
                resp (mok-call :add-comment (assoc params :rid rid))]
            (topup-score {:source :reply-any :aid aid})
            (success resp)))))


  (GET "/app/msg" req handle-get-app-msg)
  (POST "/app/msg" req handle-post-app-msg)
  (DELETE "/app/msg" req handle-delete-app-msg)

  (PUT "/jifen/coupon" req handle-put-jifen-coupon)
  (GET "/jifen/coupon" req handle-get-jifen-coupon)
  (POST "/feedback" req
        (r/json
         (save-feedback req)))
  )
