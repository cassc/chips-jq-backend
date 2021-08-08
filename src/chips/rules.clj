(ns chips.rules
  (:require
   [chips.store.rds :as rds]))

;; thread-local binding of token for a request
(def ^:dynamic *cstoken*)

(defn user-page
  [req]
  ;;(when-let [cstoken (get-in req [:headers "cstoken"])] (rds/cstoken->aid cstoken))
  (or
   (= :options (:request-method req))
   (:aid req)))
