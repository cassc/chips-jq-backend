(ns chips.routes.validators.mblog
  (:require
   [cheshire.core :refer [generate-string parse-string]]
   [taoensso.timbre    :as t]
   [noir.validation    :as v]
   [noir.util.route    :refer [restricted]]
   [clojure.string     :as s]
   [clojure.java.io    :as io]
   [clojure.data.json  :refer [write-str]]
   [sparrows.misc      :refer [str->num lowercase-trim]]
   [sparrows.cypher    :refer [md5]]
   [sparrows.system    :refer [get-mime]]
   [chips.routes.base  :refer [return-code-with-alt]]
   [chips.utils        :as vu :refer [role-enabled? uid->type cid->signature]]
   [chips.utility :refer [wrap-async]]
   [chips.models.base :refer [allow-post-mblog?]]
   [chips.models.users :as mu]
   [chips.models.mblog :as b]
   [chips.config       :refer :all]
   [chips.codes        :refer [cds]]))


(defn validate-put-mblog [{:keys [account_id]}]
  (v/rule
   (allow-post-mblog? account_id)
   [:error (cds :account-post-mblog-disabled)]))
