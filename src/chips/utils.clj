(ns chips.utils
  (:require
   [chips.config          :refer :all]
   [chips.codes           :refer [cds]]

   [cheshire.core :refer [parse-string]]
   [clojure.edn           :as e]
   [taoensso.timbre       :as t]
   [sparrows.misc         :as sm :refer [str->num]]
   [sparrows.cypher       :refer :all]
   [sparrows.http         :refer :all]
   [sparrows.time         :as time :refer [long->datetime-string long->date-string date-string->long]]
   [hiccup.core           :refer [html]]
   [com.climate.claypoole :as cp]
   [clojure.string        :as s]
   [clojure.java.io       :as io]
   [clojure.core.async :as a])
  (:import
   [java.time.format DateTimeFormatter]
   [java.time Instant ZoneId LocalDateTime ZoneOffset]
   com.google.zxing.EncodeHintType
   com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
   net.glxn.qrgen.javase.QRCode
   [chips AlgoProfileBuilder SexEnum]
   ;; [com.chipsea.health CSAlgorithmUtils CSAlgorithmUtils$Profile]
   [java.net InetAddress]
   [java.io File]
   [java.nio.file Files CopyOption]
   [org.apache.commons.validator.routines EmailValidator]
   [java.util UUID HashMap HashSet Date]))

(defmacro call-method [inst m & args]
  `(. ~inst ~(symbol (eval m)) ~@args))

(defn super-of?
  "Determines if `me` is a super class/interface of `that`.

  Usage
  `(super-of? Throwable (.getClass e#))`"
  [p c]
  (.isAssignableFrom p c))


(defn- wrap-time
  "Returns a wrapped function of the original, logs execution time of
  this function if :enable-time-logging is enabled. "
  ([f]
   (wrap-time f f))
  ([key f]
   (if (props :enable-time-logging {:default nil})
     (fn [& args]
       (let [start (System/currentTimeMillis)]
         (try
           (apply f args)
           (finally
             (t/info "Runtime of" key (- (System/currentTimeMillis) start))))))
     f)))

(defmacro redef-with-timer
  "Redefine a function with by wrapping a time logger around it. Example usage:

  `
  (defn slow-run
  [a]
  (Thread/sleep 1111)
  (prn a))

  (redef-with-timer slow-run)
  (redef-with-timer \"slow-run\" slow-run)`"

  ([func]
   `(def ~func (#'chips.utils/wrap-time '~func ~func)))
  ([key func]
   `(def ~func (#'chips.utils/wrap-time ~key ~func))))

(defmacro wrap-nil-on-error
  "Eval (list* func args) in a try catch block. If any error is caught,
  returns nil"
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (warn e# "error ignored")
       nil)))

(defn- int-entry-to-map
  [m [k v]]
  (assoc m k (or (sm/str->num v) v)))

;; this fixes the following bug when calling to-java for ->xml conversion
;; java.lang.IllegalArgumentException: argument type mismatch
(defn maybe-vals->int
  [m]
  (reduce int-entry-to-map {} m))

(defn rand-ints
  [n]
  (reduce str (repeatedly n #(rand-int 10))))

(defn phone?
  [s]
  (re-seq #"^1[1-9][0-9]{9}$" (str s)))

(defn email?
  [s]
  (when-let [s (sm/lowercase-trim s)]
    (.. EmailValidator getInstance (isValid s))))

(defn uid->type
  [uid]
  (cond
    (phone? uid) :phone
    (email? uid) :email))

(defn cid->signature
  [cid]
  (props [:signatures cid] {:default (props [:signatures :default])}))

(defn- maybe-decrypt
  "Decrypt if the input is a map, ie., like `{:enc ...}`"
  [pass]
  (if (string? pass)
    pass
    (try
      (decrypt-aes (:enc pass) aes-key)
      (catch Exception e
        (t/error e (str "Decrypt " pass " failed!"))))))

(def default-sender-map
  (delay
   (update-in
    (props :email-sender)
    [:smtp-pass]
    maybe-decrypt)))


(defn send-plain-email
  "Send plain text to the receiver. Should return nil when sent failed."
  [address subject msg]
  (io!
   (try
     (send-email
      (assoc @default-sender-map
             :to (if (sequential? address)
                   (vec address)
                   [address]) :subject subject :text msg :from (:smtp-user @default-sender-map)))
     (catch Exception e
       (t/error e (str "Failed sending message to " address ":" subject ":" msg))
       nil))))

(declare id->pool)

(def hostinfo (str (java.net.InetAddress/getLocalHost)))

(defn dissoc-nil-val
  "Remove all entries with nil val

  Should use `dissoc-empty-val' normally."
  [m]
  (loop [ks (keys m)
         m m]
    (if (seq ks)
      (if-not (get m (first ks))
        (recur (rest ks) (dissoc m (first ks)))
        (recur (rest ks) m))
      m)))


(defn uuid
  "Return uuid without hyphens"
  []
  (.. UUID randomUUID toString (replace "-" "")))


(defn dissoc-empty-val
  "Remove all entries with nil or empty val"
  [m]
  (if (map? m)
    (loop [ks (keys m)
           m m]
      (if (seq ks)
        (let [v (get m (first ks))]
          (if (cond
                (string? v)          (sm/lowercase-trim v)
                (instance? Number v) v
                (sequential? v)      (seq v)
                :else                v)
            (recur (rest ks) m)
            (recur (rest ks) (dissoc m (first ks)))))
        m))
    m))

(defn dissoc-empty-val-and-trim
  "Like `dissoc-empty-val' but also trims string value"
  [m]
  (if (map? m)
    (loop [ks (keys m)
           m m]
      (if (seq ks)
        (let [k (first ks)
              v (get m k)
              more (rest ks)]
          (cond
            (string? v)          (if (s/blank? v)
                                   (recur more (dissoc m k))
                                   (recur more (assoc m k (s/trim v))))
            (sequential? v)      (recur more (assoc m k (seq v)))
            :else                (recur more m)))
        m))
    m))

(defn- ^long to-long [^java.util.Date date]
  (.getTime date))

(defn convert-sql-val
  "Performs conversion from sql data types to clojure/java data types, or more precisely

  - convert java.sql.Date or java.sql.Timestamp to string
  - convert Float to string. See `cheshire.generate/number-dispatch` which
  promotes float to double when convertingx clojure data to json - dissoc
  nil vals"
  [m]
  (loop [ks (keys m)
         m m]
    (if (seq ks)
      (if-let [v (get m (first ks))]
        (cond
          (float? v)
          (recur (rest ks) (assoc m (first ks) (str v)))

          (instance? java.sql.Date v)
          (recur (rest ks) (assoc m (first ks)
                                  (->> v to-long time/long->date-string)))

          (instance? java.sql.Timestamp v)
          (recur (rest ks) (assoc m (first ks)
                                  (->> v to-long time/long->datetime-string)))

          :else (recur (rest ks) m))

        (recur (rest ks) (dissoc m (first ks))))
      m)))

(defn convert-db-val
  "Performs conversion from sql data types to clojure/java data types, or more precisely

  - convert java.sql.Date or java.sql.Timestamp to string
  - convert Float to string. See `cheshire.generate/number-dispatch` which
  promotes float to double when convertingx clojure data to json - dissoc
  nil vals"
  [m]
  (loop [ks (keys m)
         m m]
    (if (seq ks)
      (if-let [v (get m (first ks))]
        (cond
          (float? v)
          (recur (rest ks) (assoc m (first ks) (str v)))

          (instance? java.sql.Date v)
          (recur (rest ks) (assoc m (first ks)
                                  (->> v to-long time/long->date-string)))

          (instance? java.sql.Timestamp v)
          (recur (rest ks) (assoc m (first ks)
                                  (->> v to-long time/long->datetime-string)))

          (instance? (Class/forName "[B") v)
          (recur (rest ks) (assoc m (first ks) (base64-encode v)))
          
          :else (recur (rest ks) m))

        (recur (rest ks) (dissoc m (first ks))))
      m)))

(defn md5sum?
  [s]
  (re-seq #"^[0-9a-f]{32}$" s))


(defn- hash-with-salt
  [salt password]
  (str salt ":" (sha512 (str (if (md5sum? password) password (md5 password))
                             (md5 salt)))))

(defn encrypt
  "Input password can be either md5-hased or not"
  [password]
  {:pre [(not (s/blank? password))]}
  (hash-with-salt (uuid) password))

(defn check-password
  "Input password can be either md5-hased or not. "
  [password correct-hash]
  (let [[salt _] (s/split correct-hash #":")]
    (= (hash-with-salt salt password) correct-hash)))

(def seven-days-millis
  (memoize (fn [] (* 7 24 3600 1000))))

(defn filename-secure?
  "Check whether a single file contains only alphanumeric characters and underscore"
  [^String filename]
  (when (and filename (string? filename))
    (and (re-seq #"^[a-zA-Z0-9_\.]+$" filename) (not (.contains filename "..")))))


(defn append-as-bytes
  "Append bytes in `source` file to the end of `target` file"
  [^File source ^File target]
  (with-open [in  (io/input-stream source)
              out (io/output-stream target :append true)]
    (io/copy in out)))

(defn move
  [^File source ^File target]
  (Files/move (.toPath source) (.toPath target) (into-array CopyOption [])))

(defn force-move
  [^File source ^File target]
  (try
    (when (.exists target)
      (try
        (let [dups (io/file (props :cloud-file-temp-root) (str (.getName target) ".dups"))]
          (t/warn "Moving duplicate file from" (.getAbsolutePath target) "-->" (.getAbsolutePath dups))
          (Files/move (.toPath target) (.toPath dups) (into-array CopyOption [])))
        (catch Exception e (t/warn e))))
    (Files/move (.toPath source) (.toPath target) (into-array CopyOption []))
    (catch Exception e
      (t/info e (str "Move file fails: " (.getAbsolutePath source) " --> " (.getAbsolutePath source) )))))

(defn delete-if-exists
  [^File f]
  (when (and f (.exists f))
    (.delete f)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Thread pools using com.climate.claypoole
;; Usage::
;; register pool(s) when server starts
;; (register-lazy-pool :email-sender-pool (delay (cp/threadpool 1 :daemon true :name "email-sender-pool")))
;;
;; Get pool by id
;; (id->pool :email-sender-pool)
;;
;; destroy when server shutdown
;;  (register-shutdownhook
;; (fn shutdown-handler []
;;   (timbre/info "Application is shutting down. Cleaning ...")
;;   (try
;;     (destroy-lazy-pools)
;;     (catch Throwable e
;;       (timbre/error e "Error caught when shutting down ..."))
;;     (finally
;;       (timbre/info "Cleaning success!")))))

(def ^:private chips-pools
  (atom {}))

(defn destroy-lazy-pools
  []
  (t/info "Shutting down all thread pools ... ")
  (doseq [p (seq (vals @chips-pools))]
    (when (and p (realized? p))
      (.shutdown @p))))

(defn id->pool
  [id]
  (when-let [p (id @chips-pools)]
    @p))

(defn- register-lazy-pool
  "Register a pool for later use.
  id should be a unique keyword
  pool should be a delay (cp/pool ...) instance"
  [id pool]
  {:pre [id pool (keyword? id) (delay? pool)]}
  (do
    (swap! chips-pools assoc id pool)
    pool))

(register-lazy-pool :email-sender-pool (delay (cp/threadpool 1 :daemon true :name "email-sender-pool")))
;; pool shared by
;;  should be used only for executing non-critical tasks
(register-lazy-pool :shared-pool (delay (cp/threadpool 2 :daemon true :name "shared-pool")))
(register-lazy-pool :circle-merger (delay (cp/threadpool 4 :daemon false :name "cirecle-merger")))
(register-lazy-pool :moments-pool (delay (cp/threadpool 8 :daemon false :name "moments-pool")))
(register-lazy-pool :async-last-log (delay (cp/threadpool 8 :daemon true :name "async-last-log")))
(register-lazy-pool :mqtt-push-pool (delay (cp/threadpool 1 :daemon true :name "mqtt-push-pool")))
(register-lazy-pool :internal-push-pool (delay (cp/threadpool 2 :daemon true :name "internal-push-pool")))

(def year-in-mills
  (* 3600 1000 24 365))

(defprotocol Image
  (write-icon-file [this]))

(extend-protocol Image
  (Class/forName "[B")
  (write-icon-file [bs]
    (when (pos? (count bs))
      (let [checksum (md5 bs)]
        (let [out (io/file (props :user-logos) checksum)]
          (when-not (.exists out)
            (io/copy bs out))
          checksum))))
  java.io.File
  (write-icon-file [f]
    (when (and f (.exists f) (pos? (.length f)))
      (let [checksum (with-open [in (io/input-stream f)] (md5 in))]
        (let [out (io/file (props :user-logos) checksum)]
          (when-not (.exists out)
            (io/copy f out))
          checksum))))
  nil
  (write-icon-file [_]
    nil))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roles
(def unknown-role-exception
  (RuntimeException. "UNKNOWN ROLE"))

(defn role-enabled?
  [n]
  (let [n (str->num n)]
    (case n
      0 false
      1 true
      (throw unknown-role-exception))))

(defn exclusive-or
  [a b]
  (and
   (or a b)
   (not (and a b))))

(defn compare-version
  "Returns truethy is v2-str is newer than v1-str.

  Input version string should only contain numbers interleaved with dots"
  [v1-str v2-str]
  (when (and v2-str v1-str (not= v1-str v2-str))
    (let [v1s (s/split v1-str #"\.")
          v2s (s/split v2-str #"\.")]
      (pos?
       (first
        (filter
         #(not= % 0)
         (map
          (fn [a b] (- (e/read-string a)
                       (e/read-string b)))
          v2s v1s)))))))

(def ^{:doc "Returns truethy if v1 is not older thant v2" :arglists '([v1 v2])}
  reverse-compare-version (complement compare-version))

(defn gen-qrcode
  "Create a qr code, use :charset to set charset, e.g., to `ISO-8859-1` or `UTF-8`"
  [content & [{:keys [size out image-type charset]
               :or {size    220
                    image-type    "PNG"
                    charset "ISO-8859-1"}
               :as params}]]
  (let [out (or out (File/createTempFile "qrcode-" (str "." image-type)))]
    (doto (QRCode/from content)
      (.withCharset charset)
      (.withErrorCorrection (ErrorCorrectionLevel/H))
      (.withHint (EncodeHintType/CHARACTER_SET) charset)
      (.to (eval (symbol (str "net.glxn.qrgen.core.image.ImageType/" image-type))))
      (.withSize size size)
      (.writeTo (io/output-stream out))))
  out)


(defn equals-ignore-whitespace
  [s1 s2]
  (= (s/trim s1) (s/trim s2)))


(defn android? [req]
  (some->  req
           (get-in [:headers "user-agent"])
           s/lower-case
           (s/includes? "android")))

(defn low-version-ios? [req]
  (when-let [ua (get-in req [:headers "user-agent"])]
    (re-seq #"(?i)btWeigh(En)?/1\.4\.[1-4].*?ios" ua)))

(defn up-version-okok?
  "高版本 okok android 
  fix: android低版本收到weight外的类别崩溃"
  [req]
  (or
   (when-let [ver (some->> (get-in req [:headers "user-agent"])  (re-seq #"com\.chipsea\.btcontrol/(.*?)\s+") first second)]
     (compare-version "2.0.9" ver))
   (when-let [ver (some->> (get-in req [:headers "user-agent"])  (re-seq #"com\.nbputian\.btcontrol/(.*?)\s+") first second)]
     (compare-version "1.0.7" ver))))

(comment
  (start-of-week (System/currentTimeMillis))
  (=
   (* 8 3600 1000)
   (-
    (cc/to-long (start-of-week (System/currentTimeMillis) (tc/time-zone-for-offset 0)))
    (cc/to-long (start-of-week (System/currentTimeMillis)))))
  )


;; (let [required-params {:sex "m/f" :age "number" :height "number"
;;                        :weight "number" :resistance "number" :exercise_level "integer"}
;;       csalgo-required-params (dissoc required-params :exercise_level)
;;       csalgo-keys ["BFR" "BMR" "BodyAge" "EE" "EXF" "FM" "InF" "LBM" "MC" "MSW" "OD" "PM" "SLM" "Score" "TF" "TFR" "VFR" "WC"]]
;;   (defn calculate-profile-scores
;;     [{:keys [sex age height weight resistance exercise_level]}]
;;     (try
;;       (if (and sex age height weight resistance exercise_level)
;;         (let [builder (doto (AlgoProfileBuilder.)
;;                         (.withSex (case sex "f" SexEnum/F "m" SexEnum/M))
;;                         (.withAge (str->num age))
;;                         (.withHeight (str->num height))
;;                         (.withWeight (str->num weight))
;;                         (.withResistance (str->num resistance))
;;                         (.withExerciselevel (str->num exercise_level)))
;;               p (.build builder)]
;;           {:fat (.fatScore p)
;;            :viscera (.visceraScore p)
;;            :bmi (.bmiScore p)
;;            :bone (.boneScore p)
;;            :water (.waterScore p)
;;            :mus (.musScore p)
;;            :dci (.dciScore p)
;;            :bmr (.bmrScore p)})
;;         (cds :required-param-not-exist :required-params required-params))
;;       (catch Exception e
;;         (t/info e)
;;         (assoc (cds :exception) :alt (.getMessage e) :required-params required-params))))

;;   (defn cs-algorithm-utils
;;     [{:keys [sex age height weight resistance]}]
;;     (try
;;       (if (and sex age height weight resistance)
;;         (let [[age height weight resistance] (map str->num [age height weight resistance])
;;               p (CSAlgorithmUtils$Profile/createProfile (double height) (double weight) (case sex "m" 1 "0") age resistance)
;;               u  (CSAlgorithmUtils.)]
;;           (t/info (mapv class [(.-height p) (byte (.-sex p)) (.-weight p) (.-age p) (.-resistance p)]))
;;           (reduce
;;            (fn [m key]
;;              (assoc
;;               m
;;               key
;;               (clojure.lang.Reflector/invokeInstanceMethod
;;                u
;;                (str "get" key)
;;                (into-array Object [(.-height p) (byte (.-sex p)) (.-weight p) (.-age p) (.-resistance p)]))))
;;            {}
;;            csalgo-keys)
;;           )
;;         (cds :required-param-not-exist :required-params csalgo-required-params))
;;       (catch Exception e
;;         (t/info e)
;;         (assoc (cds :exception) :alt (.getMessage e) :required-params csalgo-required-params)))))


(let [sr (java.security.SecureRandom.)]
  (defn rand-bytes
    ([]
     (rand-bytes 32))
    ([n]
     (let [bs (byte-array n)]
       (.nextBytes sr bs)
       bs))))

(defn b64-dec->bytes
  "If input is string, base64 decode to bytes. Otherwise return the input"
  [s-or-bs]
  (if (string? s-or-bs)
    (base64-decode s-or-bs :as-bytes? true)
    s-or-bs))

(defn b64-enc->string
  "Encode input to base64 string"
  [s-or-bs]
  (if (string? s-or-bs)
    (base64-encode (b64-dec->bytes s-or-bs))
    (base64-encode s-or-bs)))

(defn long-cal [] (do (Thread/sleep 3000) (* 3600 1000 24 365)))
(defmacro years-in-millis [n]
  (let [m (long-cal)]
    `(* ~m ~n)))

(defn valid-date?
  ([date-str]
   (valid-date? "yyyy-MM-dd"))
  ([date-str format]
   (try
     (let [sdf (java.text.SimpleDateFormat. format)]
       (.setLenient sdf false)
       (.parse sdf date-str))
     (catch Throwable e nil))))

(defn to-sql-date [ts]
  (java.sql.Date. ts))

(defn days-before [ts ndays]
  (let [start (- ts (* ndays 86400000))]
    (time/start-of-day start)))

(defn make-log-file [headers]
  (let [f (io/file (props :log-app-root)
                   (headers "cs-device-id" "unknown")
                   (str (long->datetime-string (System/currentTimeMillis) {:pattern "yyMMddHHmmss" :offset "+8"})
                        ".log.gz"))]
    (io/make-parents f)
    f))

(defn enqueue-merge-circle [rel]
  (a/put! merge-circle-chan rel))


(defn now-as-long []
  (System/currentTimeMillis))

(defn today-as-string []
  (long->date-string (now-as-long)))

(defn n-days-before-as-string [n]
  (long->date-string (- (now-as-long) (* n 3600 24 1000))))

(defn compare-version
  "Returns non nil is v2-str is newer than v1-str.

  Input version string should only contain numbers interleaved with dots"
  [v1-str v2-str]
  ;; (info v1-str v2-str)
  (when (and v2-str v1-str (not= v1-str v2-str))
    (let [v1s (s/split v1-str #"\.")
          v2s (s/split v2-str #"\.")]
      (some->>
       (map
        (fn [a b] (- (or (e/read-string a) 0)
                     (or (e/read-string b) 0)))
        v2s v1s)
       (some #(when (not= % 0) %))
       pos?))))

(def reverse-compare-version (complement compare-version))

(defn gen-ouid []
  (long->date-string (now-as-long) {:pattern "yyyyMMddHHmmssSSS" :offset "+8"}))

(defn cent->yuan [price]
  (format  "%.2f" (/ price 100.0)))

(defn ip-from-req [req]
  (or (get-in req [:headers "x-real-ip"])
      (:remote-addr req)))

(defn hexify [bytes]
  (apply str (map #(format "%02x" %) bytes)))

(defn unhexify [hex]
  (into-array Byte/TYPE
              (map (fn [[x y]]
                     (unchecked-byte (Integer/parseInt (str x y) 16)))
                   (partition 2 hex))))

(defn payment-source [{:keys [merOrderId outer_trade_no]}]
  (cond
    merOrderId :yl
    outer_trade_no :kjt))

(defn age-from-birthday [birthday]
  (try
    (when-not (s/blank? birthday)
      (let [this-date (long->date-string (System/currentTimeMillis) {:pattern "yyyy-MM-dd" :offset "+8"})
            this-md (subs this-date 5)
            that-md (subs birthday 5)
            this-year (Integer/parseInt (subs this-date 0 4))
            that-year (Integer/parseInt (subs birthday 0 4))]
        (- this-year that-year (if (neg? (compare this-md that-md)) 1 0))))
    (catch Throwable e
      (t/error "Ignore age-from-birthday error" e))))
