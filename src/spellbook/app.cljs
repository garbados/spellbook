(ns spellbook.app
  (:require [clojure.string :as string]
            [markdown.core :as md]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [spellbook.db :as db]))

(def tagsplit #",\s+?")
(def to-index [:text :created-at])

(defonce -db (db/init-db "spellbook"))
(defonce -state (r/atom :loading))
(defonce -paginator (r/atom nil))
(defonce -entries (r/atom nil))

(defn- refresh-page []
  (.then (.getSamePage @-paginator)
         #(reset! -entries (.-rows %))))

(defn- prev-page []
  (.then (.getPrevPage @-paginator)
         #(reset! -entries (.-rows %))))

(defn- next-page []
  (.then (.getNextPage @-paginator)
         #(reset! -entries (.-rows %))))

(defn- setup []
  (let [views
        [["entries"
          {:map {:key [{:access "type" :emit true :equals "entry"}
                       {:access "created-at" :transform :date}]}
           :reduce "_count"}
          "text"
          {:map {:key [{:access "type" :emit true :equals "entry"}
                       {:access "text" :transform :words}]}}]]]
    (-> (.all js/Promise
              (for [[view-name json-view] views]
                (.putJsonView -db "spellbook" view-name (clj->js json-view))))
        (.then #(.query -db "spellbook/entries" (clj->js {:reduce false})))
        (.then #(reset! -paginator %))
        (.then #(next-page))
        (.then #(.allDocs -db (clj->js {:include_docs true})))
        (.then #(.getNextPage %))
        (.then #(.log js/console %)))))

(defn- navbar []
  [:div.level
   [:div.level-left
    [:div.level-item
     [:h1.title "📖 Spellbook"]]
    [:div.level-item
     [:button.button.is-primary
      {:on-click #(reset! -state :new-entry)}
      "✍️ New Entry"]]]
   [:div.level-right
    [:div.level-item
     [:a.button.is-info.is-light
      {:href "https://github.com/garbados/spellbook"
       :target "_blank"}
      "Source 👩‍💻"]]
    [:div.level-item
     [:p.subtitle
      [:strong "A diary by "
       [:a {:href "https://www.patreon.com/garbados"
            :target "_blank"}
        "DFB 💖"]]]]]])

(defn- loading []
  (.then (setup)
         #(if (seq @-entries)
            (reset! -state :index)
            (reset! -state :new-entry)))
  [:div.container>div.box>div.content
   [:h1 "Loading..."]])

(defn- prompt-text [value]
  [:input.input
   {:type "text"
    :value @value
    :on-change #(reset! value (-> % .-target .-value))}])

(defn- prompt-textarea [value]
  [:textarea.textarea
   {:on-change #(reset! value (-> % .-target .-value))
    :rows 10
    :value @value}])

(defn- save-entry [id text]
  (let [value {:text text
               :created-at (.now js/Date)
               :updated-at nil}]
    (db/save-doc -db id value to-index)))

(defn- update-entry [id text]
  (.then (db/resolve-id -db id)
         #(assoc %
                 :text text
                 :updated-at (.now js/Date))
         #(db/upsert-doc -db id % to-index)))

(defn- new-entry [-text]
  [:div.container>div.box>div.content
   [:h3 "✨ New Entry"]
   [:div.field
    [:div.control
     [prompt-textarea -text]]
    [:p.help "Use markdown!"]]
   [:p
    [:button.button.is-link.is-fullwidth
     {:on-click #(.then (save-entry (random-uuid) @-text)
                        (fn [& _] (reset! -state :index)))}
     "Save Entry"]]])

(defn- some-entry [id created-at updated-at -text -editing?]
  (if @-editing?
    [:div.container>div.box>div.content
     [:div.field
      [:div.control
       [prompt-textarea -text]]
      [:p.help "Use markdown!"]]
     [:p
      [:button.button.is-primary.is-fullwidth
       {:on-click #(do (update-entry id @-text)
                       (reset! -editing? false))}
       "Update Entry"]]]
    [:div.container>div.box>div.content
     (md/md->html @-text)
     [:p created-at]
     (when updated-at
       [:p updated-at])
     [:div.columns
      [:div.column.is-half
       [:button.button.is-warning.is-fullwidth
        {:on-click #(reset! -editing? true)}
        "Edit Entry"]]
      [:div.column.is-half
       [:button.button.is-danger.is-fullwidth
        {:on-click #(.then (db/remove-id! -db id)
                           refresh-page)}
        "Delete Entry"]]]]))

(defn- list-entries []
  (for [entry @-entries
        :let [{:keys [_id text created-at updated-at]} entry]]
    [some-entry _id created-at updated-at (r/atom text) (r/atom false)]))

(defn- app []
  [:section.section
   [navbar]
   [:hr]
   [:div.block
    (case @-state
      :loading   [loading]
      :new-entry [new-entry (r/atom "")]
      :index     [list-entries]
      ;;:search  [search-entries]
      ;;:archive [view-archive]
      )]])

(defn- main []
  (rd/render [app] (js/document.getElementById "app")))

(main)
