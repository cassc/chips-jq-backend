(ns chips.routes.validators.shop
  (:require
   [chips.routes.base  :refer [return-code-with-alt]]
   [chips.utils        :as vu :refer [role-enabled? uid->type cid->signature]]
   [chips.utility :refer [wrap-async]]
   [chips.models.base :refer :all]
   [chips.models.users :as mu]
   [chips.config       :refer :all]
   [chips.store.rds    :as rds :refer [store-code]]
   [chips.codes        :refer [cds]]
   
   [cheshire.core :refer [generate-string parse-string]]
   [taoensso.timbre    :as t]
   [noir.validation    :as v]
   [noir.util.route    :refer [restricted]]
   [clojure.string     :as s]
   [clojure.java.io    :as io]
   [clojure.data.json  :refer [write-str]]
   [sparrows.misc      :refer [str->num lowercase-trim]]   ))


(defn validate-add-address
  [{:keys [aid fullname phone province city area address zipcode isdefault] :as params}]
  {:pre [aid]}
  (and
   (v/rule
    (and fullname phone province address zipcode isdefault)
    [:error (cds :required-param-not-exist)])

   ))

(defn validate-update-address
  [{:keys [aid id fullname phone province city area address zipcode isdefault] :as params}]
  {:pre [aid]}
  (and
   (v/rule
    (and id (or fullname phone province address zipcode isdefault))
    [:error (cds :required-param-not-exist)])

   ))


(defn validate-update-cart-product
  [{:keys [aid pid quantity] :as params}]
  {:pre [aid]}
  (and
   (v/rule
    (and pid quantity)
    [:error (cds :required-param-not-exist)])))


(defn validate-delete-order
  [{:keys [aid oid] :as params}]
  {:pre [aid]}
  (and
   (v/rule
    oid
    [:error (cds :required-param-not-exist)])
   (let [{:keys [status]} (->entity (mysql-db) :s_order {:id oid :aid aid})]
     (and
      (v/rule
       status
       [:error (cds :order-not-exists)])
      (v/rule
       (not= status "paid")
       [:error (cds :order-invalid-state)])))))

(defn validate-put-order [{:keys [v-product address payment_type price score aid]}]
  {:pre [aid price score]}
  (let [{:keys [fullname phone province address zipcode]} address
        v-sid (map :sid v-product)]
    (and
     (v/rule
      (seq v-product)
      [:error (cds :required-param-not-exist)])
     (v/rule
      (and fullname phone province address zipcode)
      [:error (cds :required-param-not-exist)])
     (v/rule
      (every? identity v-product)
      [:error (cds :invalid-param)])
     (v/rule
      (= (count v-product)
         (count (map :id v-product)))
      [:error (cds :order-same-prod-should-group)])
     (v/rule
      (apply = v-sid)
      [:error (cds :order-from-same-seller-required)])
     (v/rule
      (or (s/blank? payment_type)
          (#{"weixin" "alipay" "yl-weixin" "yl-alipay" "unionpay" "kjt-h5"} payment_type))
      [:error (cds :invalid-payment-type)])
     (v/rule
      (>= (mu/get-jifen aid) score)
      [:error (cds :not-enough-score)]))))

(defn validate-prepay [{:keys [aid id price score status]}]
  (and
   (v/rule
    id
    [:error (cds :order-not-exists)])
   (v/rule
    (= "pending" status)
    [:error (cds :order-status-invalid)])
   (v/rule
    (pos? price)
    [:error (cds :order-no-need-to-pay)])))


(defn validate-order-confirm [{:keys [aid id price score status]}]
  (and
   (v/rule
    id
    [:error (cds :order-not-exists)])
   (v/rule
    (= "paid" status)
    [:error (cds :order-status-invalid)])))

(defn validate-order--product-return-or-change [{:keys [orig-order request x-old-return]}]
  (let [{:keys [tpe ouid pid quantity ship_provider ship_id]} request
        {:keys [status product]} orig-order
        n-old-returned (reduce (fn [sum {:keys [quantity]}]
                                 (+ sum quantity))
                               0
                               x-old-return)]
    (and
     (v/rule
      (and (not (s/blank? ouid)) pid quantity)
      [:error (cds :required-param-not-exist)])
     (v/rule
      orig-order
      [:error (cds :order-not-exists)])
     (v/rule
      product
      [:error (cds :product-not-exists)])
     (v/rule
      (and quantity (pos? quantity))
      [:error (cds :invalid-param)])
     (v/rule
      (<= (+ n-old-returned quantity) (:quantity product))
      [:error (cds :not-enough-prod-to-return)])
     (v/rule
      (or
       (= "complete" status)
       (and
        (= tpe :refund)
        (#{"paid" "delivery" "complete"} status)))
      [:error (cds :invalid-order-status)]))))







