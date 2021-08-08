(ns chips.routes.validators.users
  (:require
   [cheshire.core :refer [generate-string parse-string]]
   [taoensso.timbre    :as t]
   [noir.validation    :as v]
   [clojure.string     :as s]
   [clojure.java.io    :as io]
   [clojure.data.json  :refer [write-str]]
   [sparrows.misc      :refer [str->num lowercase-trim]]
   [sparrows.cypher    :refer [md5]]
   [sparrows.system    :refer [get-mime]]
   [sparrows.http :refer [async-request]]
   [chips.routes.base  :refer [return-code-with-alt]]
   [chips.utils        :as vu :refer [role-enabled? uid->type cid->signature]]
   [chips.utility :refer [wrap-async]]
   [chips.models.base :refer [all-company-ids haider->status]]
   [chips.models.users :as mu]
   [chips.config       :refer :all]
   [chips.store.rds    :as rds :refer [store-code]]
   [chips.codes        :refer [cds]]
   [chips.open.haier   :as haier]))


(defn cid-valid?
  "A valid company_id should be integer and the company actually exists. "
  [companyid]
  ((set (all-company-ids))  (str->num companyid)))

(defn validate-app-weixin-openid
  "Validate app weixin login 

  https://open.weixin.qq.com/cgi-bin/showdocument?action=dir_list&t=resource/res_list&verify=1&id=open1419317851&token=76a532c171d8380f0183bc17eebbd44e4b7f4195&lang=zh_CN"
  {:arglists '([{:keys [access_token refresh_token openid]}])}
  [{:keys [access_token openid] :as args}]
  (let [{:keys [body status]}
        @(async-request {:url (str "https://api.weixin.qq.com/sns/auth?access_token=" access_token "&openid=" openid)
                         :method :get})]
    (t/info "validate weixin openid" args "returns" body)
    (when (= status 200)
      (zero? (:errcode (parse-string body keyword))))))

(defn validate-post-account-regist
  "Validate register user, called by http/rpc routes"
  [{:keys [uid password vericode company_id]}]
  (let [type (vu/uid->type uid)]
    (and
     (v/rule
      (and uid password company_id)
      [:error (cds :required-param-not-exist)])

     (v/rule
      (cid-valid? company_id)
      [:error (cds :invalid-company-id)])

     (v/rule
      type
      [:error (cds :invalid-uid)])

     (v/rule
      (if (= type :phone)
        (and vericode (= vericode (rds/get-code {:key uid})))
        't)
      [:error (cds :vericode-invalid)])

     (v/rule
      (not (mu/userid->aid {:uid uid :company_id company_id}))
      [:error (cds :register_account_exist)]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Role ops validation
;; TODO validate format of birthday
(defn validate-add-role
  [{:keys [nickname height birthday sex icon weight_goal account_id] :as params}]
  {:pre [account_id]}
  (and
   (v/rule
    (and nickname birthday sex height)
    [:error (cds :required-param-not-exist)])

   ;; TODO validate format of birthday

   (v/rule
    (php-genders sex)
    [:error (cds :invalid-sex)])

   (let [;; all roles including deleted by php
         roles       (mu/get-roles {:account_id account_id})

         ;; all roles enabled
         eroles (filter #(role-enabled? (:current_state %)) roles)

         ;; role exist?
         u           (first (filter #(= nickname (:nickname %)) roles))
         role-exist? (when u (role-enabled? (:current_state u)))]
     (and
      (v/rule
       (not u)
       [:error (if role-exist?
                 (cds :role_already_exist)
                 (cds :role-already-deleted))])

      (v/rule
       (< (count eroles) 8)
       [:error (cds :too-many-roles)])))))


(defn validate-update-role
  [{:keys [nickname height birthday sex icon weight_goal weight_init account_id roleid role_type] :as params}]
  {:pre [account_id roleid]}
  (and
   (v/rule
    (or nickname height birthday sex weight_goal icon weight_init role_type)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (if sex (php-genders sex) 't)
    [:error (cds :invalid-sex)])

   (v/rule
    (= (:account_id (mu/get-role {:id roleid}))
       account_id)
    [:error (cds :role-not-exist)])

   (v/rule
    (if nickname
      (not (mu/get-role {:account_id account_id :nickname nickname}))
      't)
    [:error (cds :role_already_exist)])))

(defn validate-delete-role
  [{:keys [account_id roleid] :as params}]
  {:pre [account_id roleid]}
  (let [roles  (mu/accountid->roles account_id)
        roleid (str->num roleid)
        main   (first roles)]
    (and
     (v/rule
      ;; role exists
      (and main (first (filter #(= (:id %) roleid) roles)))
      [:error (cds :role-not-exist)])
     ;; at least two roles left
     (v/rule
      (not= (:id main) roleid)
      [:error (cds :main-role-delete-not-allowed)]))))


(defn validate-reminder-weight
  [reminders]
  (and
   (v/rule
    (seq reminders)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (every? :id reminders)
    [:error (assoc (cds :required-param-not-exist) :alt :id)])

   (v/rule
    (every? #(re-seq #"\d+:\d+" (:remind_time %)) reminders)
    [:error (assoc (cds :invalid-reminder-time) :alt (write-str reminders :escape-unicode false))])))

(defn validate-update-units
  [{:keys [length weight] :as params}]
  (and
   (v/rule
    (and length weight)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (#{1401 1400} (str->num length))
    [:error (cds :invalid-length-unit)])

   (v/rule
    (#{1401 1400 1402 1403} (str->num weight))
    [:error (cds :invalid-weight-unit)])))

(defn validate-put-mtypes [{:keys [mtypes]}]
  (and
   (v/rule
    (seq mtypes)
    [:error (cds :required-param-not-exist)])
   (v/rule
    (or
     (and (= 1 (count mtypes))
          (= (first mtypes) "all"))
     (every? valid-mtypes (map keyword mtypes)))
    [:error (cds :invalid-mtype)])))

(defn validate-post-account-login
  [{:keys [wdata access_token company_id haier] :as params}]
  (and
   (v/rule
    (and haier company_id)
    [:error (cds :required-param-not-exist)])

   (v/rule
    (cid-valid? company_id)
    [:error (cds :invalid-company-id)])

   (v/rule
    (= (haier/access-token->username access_token) haier)
    [:error (cds :invalid-access-token)])

   (v/rule
    (let [status (haider->status haier)]
      (or (not status) (pos? status)))
    [:error (cds :account-disabled)])))


(defn validate-update-account [{:keys [length_unit weight_unit mtypes signature appbg]}]
  (and
   (v/rule
    (or length_unit weight_unit mtypes signature appbg)
    [:error (cds :required-param-not-exist)])
   (v/rule
    (or (not mtypes)
        (every? (conj valid-mtypes :all) (keyword mtypes)))
    [:error (cds :invalid-mtype)])))
