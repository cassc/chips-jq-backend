(ns chips.models.mblog
  (:require
   [taoensso.timbre :as t]
   [chips.config :refer :all]
   [chips.utils :refer [convert-sql-val exclusive-or super-of? days-before uuid enqueue-merge-circle id->pool]]
   [chips.models.base :refer :all]
   [clojure.java.jdbc :as j]
   [sparrows.misc :refer [get-time-as-string dissoc-nil-val str->num]]
   [clojure.string :as s]
   [com.climate.claypoole :as cp]
   [clojure.core.memoize :as memo]))

(defn last-mblog-sqn [{:keys [account_id]}]
  (or
   (-> (j/query (mysql-db) ["select sqn from csb_mblog where account_id=? and parent_id is null order by id desc limit 1" account_id])
       first
       :sqn)
   0))

(defn following?
  "Returns relation if `target` is followed by `followed_by`"
  [target followed-by]
  (->entity (mysql-db) :csb_relation {:account_id target :follower_id followed-by}))

(defn- liked-by? [bid aid]
  {:pre [bid aid]}
  (first (j/query (mysql-db) ["select id from csb_mblog_liked where mblog_id=? and account_id=? limit 1" bid aid])))

(defn- attach-account-info [{:keys [account_id] :as m}]
  {:pre [account_id]}
  (assoc m :account (aid->info account_id)))

(defn- attach-blog-likes [{:keys [id] :as m}]
  (if-let [likes (seq (j/query (mysql-db) ["select account_id, ts from csb_mblog_liked where mblog_id=?" id]))]
    (assoc m :liked_by (map attach-account-info likes))
    m))

(defn- attach-blog-replies [{:keys [id] :as m}]
  (if-let [replies (seq (j/query (mysql-db) ["select id,account_id,pic,ts,msg from csb_mblog where parent_id=? and hidden='n'" id]))]
    (assoc m :replies (map attach-account-info replies))
    m))

;(defn- attach-blog-like-status [aid {:keys [liked_by account_id] :as m}]
;  (let [like (if (some #(= account_id (:account_id %)) liked_by) "y" (str "n" aid "," (list (map :account_id liked_by)) "," account_id))]
;    (assoc m :like like)))

(defn- attach-blog-like-status [aid {:keys [liked_by account_id] :as m}]
  (let [like (if (some #(= aid (:account_id %)) liked_by) "y" "n")]
    (assoc m :like like)))

(defn- attach-follow-status [aid {:keys [account_id] :as m}]
  (let [f? (following? account_id aid)]
    (assoc m :followed (if f? "y" "n"))))

(defn attach-pics [{:keys [pic pics] :as m}]
  (let [v-pics (when-not (s/blank? pics) (s/split pics #","))
        pic (if (s/blank? pic) (first v-pics) pic)
        pics (if (s/blank? pics) pic pics)]
    (assoc m :pic pic :pics pics)))

(defn get-mblog [{:keys [aid account_id lastid parent_id reply_only id hidden cnt]}]
  (let [cnt (or (str->num cnt) 20)
        qstr (str "select * from csb_mblog where account_id =?"
                  (when id " and id = ? " )
                  (cond
                    parent_id " and parent_id = ?"
                    reply_only " and parent_id is not null "
                    :else " and parent_id is null ")
                  (when hidden " and hidden=? ")
                  (when lastid " and id<? ")
                  " order by id desc limit ? ")
        qvec (filter identity [qstr account_id id parent_id  hidden lastid cnt])]
    (t/info (vec qvec))
    (map
     (comp
      attach-pics
      attach-account-info
      (if (or parent_id reply_only) identity attach-blog-replies)
      (if (or parent_id reply_only) identity (partial attach-blog-like-status aid))
      (if (or parent_id reply_only) identity (partial attach-follow-status aid))
      (if (or parent_id reply_only) identity attach-blog-likes))
     (j/query (mysql-db) qvec))))

(defn- qvec-to-comp-mblogs [qvec aid]
  (t/info (vec qvec))
  (map
   (comp
    attach-account-info
    attach-blog-replies
    (partial attach-blog-like-status aid)
    (partial attach-follow-status aid)
    attach-blog-likes)
   (j/query (mysql-db) qvec)))

(defn get-pop-mblog [{:keys [cnt page aid]}]
  (let [cnt (or cnt 20)
        page (or page 1)
        start (* (dec page) cnt)
        qstr (str "select * from csb_mblog where ispop=1 and hidden=? order by pop_weight desc limit ?,?")
        qvec [qstr "n" start cnt]]
    (qvec-to-comp-mblogs qvec aid)))

(defn get-mblog-by-actid [{:keys [actid lastid cnt aid]}]
  {:pre [actid]}
  (let [lastid (or lastid (Integer/MAX_VALUE))
        cnt    (or cnt 20)
        qstr   (str "select * from csb_mblog "
                    " where act_id=? and id<? "
                    " and parent_id is null and hidden=? order by id desc limit ?")
        qvec   [qstr actid lastid "n" cnt]]
    (qvec-to-comp-mblogs qvec aid)))

(defn get-mblog-following [{:keys [lastid cnt aid]}]
  (let [lastid (or lastid (Integer/MAX_VALUE))
        cnt (or cnt 20)
        qstr (str "select * from ("
                  "select m.* from csb_mblog m, csb_relation r "
                  "where r.follower_id=? and r.account_id=m.account_id and m.hidden='n'"
                  "union "
                  "select * from csb_mblog where account_id = ?) m "
                  "where m.id<? and m.parent_id is null "
                  "order by m.id desc limit ?")
        qvec [qstr aid aid lastid cnt]]
    (qvec-to-comp-mblogs qvec aid)))

(defn get-mblog-replies [{:keys [no-hide-for parent_id]}]
  (j/query (mysql-db) ["select * from csb_mblog where parent_id=? and (hidden=? or account_id=?) order by id" parent_id "n" no-hide-for]))

(defn delete-mblog [{:keys [id aid]}]
  (j/delete! (mysql-db) :csb_mblog ["id=? and account_id=?" id aid]))

(defn- topup-score-by-mblog [{:keys [parent_id pic pics pic_size hidden msg account_id act_id tag] :as mblog}]
  (if parent_id
    (topup-score {:source :reply-any :aid account_id})
    (if (and (s/blank? pic) (s/blank? pics))
      (topup-score {:source :mblog-no-image :aid account_id})
      (topup-score {:source :mblog-with-image :aid account_id}))))

(defn insert-mblog [{:keys [parent_id pic pics pic_size hidden msg account_id act_id tag] :as mblog}]
  {:pre [account_id]}
  (let [sqn (if parent_id 0 (inc (last-mblog-sqn {:account_id account_id}))) 
        id (get-generated-id
            (j/insert!
             (mysql-db)
             :csb_mblog
             (-> mblog
                 (select-keys [:parent_id :pic :pics :pic_size :hidden :msg :account_id :act_id :tag])
                 (assoc :ts (System/currentTimeMillis) :sqn sqn))))]
    (topup-score-by-mblog mblog)
    {:mblog_id id :sqn sqn}))

(defn update-mblog [{:keys [id pic pic_size hidden msg account_id] :as mblog}]
  {:pre [account_id id]}
  (j/update! (mysql-db) :csb_mblog (select-keys mblog [:pic :pics :pic_size :hidden :msg]) ["id=? and account_id=?" id account_id]))

(defn follow [target followed-by]
  {:pre [target followed-by]}
  (let [relation {:account_id target :follower_id followed-by :ts (System/currentTimeMillis) :gid (uuid)}]
    (try
      (let [id (get-generated-id (j/insert! (mysql-db) :csb_relation relation))]
        (enqueue-merge-circle {:id id :from followed-by :to target})
        id)
      (catch Exception e
        ;; can only happen when user not exists or duplicate follow so we ignore these error quietly
        (when-not (s/includes? (.getMessage e) "Duplicate")
          (t/warn "Ignore follow" target followed-by "error" (.getMessage e)))))))

(defn unfollow [target followed-by]
  {:pre [target followed-by]}
  (j/delete! (mysql-db) :csb_relation ["account_id=? and follower_id=?" target followed-by]))

(defn- ->follower
  "Set `follow?` to true to get a list of followers, otherwise get a
  list of users this user is following"
  [{:keys [account_id page follow? cnt]}]
  {:pre [account_id page]}
  (let [cnt (or cnt (Integer/MAX_VALUE))
        start (* 10 (dec page))
        qstr (str "select "
                  (if follow? "follower_id" "account_id")
                  " aid, ts from csb_relation where "
                  (if follow? "account_id" "follower_id")
                  "=? limit ?,?")]
    (map #(assoc (aid->info (:aid %)) :ts (:ts %))
         (j/query (mysql-db) [qstr account_id start cnt]))))

(defn- ->follower-ids [follow? account_id]
  (let [qstr (str "select "
                  (if follow? "follower_id" "account_id")
                  " aid, ts from csb_relation where "
                  (if follow? "account_id" "follower_id")
                  "=?")]
    (set (mapv :aid (j/query (mysql-db) [qstr account_id])))))

(def -all-follower-ids (partial ->follower-ids true))

(def -all-following-ids (partial ->follower-ids false))

(def all-follower-ids -all-follower-ids ;; (memo/ttl -all-follower-ids :ttl/threshold 5000)
  )

(def all-following-ids -all-following-ids ;;(memo/ttl -all-following-ids :ttl/threshold 5000)
  )

(defn my-follow-status [xid me]
  (if (= xid me)
    5
    (let [me-following-ids (all-following-ids me)
          me-follower-ids (all-follower-ids me)]
      (cond
        (and (me-follower-ids xid) (me-following-ids xid)) 4
        (me-following-ids xid) 3
        (me-follower-ids xid) 2
        :else 1))))

(defn get-followers
  "Get users following this account"
  [{:keys [account_id page cnt me]}]
  (let [me (or me account_id)
        following-ids (all-following-ids account_id) ;; ids following of account_id
        followers (->follower {:account_id account_id :page page :follow? true :cnt cnt})]
    (mapv (fn [user]
            (let [xid (:account_id user)]
              (assoc user
                     :mutual (if (following-ids xid) "y" "n")
                     :follow_status (my-follow-status xid me))))
          followers)))

(defn get-following
  "Get users this account is following"
  [{:keys [account_id page cnt me]}]
  (let [me (or me account_id)
        following (->follower {:account_id account_id :page page :follow? false :cnt cnt})
        followers-ids (all-following-ids account_id)]
    (mapv (fn [user]
            (let [xid (:account_id user)]
              (assoc user
                     :mutual (if (followers-ids xid) "y" "n")
                     :follow_status (my-follow-status xid me))))
          following)))

(defn merge-circle [{:keys [id from to]}]
  (let [;; ids in circle
        [a b c d]
        (distinct
         (map
          :gid
          (j/query
           (mysql-db)
           [(str "(select gid from csb_relation where account_id = ? and id != ? limit 2)"
                 " union all "
                 "(select gid from csb_relation where follower_id = ? and id != ? limit 2)"
                 " union all "
                 "(select gid from csb_relation where account_id = ? and id != ? limit 2)"
                 " union all "
                 "(select gid from csb_relation where follower_id = ? and id != ? limit 2)")
            from id to id to id from id])))
        update-when (fn [db x]
                      (when x (j/update! db :csb_relation {:gid a} ["gid=?" x])))]
    (when a
      (j/with-db-transaction [db (mysql-db)]
        (run! (partial update-when db) [b c d])
        (j/update! db :csb_relation {:gid a} ["id=?" id])))))


(defn circle-users [{:keys [account_id]}]
  (when-let [gid (:gid (->entity (mysql-db) :csb_relation {:account_id account_id}))]
    (let [circle-aids (mapcat (juxt :account_id :follower_id) (->entities (mysql-db) :csb_relation {:gid gid}))
          following (set (map :account_id (->entities (mysql-db) :csb_relation {:gid gid :follower_id account_id})))]
      (get-public-account (distinct (remove following circle-aids))))))


(defn- -get-recommend-users [{:keys [company_id account_id] :as params}]
  {:pre [company_id account_id]}
  (let [following (set (map :account_id (get-following {:account_id account_id :page 1 :cnt 10})))]
    (->> (most-active-users-from-cache company_id)
         (remove (comp following :account_id))
         (concat (circle-users {:account_id account_id}))
         (remove #(= (:account_id %) account_id))
         distinct)))

(def get-recommend-users (memo/ttl -get-recommend-users :ttl/threshold 30000))

(comment
  (follow 775 1)
  (follow 1 773)
  (follow 1 778)
  (follow 1 775)
  (follow 66 779)
  (follow 566 1)
  (follow 5 1282)
  (get-recommend-users {:company_id 1 :account_id 14})
  (get-followers {:account_id 1 :page 1})
  (get-following {:account_id 7 :page 1}))

(defn get-most-recent-mblogs [{:keys [company_id account_id page]}]
  (let [page (or page 1)
        size (props :moments-pagesize)
        index (* (dec page) size)
        qstr (str
              "select * from ("
              "select m.* from csb_mblog m, csb_account a where a.company_id=? and m.account_id=a.id  "
              " and (m.hidden='n' or (m.hidden='y' and a.id=?)) "
              " and m.parent_id is null"
              ") mb order by mb.weight desc,mb.id desc limit ?,?")
        qvec (vec (filter identity [qstr company_id account_id index size]))]
    (t/info qvec)
    (j/query (mysql-db) qvec)))

(defn- -get-mblog-moments [{:keys [company_id account_id page] :as params}]
  {:pre [company_id account_id]}
  (t/info "get-mblog-moments" params)
  (cp/pmap
   (id->pool :moments-pool)
   (comp
    attach-pics
    attach-account-info
    attach-blog-replies
    (partial attach-blog-like-status account_id)
    (partial attach-follow-status account_id)
    attach-blog-likes)
   (get-most-recent-mblogs params)))

(def get-mblog-moments (memo/ttl -get-mblog-moments :ttl/threshold 30000))

(comment
  (get-mblog-moments {:company_id 1 :account_id 16})
  )

(def id->blog 
  (memo/ttl
   (fn [id] (->entity (mysql-db) :csb_mblog {:id id}))
   :ttl/threshold 30000))

(defn get-mblog-like [{:keys [account_id mblog_id lastid cnt mblog_id]}]
  (let [cnt (or (str->num cnt) 10)
        lastid (str->num lastid)
        qstr (str "select m.id mblog_id, m.pic, m.ts, m.sqn, m.msg,l.ts like_ts, l.id, l.account_id from csb_mblog_liked l, csb_mblog m where m.account_id=? "
                  (when mblog_id " and m.id=? ")
                  " and m.id =l.mblog_id "
                  (when lastid " and l.id<? ")
                  " order by l.id desc limit ?")
        qvec (filter identity [qstr account_id mblog_id lastid cnt])]
    (map
     (fn [{:keys [account_id mblog_id] :as blog}]
       (assoc blog :liked_by (aid->info account_id)))
     (j/query (mysql-db) qvec))))

(defn toggle-mblog-like [{:keys [account_id id]}]
  {:pre [account_id id]}
  (try
    (let [blog (id->blog id)]
      (j/insert! (mysql-db) :csb_mblog_liked {:account_id account_id :mblog_id id :ts (System/currentTimeMillis)}))
    (catch Exception e
      (j/delete! (mysql-db) :csb_mblog_liked ["account_id=? and mblog_id=?" account_id id]))))

(comment
  (get-mblog-like {:account_id 566 :lastid nil :cnt 10})
  )

(defn get-received-replies [{:keys [lastid account_id cnt hidden hide-self-reply?]}]
  (let [cnt (or (str->num cnt) 20)
        lastid (str->num lastid)
        qstr (str "select b.id mblog_id, b.msg mblog_msg, b.pic mblog_pic, r.* from csb_mblog b, csb_mblog r where b.account_id=? and r.parent_id=b.id "
                  (when lastid " and r.id<? ")
                  (when hidden " and b.hidden=? ")
                  (when hide-self-reply? " and r.account_id!=? ")
                  " order by r.id desc limit ?")
        qvec (filter identity [qstr account_id lastid hidden (when hide-self-reply? account_id) cnt])]
    (t/info (vec qvec))
    (map
     attach-account-info
     (j/query (mysql-db) qvec))))

(comment
  (get-received-replies {:account_id 16})
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; banner

(defn get-banner-list []
  (j/query (mysql-db) ["select * from csb_banner order by pos asc"]))

(defn get-activity-list []
  (j/query (mysql-db) ["select A.*,ifnull(B.count,0) mcount from csb_act A left join (
  select act_id,count(1) count from csb_mblog where parent_id is null group by act_id) B
   on A.id = B.act_id
   where A.status = 'online' order by A.position desc"]))
