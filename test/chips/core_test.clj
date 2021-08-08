(ns chips.core-test
  (:require
   [cheshire.core :refer [generate-string]]
   [sparrows.misc :refer [dissoc-nil-val]]
   [chips.core :refer [app]]
   [chips.config :refer [props]]
   [chips.models.base :refer :all]
   [chips.models.users :as mu]
   [clojure.test :refer :all]
   [clojure.java.jdbc    :as j]
   [clojure.string :as s]
   [clojure.data.json :refer [read-str]]
   [ring.mock.request :as mock :refer [header]]))


(def ^:dynamic *cs-app-id* "ebcad75de0d42a844d98a755644e30")
(def ^:dynamic *cs-token*)
(def ^:dynamic *rand-user*)
(def ^:dynamic *server* "chips")
(def ^:dynamic *chips-req-version* "v2")
(def ^:dynamic *content-type* "application/json")

(defn- attach-headers
  [req]
  (-> req
      (header :cs-app-id *cs-app-id*)
      (header :cs-token *cs-token*)
      (header :chips-req-source *server*)
      (header :chips-req-version *chips-req-version*)))

(defn mock-request
  ([method uri params]
   (app (attach-headers (mock/request method uri params))))
  ([method uri]
   (app (attach-headers (mock/request method uri)))))

(defn json-mock-request
  [method uri params]
  (app (header
        (attach-headers (mock/request method uri (generate-string params)))
        :content-type *content-type*)))

(defn mock-login
  [params]
  (mock-request :post "/account/login" params))

(defn body-as-json
  [resp]
  (when (= 200 (:status resp))
    (read-str (:body resp) :key-fn keyword)))

(defn cs-code
  [resp]
  (:code (body-as-json resp)))

(defn success? [resp]
  (= 200 (cs-code resp)))

(defn get-rand-user
  []
  (rand-nth (j/query (mysql-db) ["select * from csb_account where phone>0"])))

(defmacro deftest-in-debug
  [title & body]
  (when (props :testing-id {:default nil})
    `(deftest ~title ~@body)))

(defmacro with-user
  "Should be called in a test.
  
  Runnning the body with cs-token binded to the provided user."
  [user & body]
  `(let [user# (dissoc-nil-val ~user)
         resp# (mock-login (assoc user# :uid (:phone user#)))
         cs-token# (when (success? resp#) (get-in resp# [:headers "cs-token"]))]
     (is cs-token#)
     (binding [*cs-token* cs-token#
               *rand-user* user#]
       ~@body)))

(deftest-in-debug public-test
  (testing "Ensure in dev mode "
    (when-not (props :testing-id {:default nil})
      (print "Not in dev/test mode, exiting")
      (System/exit 1)))

  (testing "Testing public routes ..."
    (is (= (:status (mock-request :get "/latestapp/1" {:platform "android" :version "1.0.0"}))
           200))))

(deftest-in-debug user-test
  (testing "Testing user routes ..."
    (with-user (get-rand-user)
      (let [roles (mu/get-roles {:account_id (:id *rand-user*)})]
        (is (success? (mock-request :get "/roles")))
        (is (= (count roles)
               (count (:data (body-as-json (mock-request :get "/roles"))))))))))

(deftest mdata-test
  (with-user (get-rand-user)
    (let [weights (j/query (mysql-db) ["select * from csb_weight where account_id=? limit 2" (:id *rand-user*)])
          role (first (mu/get-roles {:account_id (:id *rand-user*)}))]
      (testing "Get mdata"
        (is (success?
             (mock-request :get "/mdata" {:cnt 10
                                          :end (System/currentTimeMillis) }))))
      (testing "Deleting mdata"
        (when (seq weights)
          (is (success? (mock-request :delete (str "/mdata/weight/" (s/join "-" (map :id weights))))))))
      (testing "Repeatedly put mdata"
        (letfn [(mock-put-bsl []
                  (json-mock-request :put "/mdata" {:mdata [{:mtype "bsl"
                                                        :measure_time "2015-06-12 10:44:00"
                                                        :bsl 5.2
                                                        :role_id (:id role)
                                                        :description "post-meal"}]}))]
          (is (success? (mock-put-bsl)))
          (is (pos? (get-in (body-as-json (mock-put-bsl)) [:data 0 :id])))
          (is (apply = (map #(get-in (body-as-json %) [:data 0 :id]) (repeatedly 2 mock-put-bsl))))
          (println "Repeated put bsl: " (:body (mock-put-bsl)))
          (is (= "u" (get-in (body-as-json (mock-put-bsl)) [:data 0 :exists]))))))))
