(ns chips.yl
  (:require
   [chips.utils :as utils :refer [dissoc-nil-val]]
   [chips.config :refer [props]]
   [sparrows.http :as http :refer [async-request]]
   [sparrows.cypher :refer [form-encode sha1 md5]]
   [sparrows.system :refer [get-mime]]
   [sparrows.time :as time]
   [clojure.java.io :as io]
   [cheshire.core :refer [parse-string generate-string]]
   [taoensso.timbre :as t]
   [clojure.string :as s]
   [com.rpl.specter :as specter :refer [transform]])
  (:import
   [java.util HashMap]))

(defn- yl-sign [params]
  (let [x-ky (sort (keys params))
        txt (reduce (fn [ss ky]
                      (if-let [val (get params ky)]
                        (str ss (when-not (s/blank? ss) "&") (name ky) "=" val)
                        ss))
                    ""
                    x-ky)
        txt (str txt (props [:yl :key]))]
    (s/upper-case (md5 txt))))

(defn check-sign [params]
  (let [sign (:sign params)
        sig (yl-sign (dissoc params :sign))]
    (and (not (s/blank? sign))
         (= sign sig))))


;; success resp:
;; {:tid "00000001", :mid "898310148160568", :sign "F0C0D13F3C1CD7C1A4D1329C2669E689", :targetMid "48020000", :merName "全渠道", :merOrderId "319420190327083735", :totalAmount 10, :msgType "uac.appOrder", :targetSys "UAC", :seqId "00473600907N", :appPayRequest {:tn "539883888375609285101"}, :status "NEW_ORDER", :msgSrc "WWW.TEST.COM", :settleRefId "00473600907N", :responseTimestamp "2019-03-27 08:58:50", :errCode "SUCCESS"}
;; {:tid "00000001", :mid "898310148160568", :sign "76D97DE04BA4C8D9926F9559760212C7", :targetMid "2015061000120322", :merName "全渠道", :merOrderId "319476617420190327083733", :totalAmount 10, :msgType "trade.precreate", :targetSys "Alipay 2.0", :connectSys "ALIPAY", :seqId "00474400825N", :appPayRequest {:qrCode "https://qr.alipay.com/bax02042fnbrprpfka3g6010"}, :targetStatus "10000", :status "NEW_ORDER", :qrCode "https://qr.alipay.com/bax02042fnbrprpfka3g6010", :msgSrc "WWW.TEST.COM", :settleRefId "00474400825N", :responseTimestamp "2019-03-28 08:04:47", :errCode "SUCCESS"}
;; duplicate ouid resp:
;; {:msgType "uac.appOrder", :responseTimestamp "2019-03-27 08:49:43", :errCode "DUP_ORDER", :msgSrc "WWW.TEST.COM", :errMsg "重复订单号", :sign "DFC9C40629B0C053AAE8DE828D1587AD"}
(defn create-order [{:keys [payment-type total-amount ouid]}]
  {:pre [payment-type total-amount ouid]}
  (let [now (System/currentTimeMillis)
        params {:mid (props [:yl :mid])
                :tid (props [:yl :tid])
                :msgType (case payment-type
                           :alipay "trade.precreate"
                           :weixin "wx.unifiedOrder"
                           :unionpay "uac.appOrder"
                           ;; :qmf "qmf.order"
                           )
                :msgSrc (props [:yl :msgSrc])
                :instMid (props [:yl :instMid])
                :merOrderId (str (props [:yl :msgSrcId]) (utils/rand-ints 6) ouid) ;; create new order every time
                :totalAmount total-amount
                :tradeType "APP" ;; required for weixin
                :notifyUrl (props [:yl :notify-url])
                :orderDesc (str "渐轻订单：" ouid)
                :requestTimestamp (time/long->datetime-string now)}
        sign (yl-sign params)
        resp @(async-request {:request-map
                              (-> http/default-client-options
                                  (assoc :insecure? true
                                         :body (generate-string (assoc params :sign sign))
                                         :follow-redirects false))
                              :method :post
                              :url (props [:yl :gateway-url])})]
    (parse-string (:body resp) true)))

;; paid 
;; {:tid "00000001", :mid "898310148160568", :sign "C8AF705F6BEB68DE51D2DB76BC45ED6F", :payTime "2019-04-02 09:22:27", :targetMid "48020000", :merName "全渠道", :merOrderId "319465033120190402092226658", :totalAmount 1, :msgType "uac.query", :targetSys "ACP", :errMsg "成功[0000000]", :seqId "00476400958N", :refundAmount 0, :targetStatus "00|39", :status "UNKNOWN", :settleDate "2019-04-02", :msgSrc "WWW.TEST.COM", :settleRefId "00476400958N", :responseTimestamp "2019-04-02 10:37:36", :errCode "SUCCESS"}
;; refunded
;; 
(defn order-query [{:keys [merOrderId]}]
  (let [now (System/currentTimeMillis)
        params {:mid (props [:yl :mid])
                :tid (props [:yl :tid])
                :msgType "query"
                :msgSrc (props [:yl :msgSrc])
                :instMid (props [:yl :instMid])
                :merOrderId merOrderId ;;(str (props [:yl :msgSrcId]) ouid)
                :requestTimestamp (time/long->datetime-string now)}
        sign (yl-sign params)
        resp @(async-request {:request-map
                              (-> http/default-client-options
                                  (assoc :insecure? true
                                         :body (generate-string (assoc params :sign sign))
                                         :follow-redirects false))
                              :method :post
                              :url (props [:yl :gateway-url])})]
    (parse-string (:body resp) true)))

;; (str (props [:yl :msgSrcId]) (utils/rand-ints 6) ouid) ;; create new order every time
(defn ouid-from-yl [merOrderId]
  (let [start (+ 6 (count (props [:yl :msgSrcId])))]
    (subs merOrderId start)))

;; {:tid "00000001", :mid "898310148160568", :sign "CC18DF7171AF1FADA84478DDE255C2B7", :payTime "2019-04-02 10:33:17", :targetMid "2015061000120322", :merName "全渠道", :refundFunds "花呗:1", :merOrderId "319439858320190402092727040", :totalAmount 1, :msgType "trade.refund", :targetSys "Alipay 2.0", :cardAttr "BALANCE", :refundOrderId "10001904023263317108194742", :connectSys "ALIPAY", :seqId "00476401095N", :targetStatus "10000", :status "TRADE_SUCCESS", :settleDate "2019-04-02", :msgSrc "WWW.TEST.COM", :settleRefId "00476400969N", :refundFundsDesc "花呗退款0.01元。", :responseTimestamp "2019-04-02 10:33:18", :refundTargetOrderId "2019040222001472081024668928", :errCode "SUCCESS", :refundStatus "SUCCESS"}
(defn refund [{:keys [merOrderId refundAmount]}]
  {:pre [merOrderId refundAmount]}
  (let [now    (System/currentTimeMillis)
        params {:mid              (props [:yl :mid])
                :tid              (props [:yl :tid])
                :msgType          "refund"
                :msgSrc           (props [:yl :msgSrc])
                :instMid          (props [:yl :instMid])
                :merOrderId       merOrderId
                :refundAmount     refundAmount
                :requestTimestamp (time/long->datetime-string now)}
        sign   (yl-sign params)
        body   (generate-string (assoc params :sign sign))
        resp   @(async-request {:request-map
                                (-> http/default-client-options
                                    (assoc :insecure? true
                                           :body body
                                           :follow-redirects false))
                                :method :post
                                :url    (props [:yl :gateway-url])})]
    (t/info "yl-refund returns" (:body resp))
    (parse-string (:body resp) true)))

(comment
  (create-order {:payment-type :alipay :total-amount 10 :ouid "20190327083734"})
  (order-query {:ouid "20190327083733"})
  
  )
