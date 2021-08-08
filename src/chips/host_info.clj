(ns chips.host-info
  (:require
   [clojure.string :as s]
   [taoensso.timbre :as t])
  (:import
   [java.net NetworkInterface InetAddress]))

(def host-ip-store (atom #{}))
(def host-mac-store (atom #{}))

(defn notable-ip? [ip]
  (and ip
       (not (#{"127.0.0.1" "localhost"} ip))
       (not (re-seq #"^0:0:0.*" ip))))

(defn inet->mac [inet]
  (let [mac-bytes (.getHardwareAddress (NetworkInterface/getByInetAddress inet))]
    (reduce
     (fn [s b]
       (str s (format "%02X" b)))
     ""
     mac-bytes)))


(defn resolve-host-ips []
  (let [eis (NetworkInterface/getNetworkInterfaces)]
    (while (.hasMoreElements eis)
      (let [n (.nextElement eis)
            ee (.getInetAddresses n)]
        (while (.hasMoreElements ee)
          (let [i (.nextElement ee)
                ip (.getHostAddress i)]
            (when (notable-ip? ip)
              (t/info "Host ip" ip "noted")
              (swap! host-ip-store conj ip)
              (swap! host-mac-store conj (inet->mac i)))))))))

(defn get-host-name []
  (.getHostName (InetAddress/getLocalHost)))

(defn hostinfo []
  ;;(delay (str "[" (s/join "," @host-ip-store) "](" (s/join "," @host-mac-store) ")"))
  (when-not (seq @host-mac-store)
    (resolve-host-ips))
  (if (seq @host-mac-store)
    (str "<" (get-host-name) ">[" (s/join "," @host-mac-store) "]")
    (str "<" (get-host-name) ">[NO NETWORK]")))
