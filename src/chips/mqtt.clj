(ns chips.mqtt
  (:require
   [chips.config                     :refer :all]
   [chips.utils                      :as utils]
   [chips.utility                    :refer [wrap-internal-exception-handler]]

   [cheshire.core :refer [generate-string]]
   [sparrows.misc                    :refer [uuid]]
   [taoensso.timbre                  :as t]
   [com.climate.claypoole            :as cp]
   [clojure.core.async               :as a]
   [clojurewerkz.machine-head.client :as mh])
  (:import
   [org.eclipse.paho.client.mqttv3 MqttConnectOptions]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; aps-broker

(defonce aps-broker-client (atom nil))

(defonce mqtt-client (atom nil))

(defn get-mqtt-client []
  (or @mqtt-client
      (let [client-id (str "aps-" (props [:mqtt :client-id]))
            conn (mh/connect
                  (props [:mqtt :host])
                  {:client-id client-id
                   :on-connection-lost (fn [& args] (t/error "mqtt-connection-lost" args))
                   :on-connect-complete (fn [& args] (t/info "mqtt-connect-complete" args))
                   :opts {:username (props [:mqtt :username])
                          :auto-reconnect true
                          :password (props [:mqtt :password])}})]
        (reset! mqtt-client conn)
        conn)))

(defn mqtt-pub
  ([topic payload]
   (mqtt-pub (get-mqtt-client) topic payload 10))
  ([conn topic payload n]
   (when (pos? n)
     (if (mh/connected? conn)
       (do
         (mh/publish conn topic payload 1))
       (do
         (t/info "mqtt-broker disconnected, waiting to retry" topic n)
         (Thread/sleep 1000)
         (recur conn topic payload (dec n)))))))

(defn async-push [params]
  (cp/future
    (utils/id->pool :mqtt-push-pool)
    (wrap-internal-exception-handler
     (mqtt-pub "mdata" (generate-string params)))))
