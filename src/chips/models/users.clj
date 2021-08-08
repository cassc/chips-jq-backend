(ns chips.models.users
  (:require
   [chips.config         :refer [props]]
   [chips.models.base    :refer :all]
   [chips.store.rds      :as rds]
   [chips.utils          :as vu :refer [convert-sql-val write-icon-file]]
   [chips.utility        :refer [internal-exception-hanlder]]
   [clojure.core.memoize :as memo]
   [clojure.java.jdbc    :as j]
   [clojure.string       :as s]
   [sparrows.misc        :refer [dissoc-nil-val wrap-exception str->num]]
   [taoensso.timbre      :as timbre])
  (:import [java.sql Date Timestamp]))

(timbre/refer-timbre)

(defn userid->account
  "Convert uid(email/phone), qq, sina_blog and company_id to account. Returns nil if
  the account is not exists."
  ([params]
   (userid->account (mysql-db) params))
  ([db {:keys [uid company_id qq weixin haier sina_blog login?] :as params}]
   {:pre [(or uid qq sina_blog weixin haier) company_id]}
   (let [clean-row (fn [m]
                     (convert-sql-val
                      (if login?
                        m
                        (dissoc m :password))))
         qstr (cond
                ;; login by uid, validate uid/password
                uid
                (when-let [type (and uid (vu/uid->type uid))]
                  (str "select * from csb_account where "
                       (name type)
                       "=? and company_id=? limit 1"))

                (or qq sina_blog weixin haier)
                (str "select * from csb_account where "
                     (cond qq "qq" sina_blog "sina_blog" weixin "weixin" haier "haier")
                     "=? and company_id=? limit 1"))]
     (when qstr
       (first
        (j/query db [qstr (or uid qq sina_blog weixin haier) company_id] {:row-fn clean-row}))))))

(defn userid->aid
  ([params]
   (userid->aid (mysql-db) params))
  ([db params]
   (some-> (userid->account db params) :id)))

(defn accountid->roles
  "Get active roles for this account"
  [aid]
  {:pre [aid]}
  (j/query
   (mysql-db)
   ["select * from csb_role where account_id=? and current_state=? order by id asc" aid 1]
   {:row-fn vu/convert-sql-val}))

(defn accountid->remind
  ([aid]
   (accountid->remind (mysql-db) aid))
  ([db aid]
   {:pre [aid]}
   (j/query
    db
    ["select * from csb_weight_remind where account_id=?" aid]
    {:row-fn vu/convert-sql-val})))


(defn accountid->account
  ([aid]
   (accountid->account (mysql-db) aid))
  ([db aid]
   {:pre [aid]}
   (first
    (j/query
     db
     ["select haier,weixin,email,phone,last_login,days,weight_unit,company_id,qq,sina_blog,grade_id,register_time,id,length_unit,mtypes,signature,status,appbg  from csb_account where id=? limit 1" aid]
     {:row-fn vu/convert-sql-val}))))


(defn accountid->companyid
  "account_id to company_id conversion.

  Call aid->cid for cached version instead"
  ([aid]
   (first
    (j/query
     (mysql-db)
     ["select company_id from csb_account where id=? limit 1" aid] {:row-fn :company_id}))))

(defn update-account [{:keys [length_unit weight_unit mtypes signature appbg aid] :as m}]
  {:pre [aid (or length_unit weight_unit mtypes signature appbg)]}
  (info "update-account" m)
  (let [mtypes (when mtypes
                 (if (sequential? mtypes)
                   (s/join "," mtypes)
                   mtypes))
        m (if mtypes (assoc m :mtypes mtypes) m)]
    (j/update! (mysql-db) :csb_account (select-keys m [:length_unit :weight_unit :mtypes :signature :appbg]) ["id=?" aid])))

;; account_id -> company_id conversion
(def aid->cid
  (memo/lru accountid->companyid :lru/threshold 200))


(defn insert-account
  [db {:keys [qq sina_blog weixin company_id haier password register_time last_login] :as params}]
  {:pre [(or qq sina_blog weixin haier) company_id]}
  (insert!
   db
   :csb_account
   {:qq         (or qq "")
    :sina_blog  (or sina_blog "")
    :weixin     weixin
    :haier      haier
    :mtypes     (if (= 1 (str->num company_id))
                  "food,exercise,weight"
                  "weight")
    :company_id company_id
    :password   (or password "")
    :register_time (or register_time (java.sql.Timestamp. (System/currentTimeMillis)))
    :last_login (or last_login (java.sql.Date. (System/currentTimeMillis)))}))

(defn update-last-login
  "Update last_login for by account->id"
  ([account]
   (update-last-login (mysql-db) account))
  ([db {:keys [id last_login] :or {last_login (Date. (System/currentTimeMillis))}}]
   {:pre [id]}
   (update! db :csb_account {:last_login last_login} ["id=?" id])))

(defn insert-reminders
  [db reminders]
  (insert-multi! db :csb_weight_remind reminders))

(defn create-haier-account [{:keys [haier company_id] :as params}]
  (timbre/info "create-haier-account" params)
  {:aid (get-generated-id
         (insert-account (mysql-db) {:haier haier :company_id company_id}))})

(defn get-flash-remind [aid]
  (j/query (mysql-db) ["select * from csb_flash_remind where account_id=? order by id asc" aid]))

(defn init-flash-remind [aid]
  (let [base-reminder {:account_id aid :once_open 0 :is_open 0
                       :mon_open 1 :tue_open 1 :wed_open 1 :thu_open 1 :sun_open 1
                       :fri_open 1 :sat_open 1}
        reminders     [(assoc base-reminder :remind_time "8:00")
                       (assoc base-reminder :remind_time "12:00")
                       (assoc base-reminder :remind_time "17:00")]]
    (j/insert-multi! (mysql-db) :csb_flash_remind reminders)))

(defn load-flash-remind [aid]
  (or (seq (get-flash-remind aid))
      (do
        (init-flash-remind aid)
        (get-flash-remind aid))))

(defn login-user
  "Login by email/phone/qq/sina_blog.

  Validate params before calling this function."
  [{:keys [uid password company_id wdata haier] :as params}]
  {:pre [company_id]}
  (let [account (userid->account (assoc params :login? true))
        retrieve-account (fn [{:keys [id] :as account}]
                           (if (s/blank? (str wdata))
                             {:account (dissoc account :password)}
                             {:account (dissoc account :password)
                              :role    (accountid->roles id)
                              :remind  (accountid->remind id) }))
        m-account (if account
                    (retrieve-account account)
                    (let [{:keys [aid]}  (create-haier-account params)]
                      (j/with-db-transaction [db (mysql-db)]
                        (let [base-reminder {:account_id aid :is_open 0}
                              reminders     [(assoc base-reminder :remind_time "8:00")
                                             (assoc base-reminder :remind_time "12:00")
                                             (assoc base-reminder :remind_time "17:00")]]
                          (insert-reminders db reminders)))
                      ;; move the query out of transaction to reduce table lock time
                      {:account (accountid->account aid)
                       :remind  (accountid->remind aid)}))]
    (assoc m-account :flash_remind (load-flash-remind (get-in m-account [:account :id])))))

(defn add-user
  "Valiation should have been performed before calling this method"
  ([params]
   (add-user (mysql-db) params))
  ([db {:keys [uid password company_id] :as params}]
   {:pre [uid password company_id]}
   (let [account    {(vu/uid->type uid) uid
                     :password          password
                     :company_id        company_id
                     :qq                ""
                     :sina_blog         ""
                     :mtypes            (if (= 1 (str->num company_id))
                                          "food,exercise,weight"
                                          "weight")
                     :signature         ""
                     :last_login        (Date. (System/currentTimeMillis))}

         aid
         (j/with-db-transaction [db (mysql-db)]
           (let [aid           (get-generated-id (insert! db :csb_account account))
                 base-reminder {:account_id aid :is_open 0}
                 reminders     [(assoc base-reminder :remind_time "8:00")
                                (assoc base-reminder :remind_time "12:00")
                                (assoc base-reminder :remind_time "17:00")]
                 _             (insert-reminders db reminders)]
             aid))]
     {:aid     aid
      :account (accountid->account aid)
      :remind  (accountid->remind aid)})))

(defn pwdreset
  "Reset password by email or phone."
  [{:keys [uid password vericode company_id] :as params}]
  {:pre [uid password vericode company_id]}
  (let [type (vu/uid->type uid)]
    (update!
     (mysql-db)
     :csb_account
     {:password password}
     [(str (name type) "=? and company_id=?") uid company_id])
    (rds/remove-code {:key uid})))

(defn provider-pwdreset
  [{:keys [uid password vericode company_id] :as params}]
  {:pre [uid password vericode company_id]}
  (let [type (vu/uid->type uid)
        okok-upd-str (str "update csb_account set password=? where " (name type) "=? and company_id=?")
        ;;wp-upd-str (str "update wp.wp_users set user_pass='' where user_login=?")
        ]
    (j/with-db-transaction [db (mysql-db)]
      (execute! db [okok-upd-str password uid company_id])
      ;;(j/execute! db [wp-upd-str uid])
      )
    (rds/remove-code {:key uid})))

(defn- bind-phone
  [{:keys [phone password aid] :as params}]
  {:pre [aid]}
  (update!
   (mysql-db)
   :csb_account
   (dissoc-nil-val {:phone    phone
                    :password password})
   ["id=?" aid])
  (rds/remove-code {:key phone}))

(defn- bind-openid
  [{:keys [qq sina_blog aid weixin] :as params}]
  {:pre [aid (not (and qq sina_blog weixin))]}
  (update!
   (mysql-db)
   :csb_account
   (select-keys params [:qq :sina_blog :weixin])
   ["id=?" aid]))

(defn bind
  [{:keys [phone qq sina_blog weixin password vericode aid] :as params}]
  {:pre [aid (or phone qq sina_blog weixin)]}
  (cond
    phone (bind-phone params)
    :else (bind-openid params)))

(defn unbind
  [aid type]
  {:pre [aid (#{:qq :sina_blog :weixin} type)]}
  (update!
   (mysql-db)
   :csb_account
   {type ""}
   ["id=?" aid]))

(defn get-account
  [{:keys [id password]}]
  (first
   (j/query (mysql-db) ["select password from csb_account where id=? and password=?" id password] {:row-fn :password})))

(defn update-pwd
  [{:keys [aid password]}]
  (update! (mysql-db) :csb_account {:password password} ["id=?" aid]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roles
(defn add-role
  ([params]
   (add-role (mysql-db) params))
  ([db {:keys [nickname height birthday sex account_id icon weight_goal role_type create_time] :as params}]
   {:pre [nickname account_id birthday sex height]}
   (info "Add role: " params)
   (let [icon (write-icon-file icon)
         role {:account_id      account_id
               :nickname        nickname
               :birthday        birthday
               :sex             sex
               :height          height
               :icon_image_path (or icon "")
               :weight_goal     weight_goal
               :role_type       (or role_type 0)
               :create_time (or create_time (java.sql.Timestamp. (System/currentTimeMillis)))}]
     (get-generated-id (insert! db :csb_role role)))))

(defn get-roles
  [params]
  (->entities (mysql-db) :csb_role params))

(defn get-role
  "Get the first role matching the condition"
  [params]
  (->entity (mysql-db) :csb_role params))



(defn update-role
  "Update role by role_id, `icon` is a file or inputstream"
  [{:keys [nickname height birthday sex icon weight_goal weight_init roleid role_type] :as params}]
  {:pre [roleid]}
  (let [icon (write-icon-file icon)
        role {:nickname        nickname
              :birthday        birthday
              :sex             sex
              :height          height
              :icon_image_path icon
              :modify_time     (Timestamp. (System/currentTimeMillis))
              :weight_init     weight_init
              :weight_goal     weight_goal
              :role_type       role_type}
        role (dissoc-nil-val role)]
    (update! (mysql-db) :csb_role role ["id=?" roleid])
    (convert-sql-val role)))

(defn move-to-del-role
  "Move this role and its data to del tables. "
  [roleid]
  {:pre [roleid]}
  (info "Deleting role with id" roleid)
  (letfn [(move-data [db src target]
            (execute! db [(str "delete from " src " where role_id =? ") roleid]))]
    (j/with-db-transaction [db (mysql-db)]
      (execute! db ["delete from csb_mdata where role_id=?" roleid])
      (move-data db "csb_weight" "csb_del_weight")
      (move-data db "csb_bp" "csb_del_bp")
      (move-data db "csb_bsl" "csb_del_bsl")
      (execute! db ["insert csb_del_role select * from csb_role where id=? " roleid])
      (execute! db ["delete from csb_role where id=? " roleid]))))

(defn clean-disabled-roles
  "Move all roles with current_state=0 to deleted tables. And change the current_status to 0"
  []
  (let [role-ids (j/query (mysql-db) ["select id from csb_role where current_state=?" 0] {:row-fn :id})]
    (doall (map move-to-del-role role-ids))
    (execute! (mysql-db) ["update csb_del_role set current_state=0"])))

(defn uid->public-info
  "phone/email to public info of this account"
  [{:keys [uid company_id]}]
  {:pre [uid company_id]}
  (when-let [type (and uid (vu/uid->type uid))]
    (when-first [account (j/query
                          (mysql-db)
                          [(str "select "
                                "email,phone,last_login,days,weight_unit,company_id,qq,sina_blog,grade_id,register_time,id,length_unit"
                                " from csb_account where "
                                (name type)
                                "=? and company_id=? limit 1") uid company_id]
                          {:row-fn convert-sql-val})]
      (assoc account :roles (accountid->roles (:id account))))))


(defn get-jifen-list [{:keys [aid lastid cnt]}]
  {:pre [aid lastid cnt]}
  (j/query (mysql-db) ["select * from csb_jifen where account_id=? and id<? order by id desc limit ?" aid lastid cnt]))

(defn get-jifen [aid]
  (or
   (-> (j/query (mysql-db) ["select sum(score) score from csb_jifen where account_id=?" aid])
       first
       :score)
   0))

(defn get-msg [aid status]
  {:pre [aid]}
  (if status
    (j/query (mysql-db) ["select m.id id, m.id mid, m.msg, m.title, m.img, m.ts, ifnull(s.status, 'unread') status from m_msg m left join m_status s on m.id=s.mid and s.aid=? where m.aid=? or m.aid=0 having status=? order by m.id desc" aid aid status])
    (j/query (mysql-db) ["select m.id id, m.id mid, m.msg, m.title, m.img, m.ts, ifnull(s.status, 'unread') status from m_msg m left join m_status s on m.id=s.mid and s.aid=? where m.aid=? or m.aid=0 having status!=? order by m.id desc" aid aid "delete"])))

(defn update-msg [{:keys [aid status mid]}]
  {:pre [aid mid status]}
  (j/with-db-transaction [db (mysql-db)]
    (if-let [{:keys [id]} (->entity db :m_status {:mid mid :aid aid})]
      (j/update! db :m_status {:status status} ["id=?" id])
      (j/insert! db :m_status {:status status :mid mid :aid aid}))))

(defn delete-msg [{:keys [mid aid]}]
  {:pre [mid aid]}
  (update-msg {:aid aid :mid mid :status "delete"}))

(defn add-coupon [{:keys [aid coupon score] :as params}]
  {:pre [aid coupon]}
  (try
    (j/insert! (mysql-db) :csb_jifen_coupon {:coupon coupon
                                             :aid aid
                                             :score score
                                             :status "pending"
                                             :ts (System/currentTimeMillis)
                                             :reason ""})
    (catch Exception e
      (when-not (s/includes? (or (.getMessage e) "") "Duplicate")
        (throw e)))))

(defn get-coupon-by-aid [aid]
  {:pre [aid]}
  (j/query (mysql-db) ["select * from csb_jifen_coupon where aid=? order by id desc" aid]))

(defn get-jifen-coupon-if-valid [coupon]
  (when-let [c (->entity (mysql-db) :j_jifen_coupon {:coupon coupon})]
    (when (= "valid" (:status c))
      c)))
