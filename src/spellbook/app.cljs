(ns spellbook.app 
  (:require
   ["html-alchemist" :as alchemy]
   [clojure.string :as string]
   [shadow.cljs.modern :refer [defclass]]
   [spellbook.db :as db]
   [spellbook.slurp :refer-macros [inline-slurp]]))

(defn alchemize [expr]
  (alchemy/alchemize (clj->js expr)))

;; DATABASE PREAMBLE

(def DDOC
  (clj->js
   {:_id "_design/spellbook"
    :views
    {:archive
     {:map (inline-slurp "resources/views/archive.js")
      :reduce "_count"}
     :tags
     {:map (inline-slurp "resources/views/tags.js")
      :reduce "_count"}
     :text
     {:map (inline-slurp "resources/views/text.js")
      :reduce "_count"}}}))

(def to-index [:type :text :created-at :tags])

(defn format-date [ms]
  (.toLocaleString (js/Date. ms)))

(defn format-tags [tags]
  (filter (comp pos-int? count) (string/split tags #",\s*")))

(def db (db/init-db "spellbook"))

(defn- save-entry [id text tags]
  (let [value {:text text
               :type "entry"
               :tags (format-tags tags)
               :created-at (.now js/Date)
               :updated-at nil}]
    (db/save-doc db id value to-index)))

(defn- update-entry [id text tags]
  (-> (db/resolve-id db id)
      (.then #(assoc %
                     :text text
                     :tags (format-tags tags)
                     :updated-at (.now js/Date)))
      (.then #(db/upsert-doc db id % to-index))))

;; VIEWS

(defn prompt-text [-value & [on-submit]]
  (let [onchange
        (fn [event]
          (.preventDefault event)
          (reset! -value (-> event .-target .-value)))
        onkeydown
        (fn [event]
          (when (= 13 (.-which event))
            (on-submit @-value)))]
    [:input.input
     (cond->
      {:type "text"
       :value @-value
       :onchange onchange}
       on-submit (assoc :onkeydown onkeydown))]))

(defn spellbook-view []
  (js/console.log
   (clj->js
    [[:h1 "hello world"]
     (prompt-text (atom "wowwww"))]))
  (alchemize
   [[:h1 "hello world"]
    (prompt-text (atom "wowwww"))]))

(defclass SpellbookApp
  (extends js/HTMLElement)
  (constructor [this] (super this))
  Object
  (connectedCallback
   [this]
   (-> (.put db DDOC)
       (.catch (constantly nil))
       (.then #(.appendChild this (spellbook-view))))))

(try
  (js/customElements.define "spellbook-app" SpellbookApp)
  (catch js/Error _ (js/window.location.reload)))
