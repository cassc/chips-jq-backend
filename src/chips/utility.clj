(ns chips.utility
  (:require
   [chips.config :refer [props]]
   [chips.utils :refer [send-plain-email]]
   [chips.store.rds :as rds]
   [chips.host-info :refer [hostinfo]]

   [sparrows.misc   :refer [wrap-exception]]
   
   [taoensso.timbre :as t]
   [clojure.string  :as s])
  (:import
   [com.chipsea.wifi.q81 CsBlueHelper StandardUtils] ;; for Q81 wifi
   [chips CsAlgoHelper] ;; for V10 bluetooth
   [com.chipsea.healthscale CsAlgoBuilderEx]))


(def ^:private error-email-address-prefix "error-email-address-prefix")

(defn- allow-send?
  [{:keys [address err]}]
  (let [err-key (apply str error-email-address-prefix err address)]
    (when-not (rds/get-code {:key err-key})
      (rds/store-code {:key err-key :val 1 :ttl (props :email-rate-limit)})
      true)))

(defn send-error-email
  "Send an system error email. Note that this function does not log
  any exceptions to local logs."
  [{:keys [address subject msg err] :as m}]
  (when (allow-send? m)
    (send-plain-email address subject msg)))


(defn internal-exception-hanlder
  "This is a callback for `wrap-exception`"
  [e & args]
  (let [err-msg (str "Exception: " (.getMessage e) " on " args)
        err-receiver (props [:error-handling :email-receiver])]
    (t/error e (str "Exception: " (.getMessage e) " on " args))
    (when (seq err-receiver)
      (future
        ((wrap-exception send-error-email)
         {:address err-receiver
          :subject (str (hostinfo) err-msg)
          :msg (s/join "\n" (map (comp (partial format "%120s") str) (.getStackTrace e)))
          :err (or (.getMessage e) (str (class e)))})))))

(defn exception-hanlder
  [req e]
  (let [err-msg (str "Exception: " (.getMessage e) " on Req: " req )
        err-receiver (props [:error-handling :email-receiver])]
    (t/error e (str "Exception: " (.getMessage e) " on Req: " req))
    (when (seq err-receiver)
      (future
        ((wrap-exception send-error-email)
         {:address err-receiver
          :subject (str (hostinfo) err-msg)
          :msg (s/join "\n" (map (comp (partial format "%120s") str) (.getStackTrace e)))
          :err (or (.getMessage e) (str (class e)))})))))


(defn wrap-async
  "Wraps an function with future. Exception will be captured and logged by email."
  [func]
  (fn [& args]
    (future
      (try
        (apply func args)
        (catch Throwable e
          (internal-exception-hanlder e args))))))


(defmacro wrap-internal-exception-handler
  "Wrap body in try-catch block. Logs any exception by email. Returns
  nil if an exception is caught."
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (internal-exception-hanlder e#)
       nil)))

(defn- invoke-getter [obj ky]
  (.. obj
      (getClass)
      (getDeclaredMethod (str "get" (name ky)) (into-array Class nil))
      (invoke obj (into-array Class nil))))


(def base-kys [:Version :TF :TFR :LBM :SLM :PM :FM :BFR :MC :WC :FC :BMR :MSW :VFR :BodyAge :Score :SMM])
(def eight-r-kys [:RABFR :RASLM :LABFR :LASLM :TRBFR :TRSLM :RLBFR :RLSLM :LLBFR :LLSLM :WHR])

(defn- readable-data-from-csalgo [builder eight-r?]
  (let [assoc-fields (fn [m ky]
                       (assoc m ky (invoke-getter builder ky)))
        m-base (reduce assoc-fields {} base-kys)
        m (if eight-r?
            (reduce assoc-fields m-base eight-r-kys)
            m-base)]
    m))

;; V10
(defn parse-eight-resitance [{:keys [height sex age weight_time weight arr-resist prev-mr eight-r?] :as m}]
  (t/info "parse-eight-resitance" [height sex age weight_time weight arr-resist prev-mr eight-r?])
  (let [builder (CsAlgoBuilderEx.)
        mr (CsAlgoHelper/parseEightResitance builder height sex age weight_time weight arr-resist prev-mr eight-r?)
        readable (readable-data-from-csalgo builder eight-r?)]
    [mr builder readable]))

(comment
  (parse-eight-resitance {:height 165
                          :sex 1
                          :age 35
                          :weight_time (java.util.Date.)
                          :weight 65
                          :arr-resist (byte-array 36 [21, 109, 20, -110, 20, -85, 20, -49, 20, -25, 20, 24, 4, -74, 4, -74, 4, -74, 4, -74, 4, -74, 22, 112, 19, 100, 19, 93, 19, 10, 19, 111, 19, 25, 19, 72])
                          :prev-mr nil
                          :eight-r? true})
  )

;; V10
(defn restore-mr [{:keys [arr-resist weigth_time weight]}]
  (CsAlgoHelper/restoreMeasureResult arr-resist weigth_time weight))

;; Q81 WIFI
(def wifi-keys
  [:BMI :FM :BFR :VFR :TF :TFR :SLM :MSW :BMR :BodyAge :BW :WC :MC :Score
   :BM :FC :OD :PM :LBM :Shape :RABFR :LABFR :RLBFR :LLBFR :TRBFR :RASLM :LASLM :RLSLM :LLSLM :WL :HL] )

(defn tip-from-od [od bw]
  (let [{:keys [x-title x-range x-description]} (props :od-standard)
        x-parts (partition 2 1 x-range)
        tip-idx (some identity
                      (map-indexed (fn [idx [left right]]
                                     (when (<= left od right)
                                       idx))
                                   x-parts))
        hint (nth x-description tip-idx)]
    (if (s/includes? hint "%s")
      (format hint (format "%.2f公斤" (* bw 1.0)))
      hint)))

(defn parse-wifi-weight
  ([m-weight]
   (parse-wifi-weight m-weight nil))
  ([{:keys [height weight age sex r1 rn8]} prev-weight]
   (let [builder           (CsBlueHelper/makeBuilder height weight sex age r1 rn8)
         std-utils         (StandardUtils. age sex)
         [bw score bmr msw slm slm_percent bfr fm tfr tf bmi od shape vfr]
         (map (partial invoke-getter builder)
              [:BW
               :Score
               :BMR
               :MSW
               :SLM
               :SLMPercent
               :BFR
               :FM
               :TFR
               :TF
               :BMI
               :OD
               :Shape
               :VFR])
         od-hint           (tip-from-od od bw)]
     (t/info (type vfr) vfr)
     {:score         {:value score}
      :weight_change (when-let [pw (:weight prev-weight)]
                       (- weight pw))
      :hint          od-hint
      :od            {:value   od
                      :level   shape
                      :tip     (cond
                                 (neg? shape) "消瘦"
                                 (< shape 1) "普通"
                                 (< shape 2) "隐形肥胖"
                                 (< shape 3) "肌肉型肥胖"
                                 :else "肥胖")
                      :percent  (* (/ (+ shape 2) 5.5) 100.0)}
      :bmr           (let [level (.getMetabolismLevel std-utils bmr)]
                       {:value   bmr
                        :level   level
                        :tip     (if (< level 2) "未达标" "正常")
                        :percent (* 100 (/ bmr 3000.0))})
      :msw           (let [level (.getBoneLevel std-utils msw)]
                       {:value   msw
                        :level   level
                        :tip     (cond
                                   (< level 2) "偏低"
                                   (< level 3) "标准"
                                   :else       "优")
                        :percent (* 100 (/ msw 5.0))})
      :slm           (let [level (.getMuscleLevel std-utils slm)]
                       {:value   slm
                        :level   level
                        :tip     (cond
                                   (< level 2) "不足"
                                   (< level 3) "标准"
                                   :else       "优")
                        :percent slm_percent})
      :slm_percent   (let [level (.getMuscleLevel std-utils slm)]
                       {:value   slm_percent
                        :level   level
                        :tip     (cond
                                   (< level 2) "不足"
                                   (< level 3) "标准"
                                   :else       "优")
                        :percent slm_percent})
      :bfr           (let [bfr    bfr
                           axunge fm
                           level  (.getAxungeLevel std-utils axunge)]
                       {:value   bfr
                        :level   level
                        :tip     (cond
                                   (< level 2) "标准"
                                   (< level 3) "稍微超标"
                                   :else       "过多")
                        :percent bfr})
      :fm            (let [bfr    bfr
                           axunge fm
                           level  (.getAxungeLevel std-utils axunge)]
                       {:value   axunge
                        :level   level
                        :tip     (cond
                                   (< level 2) "标准"
                                   (< level 3) "稍微超标"
                                   :else       "过多")
                        :percent bfr})
      :tfr           (let [level (.getWaterLevel std-utils tf)]
                       {:value   tfr
                        :level   level
                        :tip     (cond
                                   (< level 2) "不足"
                                   (< level 3) "标准"
                                   :else       "优")
                        :percent tfr})
      :tf            (let [level (.getWaterLevel std-utils tf)]
                       {:value   tf
                        :level   level
                        :tip     (cond
                                   (< level 2) "不足"
                                   (< level 3) "标准"
                                   :else       "优")
                        :percent tfr})
      :bmi           (let [level (StandardUtils/getBmiLevel bmi)]
                       {:value   bmi
                        :level   level
                        :tip     (cond
                                   (< level 2) "偏瘦"
                                   (< level 3) "标准"
                                   (< level 4) "偏胖"
                                   :else       "肥胖")
                        :percent (* 100 (/ bmi 50.0))})
      :vfr           (let [level (StandardUtils/getVisceraLevel vfr)]
                       {:value   vfr
                        :level   level
                        :tip     (cond
                                   (< level 3) "标准"
                                   (< level 4) "稍微超标"
                                   :else       "过多")
                        :percent (* 100 (/ vfr 50.0))})})))


(comment
  (parse-wifi-weight {:height 165
                      :weight 62
                      :age 35
                      :sex 1
                      :r1 492.0
                      :rn8 "1:651.5,474.5,477.2,507.2,509.9"})
  )
