(ns chips.models.admin
  (:require
   [chips.config         :refer [props]]
   [chips.models.base    :refer :all]
   [chips.store.rds      :as rds]
   [chips.utils          :as vu :refer [convert-sql-val write-icon-file reverse-compare-version compare-version b64-dec->bytes]]
   [chips.utility        :refer [internal-exception-hanlder]]
   [sparrows.cypher :refer :all]
   [sparrows.misc        :refer [dissoc-nil-val wrap-exception str->num]]
   
   [clojure.core.memoize :as memo]
   [clojure.java.jdbc    :as j]
   [clojure.string       :as s]
   [clojure.data.json :refer [read-str]]
   [taoensso.timbre      :as t])
  (:import [java.sql Date Timestamp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UTILITY
(defn- ->binary [uuid]
  (when uuid
    (if (string? uuid)
      (base64-decode uuid :as-bytes? true)
      uuid)))

(defn- ->chips-role [rid]
  (->entity (mysql-db) :csb_role {:id rid}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FEEDBACK 
(defn- insert-feedback
  ([fd]
   (insert-feedback (admin-db) fd))
  ([db {:keys [content appid] :as fd}]
   {:pre [content appid]}
   (get-generated-id (j/insert! db :mkFeedback (-> fd
                                                   (select-keys [:content :ua :contact :appid :auid :aid])
                                                   (assoc :ts (System/currentTimeMillis)))))))


(defn- insert-feedback-reply
  ([fd]
   (insert-feedback-reply (admin-db) fd))
  ([db {:keys [appid adminid auid aid rtype fid content] :as fd}]
   {:pre [appid (or adminid auid aid) content rtype fid]}
   (j/insert! db :mkFeedbackReply (-> fd
                                      (select-keys [:fid :content :ua :rtype :amdinid :auid :aid :appid])
                                      (assoc :ts (System/currentTimeMillis))))))

(defn feedback
  "If `fid` is provided, create a feedback reply, create a feedback otherwise"
  [fd]
  (if-let [fid (:fid fd)]
    (j/with-db-transaction [db (admin-db)]
      (insert-feedback-reply db fd)
      (j/update! db :mkFeedback {:status "open"} ["id=?" fid]))
    (insert-feedback fd)))

(defn fid->replies [fid]
  (map convert-sql-val (j/query (admin-db) ["select * from mkFeedbackReply where fid=? order by id desc" fid])))

(defn auid->feedback [{:keys [auid fid]}]
  {:pre [auid]}
  (let [qstr (str "select * from mkFeedback where auid=?"
                  (when fid " and fid=?")
                  ;;" and status != ?"
                  " order by id desc limit 10")
        qvec (vec (filter identity [qstr auid fid]))]
    (t/info qvec)
    (j/query (admin-db) qvec {:row-fn
                              (fn [fd]
                                (assoc (convert-sql-val fd)
                                       :replies (fid->replies (:id fd))))})))

(defn ->feedbacks
  "auid if not nil, should be a byte-array"
  [{:keys [auid aid fid appid] :as params}]
  {:pre [(or auid (and appid aid))]}
  (let [qstr (str "select * from mkFeedback where 1=1"
                  (when fid " and id =? ")
                  (when auid  " and auid=?")
                  (when appid " and appid=?")
                  (when aid   " and aid=?")
                  ;; " and status != ? "
                  " order by ts desc")
        qvec (vec (filter identity [qstr fid auid appid aid ]))] ;; "closed"
    (t/info qvec)
    (map
     (fn [fd]
       (assoc (convert-sql-val fd)
              :replies (fid->replies (:id fd))))
     (j/query (admin-db) qvec))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; COMPANY & PRODUCT
(defn get-company
  [params]
  (first
   (->entities (mysql-db) :csb_company params)))


(defn get-product
  [{:keys [product_id lang] :as params}]
  {:pre [product_id]}
  (when-let [{:keys [multilang_desc] :as product}
             (first
              (j/query
               (mysql-db)
               ["select p.product_id, p.company_id, coalesce(p.logo_path,c.logo_path) logo_path, p.product_model, product_desc, multilang_desc from csb_company_product p, csb_company c where p.product_id=? and p.company_id=c.id limit 1" product_id]))]
    (let [lmap (when-not (s/blank? multilang_desc)
                 (read-str multilang_desc))]
      (merge
       (dissoc product :multilang_desc)
       (when lmap
         (if lang
           {lang (get lmap lang)}
           lmap))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; APP
(defn applications []
  (map convert-sql-val (j/query (admin-db) ["select * from mkApp"])))

(defn- all-apps
  "All apps with the provided package name on the specified platform"
  [{:keys [platform pkg]}]
  {:pre [platform pkg]}
  (map convert-sql-val (->entities (admin-db) :mkAppRelease {:pkg pkg :platform platform})))

(defn- sort-apps-by-version [apps]
  (sort-by :version reverse-compare-version apps))

(defn get-app-update
  "Returns app-info if a newer version exists"
  [{:keys [platform version pkg]
    :as params}]
  {:pre [pkg platform]}
  (let [app (first (sort-apps-by-version (cons params (all-apps params))))]
    (when-not (= (:version app) version)
      (t/info "updating to" app)
      (let [mu (:mu_version app)]
        (assoc app
               ;; required to update if provided ver. is smaller than minimum usable version
               :required (if (and (not (s/blank? mu))
                                  (compare-version version mu))
                           "y"
                           "n"))))))

(defn latest-app-for [params]
  (first (sort-apps-by-version (all-apps params))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BROADCAST & COMMENTS

(defn broadcast-categories
  "Get all broadcast categories as a list"
  [& [{:keys [appid]}]]
  (if appid
    (j/query (admin-db) ["select c.* from mkCategory c, mkAppCategory a where a.category_id=c.id and a.appid=?" (if (string? appid) (base64-decode appid :as-bytes? true) appid)]
             {:row-fn dissoc-nil-val})
    (j/query (admin-db) ["select * from mkCategory order by id"] {:row-fn dissoc-nil-val})))

;; TODO every broadcast actually has only one category tag
(defn broadcast->categories
  "Get a list of categories by broadcast id"
  [bid]
  (j/query
   (admin-db)
   ["select category_id from mkBroadcastCategory where broadcast_id=?" bid]
   {:row-fn :category_id}))

(defn- attach-category-ids
  [{:keys [id] :as row}]
  (assoc row :categories (s/join "," (broadcast->categories id))))

(defn- likes-count-by-id
  [id]
  (:cnt (first (j/query (admin-db) ["select count(id) cnt from mkLikedArticle where bid =?" id]))))

(def memo-likes-count-by-id
  (memo/ttl likes-count-by-id :ttl/threshold 5000))

(defn- attach-likes-count
  [{:keys [id] :as row}]
  (assoc row :nlikes (memo-likes-count-by-id id)))

(defn- comment-count-by-id
  [id]
  (:cnt (first (j/query (admin-db) ["select count(id) cnt from mkComment where bid =?" id]))))

(def memo-comment-count-by-id
  (memo/ttl comment-count-by-id :ttl/threshold 5000))

(defn- attach-comments-count
  [{:keys [id] :as row}]
  (assoc row :ncomments (memo-comment-count-by-id id)))

(defn- cats->set
  [cats-or-cat]
  (if (sequential? cats-or-cat)
    (when-let [xcat (seq (filter identity (map str->num cats-or-cat)))]
      (set xcat))
    (when-let [c (str->num cats-or-cat)]
      [c])))

(defn get-broadcasts
  [{:keys [sex cnt categories end] :as params}]
  (let [scats (cats->set categories)
        nc (count scats)
        sex   (or (case sex "男" 1 "女" 2 nil) (str->num sex))
        cnt   (or (str->num cnt) 10)
        end   (or (str->num end) (System/currentTimeMillis))
        ;; TODO could be slow
        qstr  (str
               "select distinct b.id,b.buid, title,uri,cover,ts,pv from mkBroadcast b, mkBroadcastCategory c where "
               " ts < ? "
               " and b.id=c.broadcast_id "
               (when sex " and (sex is null or sex =? )")
               (when scats (str " and c.category_id in (" (s/join "," (repeat nc "?")) ") "))
               " order by ts desc  limit ?")
        qlist (filter identity (conj (vec (list* end sex scats)) cnt))
        qvec  (vec (list* qstr qlist))
        transformers (comp
                      (map attach-category-ids)
                      (map attach-likes-count)
                      (map attach-comments-count))]
    (t/info qvec)
    (into [] transformers (j/query (admin-db) qvec))))

(defn get-comments
  [{:keys [bid lastid cnt]}]
  (let [qstr (str "select * from mkComment where bid=? "
                  (when lastid " and id < ? ")
                  " order by id desc limit ?")
        qvec (vec (list* qstr (filter identity [bid lastid (or cnt 10)])))]
    (t/info qvec)
    (j/query (admin-db) qvec {:row-fn dissoc-nil-val})))

(defn get-num-of-comments
  [{:keys [bid]}]
  {:bid bid :ncomments (comment-count-by-id bid)})

(defn get-num-of-likes
  [{:keys [bid]}]
  {:bid bid :nlikes (likes-count-by-id bid)})

(defn add-comment
  [{:keys [rid erid bid content]}]
  {:pre [(or rid erid) bid content]}
  (let [{:keys [r_uuid nickname icon_image_path icon]} (->chips-role rid)
        comment {:bid bid :role_id rid :erid (->binary erid) :commenter_nickname nickname :commenter_icon (or icon_image_path icon) :content content :ts (System/currentTimeMillis)}]
    (get-generated-id (j/insert! (admin-db) :mkComment comment))))

(defn toggle-like
  [{:keys [bid aid eaid]}]
  {:pre [bid (or aid eaid) (not (and aid eaid))]}
  (let [la (dissoc-nil-val {:bid bid :account_id aid :eaid (->binary eaid)})]
    (j/with-db-transaction [db (admin-db)]
      (if (->entity db :mkLikedArticle la)
        (j/delete! db :mkLikedArticle [(str "bid=? and " (if aid "account_id=?" "eaid=?")) bid (or aid (->binary eaid))])
        (j/insert! db :mkLikedArticle la)))))

(defn liked-by?
  [{:keys [bid aid eaid]}]
  {:pre [bid (or aid eaid) (not (and eaid aid))]}
  (:id (->entity (admin-db) :mkLikedArticle (dissoc-nil-val {:bid bid :account_id aid :eaid (->binary eaid)}))))

(defn get-fav [{:keys [aid page cnt]}]
  {:pre [aid]}
  (let [cnt (or (str->num cnt) 10)
        start (* cnt (dec (or (str->num page) 1)))]
    (j/query (admin-db) ["select b.id,title,ts,uri,categories,cover,abstract from mkFavArticle f, mkBroadcast b where account_id=? and f.bid=b.id order by b.id desc limit ?,?" aid start cnt])))
(defn add-fav [{:keys [aid bid]}]
  {:pre [aid]}
  (when-let [bid (str->num bid)]
    (j/insert! (admin-db) :mkFavArticle {:account_id aid :bid bid})))
(defn delete-fav [{:keys [aid bid]}]
  {:pre [aid]}
  (when-let [bid (str->num bid)]
    (j/delete! (admin-db) :mkFavArticle ["account_id=? and bid=?" aid bid])))

(defn count-fav [{:keys [aid]}]
  {:pre [aid]}
  (:cnt (first (j/query (admin-db) ["select count(id) cnt from mkFavArticle where account_id=?" aid]))))

(comment
  (add-fav {:aid 340 :bid 50})
  (delete-fav {:aid 340 :bid 50})
  (get-fav {:aid 340})
  )

(defn add-report [{:keys [aid target_aid mid uid target_uid result type ts] :as report}]
  {:pre [aid target_aid mid uid target_uid result type ts]}
  (j/insert! (admin-db) :mkReport report))

(defn add-pv
  "Add n page view count to article by buid"
  [buid n]
  (j/execute! (admin-db) ["update mkBroadcast set pv=pv+? where buid=?" n (b64-dec->bytes buid)]))

(defn inc-pv-by-path [uri]
  (j/execute! (admin-db) ["update mkBroadcast set pv=pv+1 where uri=?" uri]))


