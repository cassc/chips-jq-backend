(ns chips.routes.base
  (:require
   [cheshire.core :refer [generate-string]]
   [sparrows.misc :refer [str->num]]
   [clojure.string :as s]
   [hiccup.core :refer [html]]
   [hickory.core :refer [parse as-hiccup as-hickory]]
   [hickory.select :as hs]
   [taoensso.timbre :as t]
   [markdown.core :refer [md-to-html-string]]
   [noir.response         :as r]
   [chips.store.rds :as rds]
   [chips.config :refer [props]]
   [clojure.java.io :as io]
   [noir.session :as session]
   [ring.util.response :refer [content-type response]]
   [compojure.response :refer [Renderable]]
   [chips.codes :refer [cds]]))


(def ^:private template-folder "templates/")

(defn utf-8-response [html]
  (content-type (response html) "text/html; charset=utf-8"))

(defn make-icon-file-path
  [icondir filename]
  (io/file (props [:rsc :icon-root]) icondir filename))

(defn return-code-with-alt
  [key msg]
  (assoc (cds key) :alt msg))

(defn success
  "Return a success response code with an optional data"
  ([]
   (success nil))
  ([data]
   (if data
     (assoc (cds :success) :data data)
     (cds :success))))

(defmacro success-no-return
  [& body]
  `(do
     ~@body
     (~#'success)))

(defn auth-failed
  "Returns 403 Fobidden"
  []
  (r/status 403 "Auth Failed!"))

(def login-providers
  #{:native :qq :sina_blog})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dev docs
(defn extract-project-version-from-file
  ([]
   (extract-project-version-from-file "project.clj"))
  ([proj-file]
   (let [p (slurp proj-file)]
     (s/join " " (rest (re-find #"^\(defproject\s+(.*?)\s+\"(.*?)\"" p))))))

(defn- make-toc
  [content]
  (vec
   (list*
    :ul.toc
    (for [ht (re-seq #"<h\d>(.*?)</h\d>" content)]
      (let [[lstr title] (rest (re-find #"<h(\d)>(.*?)</h\d>" (first ht)))
            level (Integer/valueOf lstr)]
        [:li [:a {:href (str "#" title)
                  :style (str "font-size:" (- 16 level) "px;" (when (= 1 level) "font-weight:bold;"))}
              (str (reduce str (repeat (* level 2) "&nbsp;")) title)]])))))

(defn- add-anchors
  [content]
  (s/replace content  #"<h(\d)>(.*?)</h(\d)>" "<h$1>$2<a class=\"anchor\" name=\"$2\"></a></h$1>"))

(def mem-md-to-html
  (memoize md-to-html-string))

(defn render-markdown
  [path]
  (let [content (slurp path)
        mdhtml (mem-md-to-html content)
        toc (make-toc mdhtml)
        mdhtml (add-anchors mdhtml)
        title (extract-project-version-from-file)]
    (html [:html
           [:head
            [:title title]
            [:link {:rel "stylesheet" :href "../assets/css/bootstrap.css"}]
            [:link {:rel "stylesheet" :href "../css/normalize.css"}]
            [:link {:rel "stylesheet" :href "../css/grip-gh.css"}]
            [:link {:rel "stylesheet" :href "../css/screen.css"}]]
           [:body
            [:div.page
             [:div.preview-page
              [:div.container {:style "margin-left:430px;"}
               [:div.title-panel
                [:span title]
                toc]
               [:div.repository-with-sidebar.repo-container.with-full-navigation
                [:div.repository-content.context-loader-container
                 [:div.boxed-group.flush.clearfix.announce.instapaper_body.md
                  [:div.markdown-body
                   mdhtml]]]]]]]
            [:script {:src "../jquery.min.js"}]
            [:link {:rel "stylesheet" :href "../highlight/styles/github.css"}]
            [:script {:src "../highlight/highlight.pack.js"}]
            [:script {:src "../p/js/md.js"}]
            [:script {:src "../p/js/change.detect.js"}]]])))

(def pay-success-html
  (html
   [:html
    [:head
     [:title "支付成功！"]]
    [:body
     [:h3
      [:p "我们已收到您的支付，正在处理您的订单！"]
      [:p "请关闭本页面。"]]]]))

