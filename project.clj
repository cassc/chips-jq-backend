(defproject chips "3.1.201-SNAPSHOT"
  :description "haier bluetooth scale"
  :url "http://www.tookok.com"
  :license {:name "chipsea"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.reader "1.0.0-beta1"] ;; 0.9.2
                 [compojure "1.5.1" :exclusions [org.clojure/tools.reader]]
                 [hiccup "1.0.5"]
                 [http-kit "2.2.0"]
                 [com.postspectacular/rotor "0.1.0"]
                 [lib-noir "0.9.9" :exclusions [[ring/ring-defaults]]]
                 ;; [clj-time "0.11.0"]
                 ;;[clojurewerkz/quartzite "2.0.0" :exclusions [[clj-time]]]
                 [org.clojure/java.jdbc "0.6.1"]
                 ;; [org.mariadb.jdbc/mariadb-java-client "1.4.6"]
                 [mysql/mysql-connector-java "5.1.34"]
                 [com.taoensso/carmine "2.14.0" :exclusions [org.clojure/data.json
                                                             com.taoensso/encore]]
                 [org.clojure/core.memoize "0.5.9"]
                 [com.zaxxer/HikariCP "2.4.7"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojars.august/sparrows "0.2.5" :exclusions [org.clojure/tools.reader clj-http com.taoensso/timbre]]
                 [cassc/clj-props "0.1.2"]
                 [clj-http "3.1.0"]
                 [com.taoensso/timbre "4.7.3"]
                 [ring-server "0.4.0"]
                 [ring/ring-json "0.4.0"] ;; handling json-body request
                 [ring/ring-defaults "0.2.1"] ;; supports auto set utf8 encoding in content-type
                 [ring-cors/ring-cors "0.1.13"] 
                 [org.slf4j/slf4j-jdk14 "1.7.21"]
                 [dk.ative/docjure "1.10.0"] ;; write office with clojure
                 ;; [dire "0.5.3"] ;; pre-post conditions
                 [commons-validator/commons-validator "1.5.1" :exclusions [commons-logging/commons-logging]]
                 [org.clojure/java.data "0.1.1"]
                 ;; shell
                 [com.climate/claypoole "1.1.3"]
                 [camel-snake-kebab "0.4.0"]

                 ;; location detection
                 [com.maxmind.geoip2/geoip2 "2.12.0"]

                 ;; thrift rpc
                 [com.taoensso/nippy "2.12.1"]
                 [cs-thrift-client "0.1.2"]
                 [markdown-clj "0.9.89"]
                 [hickory "0.6.0" :exclusions [org.clojure/clojure]]

                 ;; oss
                 [com.aliyun/aliyun-java-sdk-sts "3.0.0"]
                 [com.aliyun/aliyun-java-sdk-core "3.5.0"]
                 
                 [net.glxn.qrgen/javase "2.0"] ;; qrcode
                 [org.clojure/core.async "0.2.385"]
                 [cheshire "5.6.3"]
                 [com.rpl/specter "1.0.0"]
                 [aleph "0.4.3"]
                 ;; [com.thoughtworks.xstream/xstream "1.4.7"]
                 [clojurewerkz/machine_head "1.0.0"]

                 ;; mvn  deploy:deploy-file -Dfile=healthscale_fx\(CS_BIAD_V431\).jar -DartifactId=health -Dversion=431 -DgroupId=com.chipsea -Dpackaging=jar -Durl=file:/home/garfield/.m2/repository
                 [com.chipsea/health "431"]
                 ;; mvn  deploy:deploy-file -Dfile=gateway-common-0.0.1-SNAPSHOT.jar -DartifactId=gateway -Dversion=0.0.1 -DgroupId=com.kjtpay -Dpackaging=jar -Durl=file:/home/garfield/.m2/repository
                 [com.kjtpay/gateway "0.0.1"]
                 [org.apache.commons/commons-lang3 "3.4"]
                 ]
  ;;:uberjar-name "chips-standalone.jar"
  :profiles {:uberjar {:aot [chips.core]}
             :production
             {:ring
              {:open-browser? false, :stacktraces? false, :auto-reload? false :reload-paths ["resources" "src"]}}
             :dev
             {:ring {:open-browser? false, :auto-reload? true, :port 3333 :auto-refresh? false}
              :source-paths ["dev" "src-dev"]
              :dependencies [[ring/ring-mock "0.3.0"]
                             [clj-gatling "0.8.1"] ;; load testing
                             [ring/ring-devel "1.5.0" :exclusions [clj-time]] 
                             [criterium "0.4.4"]]}}
  :main chips.core
  :omit-source true
  :java-source-paths ["src-java" "thrift/gen-java"]
  ;; :global-vars {*warn-on-reflection* true}
  ;; for windows user, use `-Dfile.encoding=UTF8` is required
  :jvm-opts ["-Dfile.encoding=UTF8" "-Djava.library.path=/home/garfield/lib/include" "-XX:-OmitStackTraceInFastThrow"]
  ;;:jvm-opts ["-server" "-Xms1536m" "-Xmx1536m" "-XX:+DisableExplicitGC" "-XX:+UseCMSInitiatingOccupancyOnly" "-XX:+UseConcMarkSweepGC" "-XX:+CMSParallelRemarkEnabled" "-XX:+HeapDumpOnOutOfMemoryError" "-XX:HeapDumpPath=/tmp/vhs-dump.hprof" ]
  ;; "-XX:+UseGCLogFileRotation" "-XX:NumberOfGCLogFiles=10" "-XX:GCLogFileSize=100M" "-Xloggc:/tmp/vhs-jvm-gc.log" "-XX:+UseGCLogFileRotation" "-XX:NumberOfGCLogFiles=10" "-Xmn400m" "-XX:SurvivorRatio=2" "-XX:+AggressiveOpts" "-XX:+UseBiasedLocking" "-XX:MaxMetaspaceSize=128m" "-XX:LargePageSizeInBytes=4m"
  :plugins [[ns-graph "0.1.0"]]
  :ns-graph {:name "cs"
             :abbrev-ns true
             :include ["cs.*"]}
  :ring {:handler chips.core/app
         :init    chips.core/init
         :destroy chips.core/destroy}
  :aliases {"dev-run" ["run" "web" "dev"]
            "load-test" ["test" ":only" "chips.load-test"]}
  )

;; rsync -avuhc --exclude-from="/home/garfield/bin/projects-excluded" /home/garfield/projects/clojure/chips/* "lixun@112.74.114.64:/home/lixun/projects/chips/"
