(ns chips.oss
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
   [com.rpl.specter :as specter :refer [transform]])
  (:import
   [com.aliyuncs.sts.model.v20150401 AssumeRoleRequest]
   [com.aliyuncs DefaultAcsClient]
   [com.aliyuncs.http ProtocolType]
   [com.aliyuncs.http MethodType]
   [com.aliyuncs.profile DefaultProfile]))

(defn acs-request [id]
  (let [;; RoleSessionName 是临时Token的会话名称，自己指定用于标识你的用户，主要用于审计，或者用于区分Token颁发给谁
        session-name (str "ch-" id)
        {:keys [endpoint access-key-id access-key-secret role-arn policy expire-secs]} (props :aliyun-sts)
        
        profile (DefaultProfile/getProfile "cn-hangzhou" access-key-id access-key-secret)
        client (DefaultAcsClient. profile)
        request (doto (AssumeRoleRequest.)
                  (.setVersion "2015-04-01")
                  (.setMethod MethodType/POST)
                  (.setProtocol ProtocolType/HTTPS)
                  (.setRoleArn role-arn)
                  (.setRoleSessionName session-name)
                  (.setPolicy policy)
                  (.setDurationSeconds expire-secs))
        response (.getAcsResponse client request)]
    (try
      {:StatusCode "200"
       :AccessKeyId (.. response getCredentials getAccessKeyId)
       :AccessKeySecret (.. response getCredentials getAccessKeySecret)
       :SecurityToken (.. response getCredentials getSecurityToken)
       :Expiration (.. response getCredentials getExpiration)}
      (catch Exception e
        {:StatusCode "500"
         :ErrorCode (.getErrCode e)
         :ErrorMessage (.getErrMsg e)}))))
