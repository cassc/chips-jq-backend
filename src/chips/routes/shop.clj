(ns chips.routes.shop
  (:require
   [chips.codes                   :refer [cds]]
   [chips.models.shop            :as shop]
   [chips.models.fe            :refer :all]
   [chips.models.base            :refer :all]
   [chips.utils                   :as cu :refer [uid->type super-of? maybe-vals->int]]
   [chips.routes.validators.shop :refer :all]
   [chips.store.rds               :as rds]
   [chips.routes.base             :refer :all]
   
   [clojure.string                :as s]
   [sparrows.misc                 :as sm :refer [str->num dissoc-nil-val]]
   [sparrows.time                 :refer [now-in-secs]]
   [sparrows.system               :as ss]
   [noir.validation               :as v] 
   [taoensso.timbre               :as t]
   [noir.response                 :as r]
   
   [compojure.core                :refer [defroutes GET POST PUT DELETE]]
   [noir.util.route               :refer [def-restricted-routes]]
   [com.climate.claypoole         :as cp])
  (:import
   [java.sql SQLDataException]))

(defn handle-get-address [req]
  (r/json
   (success (shop/get-address (:aid req)))))

(defn handle-put-address [{:keys [aid params]}]
  (let [addr (assoc params :aid aid)]
    (validate-add-address addr)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (success (shop/add-address addr))))))

(defn handle-post-address [{:keys [aid params]}]
  (let [addr (assoc params :aid aid)]
    (validate-update-address addr)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (success (shop/update-address addr))))))

(defn handle-delete-address [req]
  (let [id (str->num (get-in req [:params :id]))
        aid (:aid req)]
    (r/json
     (if (and id (pos? id))
       (success (shop/delete-address id aid))
       (cds :invalid-param)))))

(defn handle-get-cart [req]
  (r/json
   (success (shop/get-cart-products (:aid req)))))

(defn handle-delete-cart [req]
  (r/json
   (success-no-return (shop/delete-cart (:aid req)))))

(defn handle-post-cart-product [{:keys [aid params]}]
  (let [{:keys [pid quantity]} params
        cart-prod {:aid aid :pid (str->num pid) :quantity (str->num quantity)}]
    (validate-update-cart-product cart-prod) ;; TODO chandle may not exist
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (success (shop/update-cart-product cart-prod))))))

(defn handle-post-cart-product-increment [{:keys [aid params]}]
  (r/json
   (let [pid (:pid params)]
     (if (and (integer? pid) (pos? pid))
       (let [quantity (shop/get-cart-product-quantity {:aid aid :pid pid})
             n-cnt (inc quantity)
             params {:aid aid :pid pid :quantity n-cnt}]
         (shop/update-cart-product params)
         (success quantity))
       (cds :invalid-param)))))

(defn handle-delete-cart-product [{:keys [aid params]}]
  (let [x-pid (map str->num (s/split (:pid params "") #","))]
    (r/json
     (if (every? identity x-pid)
       (success-no-return (shop/delete-cart-product {:aid aid :x-pid x-pid}))
       (cds :invalid-param)))))

(defn handle-get-order [{:keys [aid params]}]
  (let [{:keys [page cnt oid ouid]} params
        page (or (str->num page) 1)
        cnt (or (str->num cnt) 20)]
    (when ouid
      (shop/check-pending-status ouid))
    (r/json
     (success
      (shop/get-order {:aid aid :page page :cnt cnt :ouid ouid})))))

(defn handle-delete-order [{:keys [aid] :as req}]
  (r/json
   (let [oid (str->num (get-in req [:params :oid]))
         params {:oid oid :aid aid}]
     (validate-delete-order params) 
     (if-let [err (first (v/get-errors))]
       err
       (success (shop/delete-order params))))))

(defn handle-put-order [{:keys [aid params] :as req}]
  (let [products (:products params)
        {:keys [address payment_type]} params
        v-product (map (fn [{:keys [pid quantity]}]
                         (when (and pid quantity (pos? pid) (pos? quantity))
                           (let [prod (shop/product-by-id pid)]
                             (if (= "show" (:status prod))
                               (-> prod
                                   (dissoc :id)
                                   (assoc :quantity quantity :aid aid :pid pid))
                               (assoc prod :resp-error (cds :product-removed {:pid pid}))))))
                       products)
        ;; TODO NPE if product removed
        {:keys [price score]} (reduce
                               (fn [result {:keys [price score quantity]}]
                                 (-> result
                                     (update :price (partial + (* price quantity)))
                                     (update :score (partial + (* score quantity)))))
                               {:price 0 :score 0}
                               v-product)
        raw-order {:aid aid :v-product v-product :address address :payment_type payment_type :price price :score score
                   :ip (cu/ip-from-req req)}]
    (validate-put-order raw-order)
    (r/json
     (if-let [err (first (v/get-errors))]
       err
       (if-let [err (some :resp-error v-product)]
         err
         (success (shop/add-order raw-order)))))))

(defn handle-get-product [req]
  (let [{:keys [page]} (:params req)]
    (r/json
     (success
      (shop/get-product {:page (or (str->num page) 1)})))))

(defn handle-search-shop-product [req]
  (let [term (get-in req [:params :term])
        page (or (str->num (get-in req [:params :page]))
                 1)]
    (r/json
     (if (s/blank? term)
       (cds :invalid-param)
       (success (shop/search-product {:term term :page 1}))))))

(defn handle-get-top-product [_]
  (r/json
   (success (shop/top-products))))

(defn handle-get-seller [req]
  (let [id (str->num (get-in req [:params :id]))]
    (r/json
     (if id
       (success (shop/get-seller id))
       (cds :invalid-param)))))

;; 订单与支付应该分开处理。用户下单后，出现支付宝和微信按钮，用户点其中之一后，app调我们接口获取微信/支付宝预下单id，app根据预下单id调微信/支付定sdk完成支付。
;; 不在下单中包含预下单id有两个原因：一个原因是将订单与支付分开。另一个是订单中写入预下单id后，用户未支付，过一段时间再来支付重用这个id，但这个id在三方支付那边已经超时了。
(defn handle-put-shop-prepay [{:keys [aid params] :as req}]
  (r/json
   (let [{:keys [oid payment_type]} params
         oid (str->num oid)
         order (when oid (shop/->order {:aid aid :oid oid}))]
     (validate-prepay order)
     (if-let [err (first (v/get-errors))]
       err
       (success
        {:id oid
         :payment_type payment_type
         :prepay (case payment_type
                   "weixin" {:prepay_id "dummy"}
                   "alipay" {:prepay_id "dummy"}
                   "yl-weixin" (shop/gen-yl-prepay :weixin order)
                   "yl-alipay" (shop/gen-yl-prepay :alipay order)
                   "unionpay" (shop/gen-yl-prepay :unionpay order)
                   "kjt-h5" (shop/gen-kjt-prepay-by-order (cu/ip-from-req req) order)
                   nil)})))))

(defn handle-put-order-confirm [{:keys [aid params]}]
  (r/json
   (let [{:keys [oid]} params
         oid (str->num oid)
         order (when oid (shop/->order {:aid aid :oid oid}))]
     (validate-order-confirm order)
     (if-let [err (first (v/get-errors))]
       err
       (do
         (shop/update-order-status {:aid aid :oid oid :status "complete"})
         (topup-score {:source :purchase-confirm :aid aid :oid oid})
         (success))))))

(defn handle-post-order-product-return-or-change [tpe {:keys [params aid]}]
  (r/json
   (let [{:keys [ouid pid quantity ship_provider ship_id]} params
         [pid quantity] (map str->num [pid quantity])
         orig-order (first (shop/get-order {:aid aid :ouid ouid}))
         product (some (fn [prod]
                         (when (= pid (:pid prod))
                           prod))
                       (:products orig-order))
         x-old-return (shop/x-return-by-pid {:aid aid :pid pid :ouid ouid})
         orig-order (-> orig-order (dissoc :products) (assoc :product product))
         request (assoc params :tpe tpe :pid pid :quantity quantity)]
     (t/info orig-order)
     (validate-order--product-return-or-change {:request request
                                                :orig-order orig-order
                                                :x-old-return x-old-return})
     (if-let [err (first (v/get-errors))]
       err
       (do
         (shop/save-order-product-return-request {:request request
                                                  :orig-order orig-order
                                                  :aid aid})
         (success))))))

(def handle-post-order-product-exchange (partial handle-post-order-product-return-or-change :exchange))
(def handle-post-order-product-return (partial handle-post-order-product-return-or-change :return))
(def handle-post-order-product-refund (partial handle-post-order-product-return-or-change :refund))

(def-restricted-routes shop-routes
  (GET "/seller/:id" req handle-get-seller)
  (GET "/address" req handle-get-address)
  (PUT "/address" req handle-put-address)
  (POST "/address" req handle-post-address)
  (DELETE "/address" req handle-delete-address)

  (GET "/order" req handle-get-order)
  (DELETE "/order" req handle-delete-order)
  (PUT "/order" req handle-put-order)
  (PUT "/shop/prepay" req handle-put-shop-prepay)
  (PUT "/order/confirm" req handle-put-order-confirm)

  (GET "/cart" req handle-get-cart)
  (DELETE "/cart" req handle-delete-cart)
  (POST "/cart/product" req handle-post-cart-product)
  (POST "/cart/product/increment" req handle-post-cart-product-increment)
  (DELETE "/cart/product" req handle-delete-cart-product)

  (GET "/product" req handle-get-product)

  (GET "/shop/product/q" req handle-search-shop-product)
  (GET "/shop/top/product" req handle-get-top-product)

  (POST "/shop/order/product/return" req handle-post-order-product-return)
  (POST "/shop/order/product/exchange" req handle-post-order-product-exchange)
  (POST "/shop/order/product/refund" req handle-post-order-product-refund)
  
  )
