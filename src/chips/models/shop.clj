(ns chips.models.shop
  (:require
   [chips.config         :refer [props]]
   [chips.models.base    :refer :all]
   [chips.store.rds      :as rds]
   [chips.utils          :as vu :refer [convert-sql-val write-icon-file reverse-compare-version compare-version b64-dec->bytes]]
   [chips.utility        :refer [internal-exception-hanlder]]
   [chips.kjt :as kjt]
   [chips.yl :as yl]
   
   [sparrows.cypher :refer :all]
   [sparrows.misc        :refer [dissoc-nil-val wrap-exception str->num]]

   [cheshire.core :refer [generate-string parse-string]]
   [clojure.core.memoize :as memo]
   [clojure.java.jdbc    :as j]
   [clojure.string       :as s]
   [clojure.data.json :refer [read-str]]
   [taoensso.timbre      :as t])
  (:import [java.sql Date Timestamp]))

(defn get-address [aid]
  (->entities (mysql-db) :csb_address {:aid aid}))

(defn add-address [{:keys [aid fullname phone province city area address zipcode isdefault] :as addr}]
  {:pre [aid fullname phone province address zipcode isdefault]}
  (t/info "add-address" addr)
  (j/with-db-transaction [db (mysql-db)]
    (let [id (get-generated-id (j/insert! db :csb_address (->
                                                           addr
                                                           (assoc :ts (System/currentTimeMillis))
                                                           (dissoc-nil-val))))]
      (when (= "y" isdefault)
        (j/update! db :csb_address {:isdefault "n"} ["aid=? and id!=?" aid id]))
      (assoc addr :id id))))

(defn update-address [{:keys [aid id fullname phone province city area address zipcode isdefault] :as addr}]
  {:pre [aid id (or fullname phone province address zipcode isdefault)]}
  (j/with-db-transaction [db (mysql-db)]
    (j/update! db :csb_address (dissoc-nil-val addr) ["id=?" id])
    (when (= "y" isdefault)
      (j/update! db :csb_address {:isdefault "n"} ["aid=? and id!=?" aid id]))))

(defn delete-address [id aid]
  (j/delete! (mysql-db) :csb_address ["id=? and aid=?" id aid]))

(defn- -sid->seller [sid]
  (->entity (mysql-db) :s_seller {:id sid}))

(defonce sid->seller (memo/ttl -sid->seller :ttl/threshold (* 2 60 1000)))

(defn attach-seller [{:keys [sid] :as p}]
  (assoc p :seller (sid->seller sid)))

(defn get-product [{:keys [page]}]
  {:pre [(pos? page)]}
  (let [cnt 20
        start (* cnt (dec page))
        qvec ["select *, id pid from s_product where status=? order by id desc limit ?,?" "show" start cnt]]
    (j/query (mysql-db) qvec {:row-fn attach-seller})))

(defn search-product [{:keys [term page]}]
  (let [cnt 20
        start (* cnt (dec page))]
    (j/query (mysql-db)
             ["select *, id pid from s_product where status=? and title like ? order by id desc limit ?,?" "show" (str "%" term "%") start cnt]
             {:row-fn attach-seller})))

(defn top-products []
  (j/query (mysql-db)
           ["select * from s_top_product t, s_product p where t.pid=p.id and p.status=? order by t.loc asc" "show"]
           {:row-fn attach-seller}))

(defn product-by-id [pid]
  {:pre [pid]}
  (->entity (mysql-db) :s_product {:id pid}))

(defn get-cart-products [aid]
  {:pre [aid]}
  (j/query (mysql-db)
           ["select p.*, p.id pid, c.quantity from s_cart c, s_product p where c.aid=? and c.pid=p.id" aid]
           {:row-fn attach-seller}))

(defn get-cart-product-quantity [{:keys [aid pid]}]
  {:pre [aid pid]}
  (:quantity (->entity (mysql-db) :s_cart {:aid aid :pid pid}) 0))

(defn delete-cart [aid]
  {:pre [aid]}
  (j/delete! (mysql-db) :s_cart ["aid=?" aid]))

(defn update-cart-product [{:keys [aid pid quantity]}]
  {:pre [pid quantity aid]}
  (if (pos? quantity)
    (j/with-db-transaction [db (mysql-db)]
      (if-let [{:keys [id]} (->entity db :s_cart {:pid pid :aid aid})]
        (j/update! db :s_cart {:quantity quantity} ["id=?" id])
        (j/insert! db :s_cart {:pid pid :quantity quantity :aid aid :ts (System/currentTimeMillis)})))
    (j/delete! (mysql-db) :s_cart ["aid=? and pid=?" aid pid])))

(defn delete-cart-product [{:keys [aid x-pid]}]
  {:pre [aid (seq x-pid)]}
  (let [s-where (str
                 "aid=? and pid in ("
                 (s/join "," (repeat (count x-pid) "?"))
                 ")")
        x-where (concat [s-where aid] x-pid)]
    (j/delete! (mysql-db) :s_cart x-where)))

(defn order-by-ouid [ouid]
  (->entity (mysql-db) :s_order {:ouid ouid}))

(defn delete-order [{:keys [aid oid] :as params}]
  (t/info "delete-order" params)
  (j/with-db-transaction [db (mysql-db)]
    (when-let [{:keys [status] :as od} (->entity db :s_order {:id oid :aid aid})]
      (t/info "deleting order" od)
      (cond
        (#{"complete"} status) (j/update! db :s_order {:status "hide"} ["id=?" oid])
        (#{"pending" "cancel"} status) (j/delete! db :s_order ["id=?" oid])))))

(defn ->order [{:keys [aid oid]}]
  {:pre [aid oid]}
  (->entity (mysql-db) :s_order {:aid aid :id oid}))

(defn save-payment
  ([ouid raw-payment source event]
   (let [raw (try
               (generate-string (dissoc raw-payment :sign))
               (catch Exception e
                 (str raw-payment)))]
     (j/insert! (mysql-db) :s_payment {:ouid ouid :raw raw :ts (System/currentTimeMillis)
                                       :source (when source (name source))
                                       :event (when event (name event))})))
  ([ouid raw-payment]
   (save-payment ouid raw-payment nil nil)))

(defn- remove-cart-products [db aid x-pid]
  {:pre [aid]}
  (when (seq x-pid)
    (let [qstr (str "aid=? and pid in ("
                    (s/join "," (repeat (count x-pid) "?"))
                    ")")
          qvec (concat [qstr aid] x-pid)]
      (j/delete! db :s_cart qvec))))

(defn- summary-product [v-product]
  (reduce
   (fn [ss {:keys [title quantity]}]
     (let [ss (str ss title " X " quantity "；")]
       (if (> (count ss) 200)
         (reduced (str (subs ss 0 200) " ..."))
         ss)))
   ""
   v-product))

(defn- invalid-ip? [ip]
  (or (s/blank? ip) (s/starts-with? ip "127.")))

(defn- mark-paid [{:keys [id aid score]}]
  {:pre [id aid score]}
  (j/with-db-transaction [db (mysql-db)]
    (j/update! db :s_order {:status "paid"} ["id=?" id])
    (when (pos? score)
      (topup-score {:source :consume-by-order :score (- score) :aid aid :oid id}))))

(defn- mark-paid-if-pending [ouid]
  (when-let [order (->entity (mysql-db) :s_order {:ouid ouid :status "pending"})]
    (mark-paid order)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; kjt-H5 重复支付的响应
;; 19-03-02 11:30:14.276 +0800 iZm5e6qzvrrju3blpn1rneZ INFO [chips.kjt:73] - kjt-resp {:opts {:timeout 5000, :follow-redirects false, :headers {"accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "accept-language" "en-US,en;q=0.8,zh;q=0.6", "accept-encoding" "gzip", "user-agent" "Mozilla/5.0 (X11; Linux i686; rv:30.0) Gecko/20100101 Firefox/30.0 Iceweasel/30.0"}, :insecure? true, :form-params {:service "instant_trade", :format "JSON", :sign "c8feiRaT3PVwEwJpQjqMU2vg5jIdLSaNHjTc%2FZU%2FKUtN5sqMxIAx3rjJWSLrJHSEp8OPTsCqJJMh7OW3yJT1gV9NtLGUniN8Qi5cOmu53YmTxFyF1FC%2BTG3ifk2Qh5tN%2FJnZJ5qwYoBPbdSR%2F6L%2FJlaDJplaRf6D%2FJdgIOWJmxk%3D", :request_no "1551497413911", :sign_type "RSA", :charset "UTF-8", :biz_content "1i1rsA%2FsHuIfky6YAxAiItWWihVqRk%2FRI2n%2Bo7Q5uUCYCWye07ZuWB5CJTdxXKKiGmxvOnI8mCoyJUiKB%2BJ5HtxMBVk%2B%2Bl%2F0WwPsHHLQlbJy9BmiLxRinu9zk0IsqifNpVwZtcPGoN3Yr6o7G8NWJOFDcDK2WF5PPNtkrrWd79crh5IGkZy1hKVJWWIt1y2jGhitFLMoxYERfUuTWyZnkj%2BfSLhVLvdvzjbecy7cqOwqSlN1brn2ACwF5RDHQYJMUGR%2FlAbLb%2Bl6vLSSJLnTw8fH%2BNtStcrRowgxmkBcJ4YolES1bgse0MDMiD0WH80PnRnUPhG5FWC9sFe96u213LreR26WG5ynlR55FqqiDz%2B1CQb7jVlGzXZriGuz44vrgEoEevT%2Bje2vDLKsyKUtGxxxJc2i80tQTxGRgq5%2Fbc8c3HRVnbAulHfQpME3l%2BeiP8lD2iFNIxn0BWUIiM6vsEHnU9ZWPYV8J%2BHYjGgy78uVhtUjyZ4u8fZgSolUerw0swu3i%2B7BYGiH1Sx4jac%2FhlbqxCazL%2BjKh%2BQ0rOzU1cRJlu4labMrz5gTNTGe4XN6zuIRshllvXM4%2FAYSroo8c7T5cnoWNqyemDYraBD2yBNzphSkoBlLiOaZv85wbTnYJy73U2d%2Fo%2FEB%2FE44lhWs4RdpYq%2FUKFjOXD8Be4ANck1iPHLsMQxgAX5xdWKDoWWOw2HlknRvSjemwJHPIHt%2BfjBwVTL6RgFk6YxoOmyJI%2Fne0rCAangNC4pkn%2BRMVmBYRQwJStM5AlD1laesn36%2FOXgpxxgx%2BBZPbBAFBqS1XY2UnGpmLdHAtgBv0bctJtKozQhY3GzStse%2FVm2i5mUpZw%3D%3D", :version "1.0", :timestamp "2019-03-02+11%3A30%3A13", :partner_id "200001725745"}, :method :post, :url "https://zgateway.kjtpay.com/recv.do"}, :body "{\"code\":\"S10000\",\"msg\":\"接口调用成功\",\"sign\":\"zFA6ixoqkHzqvUknxIjVlueuM6Gn/7uu6cNqSw6udSc0zSNanpfBPrOYk7YpEWe+nNMNVh61EXPBypCOMm1Cj0hqySdXF+UftJ+8Om26JwRqmnZSRuL83KnXDbJFGtPr9rYgxE5HUPEcTx9NDXmd7VGPM130sqe5yJAFSHeQhbF6RqWU+y899CNrBk/8GHqOR7G8pHsPq0JL657S1pvKuP50AD48/osX5nUBFO9a1sGnmP1PSOH2plosU7LB7dU+OOrdl2AVL+HP1MJpkgkFmhdoJjjEpucp7KTBkGVcrC4D+HZBAhuXLJI/YxhfZFbLqj8+syB0bsOohIpgg+2xRA==\",\"charset\":\"UTF-8\",\"sign_type\":\"RSA\",\"biz_content\":{\"trade_res_list\":[{\"out_trade_no\":\"20190302110335991\",\"return_time\":\"2019-03-02 11:09:21\",\"trade_no\":\"101149581654963186113\",\"return_url\":\"http://47.105.225.141:8080/pub/kjt/paysuccess\",\"status\":\"S\",\"pay_time\":\"2019-03-02 11:09:21\"}]}}", :headers {:accept-ranges "bytes", :connection "Keep-alive", :content-length "684", :content-type "text/html;charset=UTF-8", :date "Sat, 02 Mar 2019 03:30:15 GMT", :server "nginx", :via "1.1 ID-0002262071501522 uproxy-3"}, :status 200}
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; kjt h5 正常成功
;; 19-03-01 09:28:33.481 +0800 iZm5e6qzvrrju3blpn1rneZ INFO [chips.kjt:68] - kjt-resp {:opts {:timeout 5000, :follow-redirects false, :headers {"accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "accept-language" "en-US,en;q=0.8,zh;q=0.6", "accept-encoding" "gzip", "user-agent" "Mozilla/5.0 (X11; Linux i686; rv:30.0) Gecko/20100101 Firefox/30.0 Iceweasel/30.0"}, :insecure? true, :form-params {:service "instant_trade", :format "JSON", :sign "WDAViLI4nT0NYVcTCvpMkt2V5e7XyRdi42jBPaDiR12C0l3z3jViDbo0uqK20995KrObsToexNy7MLY%2FxSuCdum4urZxvJXQkEAP3tc52xErxKr0Sqd1ZDM1ZGlBhDGsnT7lpSFlk1G27XOV6Y3UItBKYPgvgC6kYqjlXuaQTa0%3D", :request_no "1551403712888", :sign_type "RSA", :charset "UTF-8", :biz_content "fvpE%2F%2BmnBEdRvlCu%2BQqHpuybJR6JoIn8xjB%2FghVRmlJD4yxcKxcpLcM937t6O1%2FmsSwANxmft2QlMNphIQJwR3fW4%2BT53WTMvCh4bMjoxZLP9pAgfUOtt6F6eSgR%2FSoWx3lyUKVtc7DK2WznPB8KL5%2BrwPvDoItVcTwwI%2FhmqEu8gsU46%2Bbm6SKgxA2Az9meIdaUtV%2BNzObqsTBGY1vz0f95KpTTWl9so4Vf8r4Cii3Myegpa9nvLQzPFDFn35mHSyC%2FXAzVRUKOBjvkVsEPksrsdRAuB%2B9wohL7EKrJO21d4hHs5fv%2FOCB6Eyqxx8d3igxqq%2FyHNbcZEUSIF3eOp5jQeF75WhsteBqHMiXPEfykvr7TEmVOdPYYrt0np8vpniPdKJ%2B5Pau9TQz0D%2FsQr7k4UneagH5H2HBr3HKx2lboiufjQ5Xk465F%2Fikm5AtgcICe%2FOlTj6uufDpUxyGxOqlJ7TjirAwCKC%2FYrlpIfZWZ5cHljL1zaR1gzRHfEe7ybxzRGvr7wKzc6KzImwk25VfuYsBJMtgp%2FueB1fzSk4K9ytTyGIkCz7nTDfu0m%2BEIodd0csll%2Bxgx0KQj0AZyFp9NSmo8QWK78OowzDTWS8fNvbztY8CS1UO8C3r6hn9fcxh4u%2Bn5CpxSWYoFLG6VOD2rCCgfIgIrVJgSjAQGYnA%3D", :version "1.0", :timestamp "2019-03-01+09%3A28%3A32", :partner_id "200001725745"}, :method :post, :url "https://zgateway.kjtpay.com/recv.do"}, :body "", :headers {:connection "Keep-alive", :content-language "en-US", :content-length "0", :date "Fri, 01 Mar 2019 01:28:34 GMT", :location "https://zcash-h5.kjtpay.com/loading?cashierType=H5&token=916574545d6b457b85f44b6a813e56ac&partnerId=200001725745", :server "nginx", :via "1.1 ID-0002262071501522 uproxy-5"}, :status 302}
(defn- gen-kjt-prepay [{:keys [ouid v-product price ip]}]
  {:pre [ouid v-product price ip]}
  (let [x-trade-info [{:out_trade_no ouid
                       :subject (summary-product v-product)
                       :price (format  "%.2f" (/ price 100.0))
                       :quantity "1"
                       :total_amount (vu/cent->yuan price)
                       :payee_identity_type "2"
                       :payee_identity "3504163011@qq.com"
                       :notify_url (props [:kjt :notify-url])}]
        terminal-info {:terminal_type "01" :ip ip}
        enc-params {:payer_identity "anonymous"
                    :payer_ip (if (invalid-ip? ip) "123.125.115.110" ip)
                    :biz_product_code "20601"
                    :cashier_type "H5"
                    :trade_info x-trade-info
                    :terminal_info terminal-info
                    :return_url (props [:kjt :return-url])}
        {:keys [headers body status]} (kjt/gateway-request "instant_trade" enc-params)
        url (:location headers)
        success-url (when (= 200 status)
                      (-> body
                          (parse-string true)
                          (get-in [:biz_content :trade_res_list 0 :return_url])))]
    (when (= success-url (props [:kjt :return-url]))
      (mark-paid-if-pending ouid))
    {:prepay_id (or url success-url)}))

(defn gen-yl-prepay [payment-type {:keys [ouid price]}]
  {:pre [payment-type price ouid]}
  (let [{:keys [errCode appPayRequest merOrderId] :as resp}
        (yl/create-order {:payment-type payment-type
                          :ouid ouid
                          :total-amount price})]
    (if (= errCode "SUCCESS")
      (do
        (t/info "yl-prepay done:" ouid merOrderId)
        (save-payment ouid resp :yl :pay-init)
        {:prepay_id (first (vals appPayRequest))})
      (do
        (t/error "yl-prepay error:" resp)))))

;; gen prepay by existing order
(defn gen-kjt-prepay-by-order [ip {:keys [id price ouid]}]
  {:pre [id (pos? price) ip ouid]}
  (let [v-product (->entities (mysql-db) :s_order_product {:oid id})]
    (gen-kjt-prepay {:ouid ouid
                     :price price
                     :v-product v-product
                     :ip (if (invalid-ip? ip) "123.125.115.110" ip)})))

(defn add-order [{:keys [aid v-product address payment_type score price ip]}]
  {:pre [aid (seq v-product)]}
  (let [ouid  (vu/gen-ouid)
        order (-> address
                  (select-keys [:fullname :phone :province :city :area :address :zipcode])
                  (assoc :aid aid
                         :status (if (zero? price)
                                   "paid"
                                   "pending")
                         :ts (System/currentTimeMillis)
                         :ouid ouid
                         :score score
                         :price price))] 
    (j/with-db-transaction [db (mysql-db)]
      (let [oid       (get-generated-id (j/insert! db :s_order order))
            r-product (mapv (fn [prod]
                              (-> prod
                                  (assoc :oid oid)
                                  (dissoc :ts :status)))
                            v-product)]
        (j/insert-multi! db :s_order_product r-product)
        (remove-cart-products db aid (map :pid v-product))
        (when (zero? price)
          (topup-score {:source :consume-by-order :score (- score) :aid aid :oid oid}))
        (assoc order
               :id oid :products (mapv attach-seller r-product)
               :payment_type payment_type
               :prepay (when (pos? price)
                         (case payment_type
                           "yl-weixin" (gen-yl-prepay :weixin
                                                      {:ouid  ouid
                                                       :price price})
                           "yl-alipay" (gen-yl-prepay :alipay
                                                      {:ouid  ouid
                                                       :price price})
                           "unionpay"  (gen-yl-prepay :unionpay
                                                      {:ouid  ouid
                                                       :price price})
                           "kjt-h5"    (gen-kjt-prepay {:ouid      ouid
                                                        :price     price
                                                        :v-product v-product
                                                        :ip        ip})
                           nil)))))))

(defn get-seller [id]
  {:pre [(pos? id)]}
  (->entity (mysql-db) :s_seller {:id id}))

(defn update-order-status [{:keys [oid status aid]}]
  {:pre [aid oid status]}
  (j/update! (mysql-db) :s_order {:status status} ["id=? and aid=?" oid aid]))

(defn handle-kjt-notify [{:keys [notify_type trade_status outer_trade_no orig_out_trade_no trade_amount] :as params}]
  (let [ouid outer_trade_no]
    (case notify_type
      "trade_status_sync" (case trade_status
                            "TRADE_SUCCESS" (let [{:keys [id status] :as order} (order-by-ouid ouid)]
                                              (if (= status "pending")
                                                (mark-paid order)
                                                (t/error "Inconsistent order status:" order params))
                                              (save-payment ouid params :kjt :pay-success))
                            ;; "TRADE_CLOSED" (j/update! (mysql-db) :s_order {:status "cancel"} ["ouid=?" outer_trade_no])
                            (t/info "ignore trade_status" params))
      "refund_status_sync" (t/info "refund done:" orig_out_trade_no params)
      (t/warn "ignore kjt-notify" params))))

(defn handle-yl-notify [{:keys [bankCardNo tid invoiceAmount buyerPayAmount mid sign billFunds targetOrderId subInst payTime orderDesc merOrderId totalAmount targetSys seqId billFundsDesc zT status notifyId settleDate couponAmount refundStatus]
                         :as params}]
  (let [ouid (yl/ouid-from-yl merOrderId)]
    (case status
      "TRADE_SUCCESS" (let [{:keys [id status] :as order} (order-by-ouid ouid)]
                        (if (= status "pending")
                          (if (= "SUCCESS" refundStatus)
                            (t/info "Refund success:" merOrderId)
                            (mark-paid order))
                          (t/error "Inconsistent order status:" order params))
                        (save-payment ouid params :yl :pay-success))
      (t/warn "ignore yl-notify" params))))


(defn yl-query-handle-paid [x-mer-oid]
  (some (fn [merOrderId]
          (let [{:keys [status] :as notify} (yl/order-query {:merOrderId merOrderId})]
            (when (= status "TRADE_SUCCESS")
              (handle-yl-notify notify)
              true)))
        x-mer-oid))

(defn check-pending-status [ouid]
  (when-let [{:keys [status]} (->entity (mysql-db) :s_order {:ouid ouid})]
    (when (= "pending" status)
      ;; currently yl only
      (let [x-mer-oid (->> (->entities (mysql-db) :s_payment {:ouid ouid})
                           (map (fn [{:keys [raw]}]
                                  ((juxt :merOrderId :status) (parse-string raw true))))
                           (group-by first)
                           (filter (fn [[merOrderId xs]]
                                     (when (and (= (count (distinct (map second xs))) 1)
                                                (= "NEW_ORDER" (-> xs first second)))
                                       merOrderId))))]
        (some yl-query-handle-paid x-mer-oid)))))

(defn x-return-by-pid [{:keys [aid ouid pid]}]
  (when (and aid ouid pid)
    (->entities (mysql-db) :s_order_product_return {:aid aid :ouid ouid :pid pid})))

(defn- assert-return-request-vals [row]
  (assert
   (every? identity (vals (select-keys row [:aid :oid :ouid :pid :sid :quantity :tpe :status :refund_payment_id :refund_success_ts])))
   "save-order-product-return-request error"))

(defn save-order-product-return-request [{:keys [request orig-order aid]}]
  (let [{:keys [ouid tpe pid quantity ship_provider ship_id reason]} request
        row {:aid               aid
             :oid               (:id orig-order)
             :ouid              ouid
             :pid               pid
             :sid               (get-in orig-order [:product :sid])
             :quantity          quantity
             :tpe               (name tpe)
             :ship_provider     ship_provider
             :ship_id           ship_id
             :status            "pending"
             :reason            reason
             :ts                (System/currentTimeMillis)
             :refund_payment_id 0
             :refund_success_ts 0}]
    (assert-return-request-vals row)
    (j/insert! (mysql-db) :s_order_product_return row)))

(defn- attach-return-status [ouid {:keys [pid aid] :as order}]
  (let [returns (x-return-by-pid {:aid aid :ouid ouid :pid pid})]
    (assoc order :returns (map #(select-keys % [:status :tpe :quantity :reason]) returns))))

(defn get-order [{:keys [aid page cnt ouid]}]
  {:pre [aid]}
  (let [page (or page 1)
        cnt (or cnt 10)
        start (* cnt (dec page))
        qstr (str "select * from s_order where aid=? "
                  (when ouid "and ouid=? ")
                  " order by id desc limit ?"
                  (when-not ouid ",?"))
        qvec (if ouid
               [qstr aid ouid 1]
               [qstr aid start cnt])
        x-order (j/query (mysql-db) qvec)]
    (map (fn [{:keys [id ouid] :as order}]
           (let [qvec ["select * from s_order_product where oid=?" id]
                 x-products (map (comp (partial attach-return-status ouid) attach-seller) (j/query (mysql-db) qvec))]
             (assoc order :products x-products)))
         x-order)))


(defn attach-payment-info [{:keys [ouid] :as o-refund}]
  {:pre [ouid]}
  (let [qstr "select * from s_payment where ouid =? order by id desc limit 1"
        payment (first (j/query (mysql-db) [qstr ouid]))]
    (assoc o-refund :orig-payment (parse-string (:raw payment) true))))

(defn- attach-order-produt [{:keys [oid pid] :as o-refund}]
  {:pre [oid pid]}
  (let [product (->entity (mysql-db) :s_order_product {:oid oid :pid pid})]
    (assoc o-refund :product product)))

(defn x-refund-request
  "Return requests pending refund"
  []
  (let [qstr "select * from s_order_product_return where status=? and refund_success_ts = ?"
        qvec [qstr "processing" 0]]
    (map (comp attach-order-produt  attach-payment-info) (j/query (mysql-db) qvec))))

(defn- mark-refund-success! [source ouid req-id resp]
  (t/info "mark-refund-success" source ouid)
  (save-payment ouid resp source :refund-success)
  (j/update! (mysql-db)
             :s_order_product_return
             {:status "success" :refund_success_ts (System/currentTimeMillis)}
             ["id=?" req-id]))

(defn refund! [{:keys [id ouid orig-payment product quantity]}]
  {:pre [id ouid quantity product orig-payment]}
  (let [price (* quantity (:price product))]
    (case (vu/payment-source orig-payment)
      :yl (let [{:keys [refundStatus errCode] :as resp}
                (yl/refund {:merOrderId (:merOrderId orig-payment)
                            :refundAmount price})]
            (if (= "SUCCESS" refundStatus)
              (mark-refund-success! :yl ouid id resp)
              (t/error "yl refund request fail:" ouid resp)))
      :kjt (let [{:keys [biz_content] :as resp} (kjt/refund ouid price)]
             (if (or (#{"P" "S"} (:status biz_content))
                     (s/includes? (:sub_msg resp "") "小于退款金额"))
               (mark-refund-success! :kjt ouid id resp)
               (t/error "kjt refund request fail:" ouid resp))))) )
