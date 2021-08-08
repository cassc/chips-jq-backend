(ns chips.models.data
  (:require
   [taoensso.timbre :as timbre]
   [clojure.java.io :as io]
   [clojure.core.memoize :as memo]
   [com.climate.claypoole :as cp]
   [chips.config :refer :all]
   [chips.utils :refer [reverse-compare-version compare-version convert-sql-val id->pool redef-with-timer]]
   [chips.utility :refer [internal-exception-hanlder]]
   [chips.models.base :refer :all]
   [clojure.java.jdbc :as j]
   [sparrows.cypher :refer :all]
   [sparrows.misc :refer [str->num]]
   [clojure.string :as s]
   [clojure.data.json :refer [read-str]])
  (:import
   [java.util.zip  GZIPInputStream]))

(timbre/refer-timbre)

(defn get-company
  [params]
  (first
   (->entities (mysql-db) :csb_company params)))

(defn- all-apps [{:keys [companyid platform region server]
                  :or   {region "china"}}]
  {:pre [companyid platform region server]}
  (->entities (mysql-db) :csb_app_info {:server server
                                        :company_id companyid
                                        :system_version platform
                                        :region region}))

(defn- sort-apps-by-version [apps]
  (sort-by :version reverse-compare-version apps))

(defn get-app-update
  "Returns app-info if a newer version exists"
  [{:keys [companyid platform version sdk region server]
    :or   {region "china"}
    :as params}]
  {:pre [server]}
  (let [app (first (sort-apps-by-version (cons params (all-apps params))))]
    (when-not (= (:version app) version)
      (info "updating to" app)
      (let [mu (:mu_version app)]
        (assoc app
               ;; required to update if provided ver. is smaller than minimum usable version
               :required (if (and (not (s/blank? mu))
                                  (compare-version version mu))
                           "y"
                           "n"))))))

(defn latest-app-for [params]
  (first (sort-apps-by-version (all-apps params))))

(comment
  (get-app-update {:companyid 1 :platform "android" :version "1.4.5" :region "china" :server "chips"}))

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

(defn update-reminders
  "Update reminders by id and account_id"
  [reminders]
  (letfn [(remind-updater [db]
            (fn [{:keys [id account_id] :as r}]
              (update! db :csb_weight_remind (dissoc r :id :account_id) ["id=? and account_id=?" id account_id])))]
    (j/with-db-transaction [db (mysql-db)]
      (dorun (map (remind-updater db) reminders)))))

(redef-with-timer update-reminders)

(defn update-flash-reminders
  [reminders]
  (letfn [(remind-updater [db]
            (fn [{:keys [id account_id] :as r}]
              (update! db :csb_flash_remind (dissoc r :id :account_id) ["id=? and account_id=?" id account_id])))]
    (j/with-db-transaction [db (mysql-db)]
      (dorun (map (remind-updater db) reminders)))))

(defn update-account-by-id [{:keys [id] :as m}]
  {:pre [id]}
  (update! (mysql-db) :csb_account (dissoc m :id) ["id=?" id]))

(defn update-account-units
  [{:keys [length weight id]}]
  {:pre [length weight]}
  (update-account-by-id {:Length_unit length :weight_unit weight :id id}))

(defn update-account-mtypes
  "`mtypes` is a collection of mtype"
  [{:keys [mtypes id]}]
  {:pre [(seq mtypes) id]}
  (update-account-by-id {:mtypes (s/join "," (map name mtypes)) :id id}))


(defn save-logs [logs]
  (j/insert-multi! (mysql-db) :csb_api_log logs))

(defn- header-line? [t]
  (when-not (s/blank? t)
    (and (s/includes? t "method")
         (s/includes? t "uri")
         (s/includes? t "start")
         (s/includes? t ";"))))

(defn parse-and-save-log [{:keys [deviceid aid]} file]
  (with-open [reader (io/reader (GZIPInputStream. (io/input-stream file)))]
    (let [[first-line & rest-lines :as all-lines] (line-seq reader)
          wheader? (header-line? first-line)
          header-line (if wheader? first-line "method;uri;start;time;msg")
          body-lines (if wheader? rest-lines all-lines)]
      (info first-line) ;; GET;/feedback;1480489725077;81;
      (when (seq body-lines)
        (let [keys (map keyword (s/split header-line #";"))]
          (doseq [lines (partition-all 100 body-lines)]
            (save-logs (map (fn [line]
                              (assoc
                               (zipmap keys (s/split line #";"))
                               :deviceid deviceid
                               :aid aid))
                            lines))))))))

(comment
  (parse-and-save-log {:aid 1 :deviceid "888888"} (io/file "resources" "161212202113.log.gz"))
  (parse-and-save-log {:aid 2 :deviceid "888888"} (io/file "resources" "161129222520.log.gz"))
  
 )
