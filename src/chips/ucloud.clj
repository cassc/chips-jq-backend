(ns chips.ucloud
  (:require
   [chips.utils :refer [dissoc-nil-val]]
   [chips.config :refer [props]]
   [sparrows.http :refer [async-request]]
   [sparrows.cypher :refer [form-encode sha1 md5]]
   [sparrows.system :refer [get-mime]]
   [clojure.java.io :as io]
   [cheshire.core :refer [parse-string]]
   [taoensso.timbre :as t]
   [clojure.string :as s]
   [com.rpl.specter :as specter :refer [transform]]
   [camel-snake-kebab.core :refer [->PascalCase ->kebab-case]])
  (:import
   [chips HmacSHA1]))

(def base-params {:action nil
                  :public-key nil
                  :signatures nil
                  :project-id nil})

(defonce token-store (atom {:token nil :expire-at 0}))

(def kept-keys
  "These keys will NOT be transformed to PascalCase"
  #{"CPU" :CPU})

(defn sign
  ([params]
   (sign (props [:ucloud :ufile :public-key])
         (props [:ucloud :ufile :private-key])
         params))
  ([public-key private-key params]
   (let [http-params (->> (assoc params :public-key public-key)
                          dissoc-nil-val
                          (transform [specter/MAP-KEYS (complement kept-keys)] (comp ->PascalCase name)))
         signature (->> http-params
                        (into (sorted-map))
                        (mapv #(str (name (first %)) (second %)))
                        (s/join "")
                        (#(str % private-key))
                        sha1)]
     (assoc http-params "Signature" signature))))

(defn parse-ucloud-response [{:keys [body status error] :as r}]
  (if (or error (not= status 200))
    (do
      (println "Error:" r)
      (throw (RuntimeException. "Request failed")))
    (parse-string body keyword)))

(defn ucloud-request [params]
  (let [response @(async-request
                   {:method :get :url "https://api.ucloud.cn" :query-params (sign params)})]
    (parse-ucloud-response response)))

(defn refresh-token []
  (when (< (- (:expire-at @token-store) (System/currentTimeMillis)) 60000)
    (let [{:keys [expire-in ret-code message access-token]} (transform
                                                             [specter/MAP-KEYS]
                                                             (comp keyword ->kebab-case name)
                                                             (ucloud-request {:action "GetAccessToken" :Region "cn-bj2"}))]
      (t/info "ucloud token reset to" access-token)
      (when (zero? ret-code)
        (reset! token-store
                {:token access-token :expire-at (+ (System/currentTimeMillis) (* expire-in 1000))})))))

(defn touch-token []
  (refresh-token)
  (:token @token-store))

(comment
  (ucloud-request {:action "DescribeBucket" :BucketName (props [:ucloud :ufile :bucket-name])})
  (ucloud-request {:action "GetAccessToken" :Region "cn-bj2"})
  (refresh-token)
  )

(defn hmac-sha1
  ([data]
   (hmac-sha1 (props [:ucloud :ufile :private-key]) data))
  ([key data]
   (.sign (HmacSHA1.) key data)))

(defn ucloud-put-file
  "文档不正确，参考其java代码PutSender#makeAuth
  https://docs.ucloud.cn/api/ufile-api/put_file"
  [^java.io.File file]
  (let [length (.length file)
        f-md5 (md5 file)
        content-type (get-mime file)
        str-to-sign (str "PUT" "\n"
                         f-md5 "\n"
                         content-type "\n"
                         "\n"
                         "/"
                         (props [:ucloud :ufile :bucket-name])
                         "/"
                         f-md5)
        headers {"Authorization" (format "Ucloud %s:%s"
                                         (props [:ucloud :ufile :public-key])
                                         (hmac-sha1 str-to-sign))
                 "Content-Length" length
                 "Content-Type" content-type
                 "Content-MD5" f-md5}
        url (format "http://%s/%s" (props [:ucloud :ufile :cdn]) f-md5)
        
        resp @(async-request
               {:method :put :url url :body file :headers headers})]
    (parse-ucloud-response resp)
    (t/info "upload ufile success" url)))

(comment
  (ucloud-put-file (clojure.java.io/file "/home/garfield/lib/bingbgs/AddoElephants_ZH-CN13744097225_1920x1080.jpg"))
  )

(defn- upload-image [^java.io.File f]
  (when (and (.isFile f)
             (pos? (.length f))
             (s/includes? (get-mime f) "image"))
    (ucloud-put-file f)))

(defn load-pictures [^String root]
  (run! upload-image (file-seq (io/file root))))
 
