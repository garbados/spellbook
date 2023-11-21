(ns spellbook.app
  (:require [clojure.string :as string]
            [markdown.core :as md]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [spellbook.db :as db]
            [pouchdb-jsonviews :as jsonview]))

(def to-index [:type :created-at :tags])

(defonce db (db/init-db "spellbook"))
(defonce -state (r/atom :loading))

(defn format-date [ms]
  (.toLocaleString (js/Date. ms)))

(defn- save-entry [id text tags]
  (let [value {:text text
               :type "entry"
               :tags (string/split tags #",\s*")
               :created-at (.now js/Date)
               :updated-at nil}]
    (db/save-doc db id value to-index)))

(defn- update-entry [id text tags]
  (-> (db/resolve-id db id)
      (.then #(assoc %
                     :text text
                     :tags (string/split tags #",\s+")
                     :updated-at (.now js/Date)))
      (.then #(db/upsert-doc db id % to-index))))

;; (defn fetch-archive)

;; (defn fetch-tag-counts []
;;   (.then
;;    (db/query-view "spellbook/tags" {:reduce true :group true :group_level 1})
;;    (fn [{:keys [rows]}]
;;      (for [{:keys [key value]} rows]
      ;;  [(first key) value]))))

(defn setup-db []
  (-> (.resolve js/Promise)
      (.then #(db/put-view db "spellbook" "archive"
                           {:map {:key [{:access "type" :emit true :equals "entry"}
                                        {:access "created-at" :transform :datetime}]}
                            :reduce "_count"}))
      (.then #(db/put-view db "spellbook" "tags"
                           {:map {:key [{:access "type" :emit true :equals "entry"}
                                        {:access "tags" :splay true}
                                        "created-at"]}
                            :reduce "_count"}))))

(defn- initial-setup []
  (setup-db))

(defn- prompt-text [value & [on-submit]]
  [:input.input
   (cond->
    {:type "text"
     :value @value
     :on-change #(reset! value (-> % .-target .-value))}
     on-submit (assoc :on-key-down
                      (fn [e]
                        (when (= 13 (.-which e))
                          (on-submit @value)))))])

(defn- prompt-textarea [value]
  [:textarea.textarea
   {:on-change #(reset! value (-> % .-target .-value))
    :rows 10
    :value @value}])

(defn- navbar []
  [:div.level
   [:div.level-left
    [:div.level-item
     [:h1.title "📖 Spellbook"]]
    [:div.level-item
     [:button.button.is-primary
      {:on-click #(reset! -state :new-entry)}
      "✍️ New Entry"]]
    [:div.level-item
     [:button.button.is-info
      {:on-click #(reset! -state :index)}
      "🔮 Recent"]]
    #_[:div.level-item
       [:button.button.is-primary
        {:on-click
         (fn [& _]
           #_(.then (setup-archive)
                    #(reset! -state :archive)))}
        "🗓️ Archive"]]]
   [:div.level-right
    #_[:div.level-item
       [:div.field
        [:div.control.has-icons-right
         [prompt-text (r/atom "")] ; TODO
         [:span.icon.is-small.is-right
          [:i.fas.fa-magnifying-glass]]]]]
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
  (.then (initial-setup)
         #(reset! -state :index))
  [:div.container>div.box>div.content
   [:h1 "Loading..."]])

(defn- entry-form [-text -tags]
  [:<>
   [:div.field
    [:label.label "What's on your mind?"]
    [:div.control
     [prompt-textarea -text]]
    [:p.help "Use markdown!"]]
   [:div.field
    [:label.label "Tags, for searching and organizing."]
    [:div.control
     [prompt-text -tags]]
    [:p.help "Separate tags with commas!"]]])

(defn- new-entry [-text -tags]
  [:div.container>div.box>div.content
   [:h3 "✨ New Entry"]
   [entry-form -text -tags]
   [:p
    [:button.button.is-link.is-fullwidth
     {:on-click #(.then (save-entry (str (random-uuid)) @-text @-tags)
                        (fn [& _] (reset! -state :index)))}
     "Save Entry"]]])

(defn- some-entry [id -doc -editing? delete!]
  (if @-editing?
    (let [doc @-doc
          -text (r/atom (:text doc))
          -tags (r/atom (string/join ", " (:tags doc)))]
      [:div.box>div.content
       [entry-form -text -tags]
       [:p
        [:button.button.is-primary.is-fullwidth
         {:on-click
          (fn [& _]
            (-> (update-entry id @-text @-tags)
                (.then #(db/resolve-id db id))
                (.then #(do (reset! -editing? false)
                            (reset! -doc %)))))}
         "Update Entry"]]
       [:p
        [:button.button.is-caution.is-light.is-fullwidth
         {:on-click #(reset! -editing? false)}
         "Cancel"]]])
    (let [{:keys [text tags created-at updated-at]} @-doc]
      [:div.box>div.content
       [:div {:dangerouslySetInnerHTML {:__html (md/md->html text)}}]
       [:hr]
       [:div.tags
        (for [tag tags]
          ^{:key tag} [:span.tag.is-info tag])]
       [:div.level
        [:div.level-left
         [:div.level-item
          [:p [:em (str "Created " (format-date created-at))]]]] 
        (when updated-at
          [:div.level-right
           [:div.level-item
            [:p [:em (str "Updated " (format-date updated-at))]]]])]
       [:div.columns
        [:div.column.is-half
         [:button.button.is-warning.is-light.is-fullwidth
          {:on-click #(reset! -editing? true)}
          "Edit Entry"]]
        [:div.column.is-half
         [:button.button.is-danger.is-light.is-fullwidth
          {:on-click #(delete!)}
          "Delete Entry"]]]])))

(defn- list-entries [title paginator -entries]
  [:div.container>div.box>div.content
   [:h3 title]
   (for [entry @-entries
         :let [{:keys [_id]} (-> entry :doc (js->clj :keywordize-keys true))
               -doc (r/atom (db/unmarshal-doc (:doc entry)))
               delete!
               (fn [& _]
                 (-> (db/remove-id! db _id)
                     (.then #(db/same-page paginator))
                     (.then #(reset! -entries %))))]]
     ^{:key _id}
     [some-entry _id -doc (r/atom false) delete!])
   [:hr]
   [:div.columns
    [:div.column.is-half
     (when (db/has-prev-page? paginator)
       [:button.button.is-link.is-light.is-fullwidth
        {:on-click
         #(.then (db/prev-page paginator)
                 (partial reset! -entries))}
        "Previous Page"])]
    [:div.column.is-half
     (when (db/has-next-page? paginator)
       [:button.button.is-link.is-light.is-fullwidth
        {:on-click
         #(.then (db/next-page paginator)
                 (partial reset! -entries))}
        "Next Page"])]]])

(defn- list-recent []
  (let [paginator (db/paginate-view db "spellbook/archive" {:reduce false
                                                            :limit 2
                                                            :include_docs true})
        -entries (r/atom [])]
    (.then (db/next-page paginator)
           #(reset! -entries %))
    [list-entries "Recent" paginator -entries]))

(defn- app []
  [:section.section
   [navbar]
   [:hr]
   [:div.block
    (case @-state
      :loading   [loading]
      :new-entry [new-entry (r/atom "") (r/atom "")]
      :index     [list-recent]
      ;;:archive [view-archive]
      )]])

(defn- main []
  (rd/render [app] (js/document.getElementById "app")))

(main)
