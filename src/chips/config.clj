(ns chips.config
  (:require
   [clojure.java.io :as io]
   [clj-props.core :refer [defconfig]]
   [clojure.core.async :refer [chan sliding-buffer]]
   [clojure.pprint  :refer [pprint]]
   [clj-time.core   :as tt]
   [clj-time.format :as tf]))

(def cfg
  (or (.get (System/getenv) "CHIPS_CONFIG") "config-chips.edn"))

(def aes-key
  (or (.get (System/getenv) "AES_KEY") "ironmanrocks"))

(def bj-timezone
  (tt/time-zone-for-id "Asia/Shanghai"))

(defconfig props (io/file cfg) {:secure false})

;; php DB-enforced
(def php-genders
  #{"男" "女"})

(defonce shutting-down? (atom false))
(defonce wx-event-chan (chan 100))
(defonce iotwx-event-chan (chan 100))

(defonce haier-companyid 15)

(def valid-mtypes #{:weight :bp :bsl :food :exercise :training})

(defonce merge-circle-chan (chan (sliding-buffer 100)))
(defonce most-active-users (atom {}))

(defn cache-most-active-users [cid users]
  (swap! most-active-users assoc cid users))

(defn most-active-users-from-cache [cid]
  {:pre [cid]}
  (get @most-active-users cid))


;; (def jifen-source-map
;;   {:first-login {:code 101 :reason "首次注册登录成功"}
;;    :first-health-scale {:code 102 :reason "首次绑定体脂秤"}
;;    :first-kitchen-scale {:code 103 :reason "首次绑定体脂秤"}
;;    })
