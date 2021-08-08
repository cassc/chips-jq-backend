(ns chips.kjt
  (:require
   [chips.utils :refer [dissoc-nil-val]]
   [chips.config :refer [props]]
   [sparrows.http :as http :refer [async-request]]
   [sparrows.cypher :refer [form-encode sha1 md5 rand-str]]
   [sparrows.system :refer [get-mime]]
   [sparrows.time :as time]
   [clojure.java.io :as io]
   [cheshire.core :refer [parse-string generate-string]]
   [taoensso.timbre :as t]
   [clojure.string :as s]
   [com.rpl.specter :as specter :refer [transform]])
  (:import
   [java.util HashMap]
   [com.kjtpay.gateway.common.util.security SecurityService]))

(defn rsa-encrypt [^String ss]
  (let [security (SecurityService. (props [:kjt :rsa :privkey])
                                   (props [:kjt :kjt-pubkey]))]
    (.encrypt security ss "UTF-8")))

(defn rsa-sign [params]
  (let [security (SecurityService. (props [:kjt :rsa :privkey])
                                   (props [:kjt :kjt-pubkey]))]
    (.sign security (reduce-kv (fn [m k v] (assoc m (name k) (str v))) {} params) "UTF-8")))

(defn rsa-verify [{:keys [sign] :as params}]
  (let [security (SecurityService. (props [:kjt :rsa :privkey])
                                   (props [:kjt :kjt-pubkey]))]
    (.verify security (reduce-kv (fn [m k v] (assoc m (name k) (str v))) {} params) sign "UTF-8")))

;; REQ:
;; request_no: 20190223151514862300
;; service: instant_trade
;; version: 1.0
;; partner_id: 200001725745
;; charset: UTF-8
;; sign_type: RSA
;; sign: 1R79S1BwUQzJegJtLULdILdhcR41NQUx3Fh%2FxKB%2FtZijhrpsf568G1LXtuyRTe595k8Wtn9YExYehuJQUc5ffdzgr9g3F90b4W8zPczSmnnObRZviU%2BYB7u%2F2gGjdudOEt%2BqMMQAiTs9I6n6tuTSg1vuoKE7nMtXSHCUo0jw8bw%3D
;; timestamp: 2019-02-23%2015%3A15%3A14
;; format: JSON
;; biz_content: 

;; RESP:  {"code":"F40003","sub_code":"TSF10063","msg":"业务处理失败","sub_msg":"交易内金额不匹配","sign":"wfLJbwV+TfYHPh/D4HAVob83bnvv5zDEAkCYbLA+vBBA1IjLs8qqabku9TYQG8uXayPWZYzYKKjsh159ZulrAHkFamMhSGv53bhM8F6xi5NBH2JEOG+qcMAyJ5r03gBWX0pbg5UGBKX1UxASjFaVVxKc0hHLMcEYxH4ETnDEIUWPcbwkQ6FK8hKQiqlwgnRJ9b+1xYV1vcOvn4Msv/F7RHsF9g4xa2kencdB7X1IO7Tv9ERIPf1Hmemhc3O6DibjVw6RyPJW8DaE0cq5npnUMzJ4tokd8PVzbEfubDGeDUko0THSo4rtMaKtYgpisaivJ6KETA0kVd5lq1xHS/oJow==","charset":"UTF-8","sign_type":"RSA"}

;; https://doc.kjtpay.com/blog/wgqq/Gateway
(defn gateway-request [service enc-params]
  (let [now (System/currentTimeMillis)
        biz (->> enc-params generate-string rsa-encrypt)
        params {:partner_id (props [:kjt :mchid])
                :charset "UTF-8"
                :sign_type "RSA"
                :request_no (str now) ;; 请求号，字母数字下划线，确保每次请求唯一
                :service service
                :version "1.0"
                :timestamp (str (time/long->datetime-string now))
                :format "JSON"
                :biz_content biz}
        sign (rsa-sign params)
        form-params (reduce-kv (fn [m k v]
                                 (assoc m k (form-encode (str v))))
                               {}
                               (assoc params :sign sign))
        resp @(async-request {:request-map
                              (-> http/default-client-options
                                  (assoc :insecure? true
                                         :form-params form-params
                                         :follow-redirects false))
                              :method :post
                              :url (props [:kjt :gateway])})]
    (t/info "kjt-req" enc-params)
    (t/info "kjt-resp" resp)
    resp))

;; {:code "S10000", :msg "接口调用成功", :sign "ASgxkCLHAf69vzhjuJsZKkXS4nB1h4AWzLI+yiX4wpjJq43SyDaKZF9bFtjEV+LsDIo6fFjWmFbMLEoamcPvXN/m5ufdRrYRfjj82PMO1l3Eb4DUOjqYzn1WFbSXs8gqSFUsRA1Otjt1vE1Gk5NuNUIVMTU9Zn2SK4MT/vXuLlM=", :charset "UTF-8", :sign_type "RSA", :biz_content {:amount "0.10", :payer_name "陈离", :trade_no "103155425346875352153", :royalty_list [], :payee_id "200003041770", :payer_id "100003092000", :seller_actual_amount "0.10", :out_trade_no "20190403085639193", :status "REFUND_REQUEST", :payee_name "海尔信息科技（深圳）有限公司", :modify_time "2019-04-03 09:06:52", :partner_id "200003041770"}}
(defn query [{:keys [ouid]}]
  (let [{:keys [headers body status]}
        (gateway-request "trade_query" {:out_trade_no ouid})]
    (when (= 200 status)
      (-> body
          (parse-string true)))))


;; 退款请求成功
;; {:code "S10000", :msg "接口调用成功", :sign "M7LMYa6uhUm5NwL5vs6uFYm698CjadCa9pC6Wpc4Tyej/G2GujQrkc6m6kGc3JgdBrLGOqHjQ0u1Z4anJlbvb+MIopRQz3d9ldkDs5OdCMyAqLTphlHKX28e5QAji1H3OlQRpVXkvIpL4nbpQzV0SIofuZFrW9ooawoNofM1AYo=", :charset "UTF-8", :sign_type "RSA", :biz_content {:out_trade_no "20190403085639193", :trade_no "103155425346875352153", :status "P"}}
;; 同一单重复请求退款
;; {:code "F40004", :sub_code "TSF11020", :msg "业务处理失败", :sub_msg "参数不合法:可退付款的金额[0.00]小于退款金额[0.10]", :sign "lFyNIFCQrc2tshDcjFRdm0Z53JkGazpGyQjlfVIPG/nN17qiK7mbZEB62xMoHqDAeFU8JNdpD0lyDO8cXn9jTsnjBA9e/5CFDBSSQB/G1Iv9RR8jUJ2c7dGM+HOg9RlWJ+ugjImvs/2xksIuRA92fV06FYkkcZAWm51QM2iqUmI=", :charset "UTF-8", :sign_type "RSA"}
;; 
;; {"code":"F40003","sub_code":"DMF11006","msg":"业务处理失败","sub_msg":"余额不足","sign":"RyUi6RqJmXHiG6tH0ZraKa4LQcEwe5n4CJmLBvgn/CoK9yK5EYVV/nEvZ5ZEWRd5wo070jFasjTAvXbaqFf15YD5xskOG3T7UDI3gAv5SJbk7xsSTuwOxNwE1ATUKo8irFE7WUPE1lKUWHNKg8tR6cfgM2GA+0vGS6hC4z9/W50=","charset":"UTF-8","sign_type":"RSA"}
(defn refund [ouid refund-amount]
  (let [{:keys [headers body status]}
        (gateway-request "trade_refund" {:out_trade_no (str ouid "_" (rand-str 6))
                                         :orig_out_trade_no ouid
                                         :refund_amount (format  "%.2f" (/ refund-amount 100.0))
                                         :notify_url (props [:kjt :notify-url])})]
    (when (= 200 status)
      (-> body
          (parse-string true)))))


;; (sign {:a 1})
(comment
  (refund "20190414123427060" 1)
  (refund "20190319225438101" 10)
  (refund "20181206175752729" 10)
  ;; SDK
  (let [now (System/currentTimeMillis)
        x-trade-info [{:out_trade_no (str now)
                       :subject "测试"
                       :price "0.1"
                       :quantity "1"
                       :total_amount "0.1"
                       :payee_identity_type "2"
                       :payee_identity "3504163011@qq.com"}]
        terminal-info {:terminal_type "01" ;; 00电脑，01手机，02平板设备，03可穿戴设备，04数字电视，99其他
                       :ip "122.224.203.210"}]
    (println
     (gateway-request "instant_trade" {:payer_identity "anonymous"
                                       :payer_ip "122.224.203.210"
                                       :biz_product_code "20601"
                                       :cashier_type "API"
                                       :pay_method {"pay_product_code" "68"
                                                    "amount" "0.1"
                                                    "bank_code" "WECHATAPP"
                                                    "target_organization" "WECHAT"}
                                       :trade_info x-trade-info
                                       :terminal_info terminal-info})))

  ;; H5
  (let [now (System/currentTimeMillis)
        x-trade-info [{:out_trade_no (str now)
                       :subject "测试"
                       :price "0.1"
                       :quantity "1"
                       :total_amount "0.1"
                       :payee_identity_type "2"
                       :payee_identity "3504163011@qq.com"
                       :notify_url (props [:kjt :notify-url])}]
        terminal-info {:terminal_type "01" ;; 00电脑，01手机，02平板设备，03可穿戴设备，04数字电视，99其他
                       :ip "122.224.203.210"}]
    (t/info
     (get-in (gateway-request "instant_trade" {:payer_identity "anonymous"
                                               :payer_ip "122.224.203.210"
                                               :biz_product_code "20601"
                                               :cashier_type "H5"
                                               :trade_info x-trade-info
                                               :terminal_info terminal-info})
             [:headers :location])))
  )

