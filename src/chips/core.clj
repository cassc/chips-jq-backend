(ns chips.core
  (:gen-class)
  (:require
   [chips.config                                :refer [props]]
   [chips.routes.public                         :as rp]
   [chips.routes.users                          :as ru]
   [chips.routes.mdata                          :as rm]
   [chips.routes.mblog                          :refer [mblog-routes]]
   [chips.routes.shop                           :as shop]
   [chips.middleware                            :refer :all]
   [chips.rules                                 :refer :all]
   [chips.codes                                 :refer [cds]]
   [chips.utils                                 :refer [destroy-lazy-pools]]
   [chips.service                               :as s]
   [chips.models.once                           :refer [create-rand-user delete-user!]]
   [chips.models.base                           :refer [start-sql-patcher]]

   [rpc.client                                  :refer [configure-props!]]
   [sparrows.misc                               :refer [wrap-exception]]
   [sparrows.system                             :refer [register-shutdownhook]]
   
   [compojure.core                              :refer [defroutes]]
   [compojure.route                             :as route]
   [noir.util.middleware                        :as noir-middleware]
   [ring.middleware.defaults                    :refer [site-defaults]]
   [ring.middleware.json                        :refer [wrap-json-params wrap-json-body]]
   [ring.middleware.keyword-params              :refer [wrap-keyword-params]]
   [ring.util.response                          :refer [redirect header response status]]
   [ring.middleware.cors :refer [wrap-cors]]
   [noir.response                               :as r]
   [taoensso.timbre                             :as timbre :refer [example-config merge-config! default-timestamp-opts]]
   [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
   [ring.middleware.reload                      :as reload]
   [org.httpkit.server                          :refer [run-server]]))

(defn make-timbre-config
  []
  {:timestamp-opts (merge default-timestamp-opts {:pattern "yy-MM-dd HH:mm:ss.SSS ZZ"
                                                  :timezone (java.util.TimeZone/getTimeZone "Asia/Shanghai")})
   :level          (props :log-level)
   :appenders      {:rolling (rolling-appender
                              {:path    (props :log-file)
                               :pattern :monthly})}})

(defn wrap-allow-cross-origin [handler]
  (fn [req]
    (let [resp (handler req)]
      (->
       resp
       (header "Access-Control-Allow-Origin" "*")
       (header "Access-Control-Allow-Methods" "GET,PUT,POST,DELETE,PATCH,OPTIONS")
       (header "Access-Control-Expose-Headers" "Cs-Token,Cs-Token-Expirytime,Cs-App-Id,Now")
       (header "Access-Control-Allow-Headers" "X-Requested-With,Content-Type,Cache-Control,Cs-Token,Cs-App-Id,Cs-Token-Expirytime,Now,Origin,Accept,Access-Control-Request-Method,Access-Control-Request-Headers")
       (header "Access-Control-Allow-Credentials" "true")))))

#_(defn wrap-allow-cors [handler]
  (wrap-cors handler
             :access-control-allow-origin [".*"]
             :access-control-allow-credentials "true"
             :access-control-allow-methods [:get :put :post :delete :options]
             :access-control-allow-headers #{:accept :content-type :cs-app-id :cs-token :cs-token-expirytime :now}
))

(defn init []
  (timbre/merge-config! (make-timbre-config))
  (s/start-common-jobs)
  (s/start-circle-merger)
  (s/start-pv-db-adder)
  (configure-props! (props :thrift-client))
  
  (register-shutdownhook
   (fn shutdown-handler []
     (timbre/info "Application is shutting down. Cleaning ...")
     (try
       (destroy-lazy-pools)
       (shutdown-agents)
       (catch Throwable e
         (timbre/error e "Error caught when shutting down ..."))
       (finally
         (timbre/info "Cleaning success!")))))
  (start-sql-patcher 2000)

  (timbre/info "chips started successfully"))

(defn destroy []
  (timbre/info "chips is shutting down"))

(defroutes resource-routes
  (route/resources "/")
  (route/not-found ""))

(defn denied
  [req]
  (r/json
   (cds :session-expired)))

(def app
  (let [x-routes [rp/public-routes
                  mblog-routes
                  ru/user-routes
                  rm/mdata-routes
                  shop/shop-routes
                  resource-routes]]
    (noir-middleware/app-handler
     (if (props :testing-id {:default nil})
       (cons rp/dev-routes x-routes)
       x-routes)
     :ring-defaults (-> site-defaults
                        ;;(assoc-in [:security :anti-forgery] false)
                        (assoc-in [:security] nil)
                        (assoc-in [:responses :default-charset] "utf-8"))
     :access-rules [{:uris ["/*"]
                     :rule user-page
                     :on-fail denied}]
     ;; wrappers from outer->inner
     :middleware [wrap-allow-cross-origin
                  ;; wrap-allow-cors
                  wrap-request-logger wrap-companyid-by-token wrap-timeout-check
                  wrap-clean-params wrap-keyword-params
                  wrap-json-params wrap-cstoken wrap-appid-check])))


(defn start-web
  [& [dev?]]
  (timbre/info "Starting web server ...")
  (let [options
        {:port (props :port {:default 3333})
         :max-body 104857600 ;; 100M
         :ip (props :host {:default  "127.0.0.1"})}]
    (if dev?
      (run-server (reload/wrap-reload #'app {:dirs ["src" "resources"]}) options)
      (run-server #'app options))))


(defn -main [& [opt & args]]
  (cond
    (or (nil? opt) (= opt "web"))
    (do
      (init)
      (start-web (first args)))

    (= opt "c-r-u")
    (apply create-rand-user args)

    (= opt "d-u")
    (delete-user! args)

    :else
    (prn "Example usage: lein run command & args")))

;; NOTE cider-refresh may break defonce binding.
;;      restart repl in this case
(defonce server-state (atom nil))
(defonce initer (delay (init)))

(defn start-dev-server []
  (reset! server-state (do
                         @initer
                         (start-web true))))

(defn stop-server []
  (when @server-state
    (@server-state)))

(comment
  (start-dev-server)
  (stop-server)
  
  )
