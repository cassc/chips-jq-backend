(ns chips.models.tutorial
  (:require
   [chips.config         :refer [props]]
   [chips.models.base    :refer :all]

   [clojure.core.memoize :as memo]
   [taoensso.timbre      :as t]
   [clojure.java.jdbc :as j]
   [clojure.string :as s]))

(defn my-fav? [aid {:keys [id]}]
  (first (j/query (mysql-db) ["select * from csb_tutorial_fav where aid=? and tid=? limit 1" aid id])))

(defn- attach-videos [{:keys [id] :as t}]
  (let [videos (->> (j/query (mysql-db) ["select * from csb_tutorial_video where tid=?" id])
                    (sort-by :seq)
                    (mapv :title)
                    (s/join ","))]
    (assoc t :videos videos)))

(defn- -all-tutorials []
  (let [x-tutorial (j/query (mysql-db) ["select * from csb_tutorial order by id desc"])]
    (map attach-videos x-tutorial)))

(def all-tutorials (memo/ttl -all-tutorials :ttl/threshold 600000))

(defn- has-tag? [t-tag {:keys [tag]}]
  (some (partial = t-tag) (s/split tag #",")))

(defn get-my-fav-tutorials [aid]
  (map attach-videos
       (j/query (mysql-db) ["select * from csb_tutorial t, csb_tutorial_fav f where f.aid=? and f.tid=t.id" aid])))

(defn attach-fav [aid tutorials]
  (if aid
    (map (fn [t] (assoc t :fav (if (my-fav? aid t) "y" "n"))) tutorials)
    tutorials))

(defn get-tutorial [{:keys [tag fav aid]}]
  (attach-fav
   aid
   (let [tutorials (if (and (= fav "y") aid)
                     (get-my-fav-tutorials aid)
                     (all-tutorials))]
     (if (s/blank? tag)
       tutorials
       (filter (partial has-tag? tag) tutorials)))))

(defn- -get-tutorial-categories []
  (let [x-tags (j/query (mysql-db) ["select tag from csb_tutorial order by id desc"])]
    (->> x-tags
         (mapcat (fn [{:keys [tag]}] (s/split tag #",")))
         distinct)))

(def get-tutorial-categories (memo/ttl -get-tutorial-categories :ttl/threshold 600000))

(defn update-tutorial-fav [{:keys [aid fav? tid]}]
  {:pre [aid tid]}
  (if fav?
    (try
      (j/insert! (mysql-db) :csb_tutorial_fav {:aid aid :tid tid})
      (catch Exception e))
    (j/delete! (mysql-db) :csb_tutorial_fav ["aid=? and tid=?" aid tid])))

