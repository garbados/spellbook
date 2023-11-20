(ns spellbook.app
  (:require [markdown.core :as md]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [spellbook.db :as db]))

(def to-index [:type :text :created-at])

(defonce -db (db/init-db "spellbook"))
(defonce -state (r/atom :loading))
(defonce -paginator (r/atom nil))
(defonce -entries (r/atom nil))

(-> (db/paginate-view -db "spellbook/text" {:reduce false
                                            :include_docs true
                                            :startkey ["Hello"]
                                            :endkey ["Hello" "Z"]})
    db/next-page
    (.then #(.log js/console (clj->js %))))

(defn next-page []
  (.then (db/next-page @-paginator)
         #(reset! -entries %)))

(defn prev-page []
  (.then (db/prev-page @-paginator)
         #(reset! -entries %)))

(defn same-page []
  (.then (db/same-page @-paginator)
         #(reset! -entries %)))

(defn format-date [ms]
  (.toLocaleString (js/Date. ms)))

(defn- save-entry [id text]
  (let [value {:text text
               :type "entry"
               :created-at (.now js/Date)
               :updated-at nil}]
    (db/save-doc -db id value to-index)))

(defn- update-entry [id text]
  (-> (db/resolve-id -db id)
      (.then #(assoc %
                     :text text
                     :updated-at (.now js/Date)))
      (.then #(db/upsert-doc -db id % to-index))))

(defn- setup-recent []
  (let [paginator (db/paginate-view -db "spellbook/entries" {:reduce false
                                                             :include_docs true})]
    (reset! -paginator paginator)
    (.then (next-page)
           #(reset! -state :index))))

(defn setup-search [term]
  (let [paginator (db/paginate-view -db "spellbook/text" {:reduce false
                                                          :startkey [term]
                                                          :endkey [term "Z"]
                                                          :include_docs true})]
    (reset! -paginator paginator)
    (.then (next-page)
           #(reset! -state :search))))

(defn- setup []
  (-> (db/put-view -db "spellbook" "entries"
                   {:map {:key [{:access "type" :emit true :equals "entry"}
                                {:access "created-at" :transform :datetime}]}
                    :reduce "_count"})
      (.then #(db/put-view -db "spellbook" "text"
                           {:map {:key [{:access "type" :emit true :equals "entry"}
                                        {:access "text" :transform :words :splay true}]}}))
      (.then setup-recent)))

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
     [:button.button.is-link
      {:on-click #(reset! -state :new-entry)}
      "✍️ New Entry"]]
    [:div.level-item
     [:button.button.is-primary
      {:on-click
       (fn [& _]
         (.then (setup-recent)
                #(reset! -state :index)))}
      "🔮 Recent"]]
    [:div.level-item
     [:button.button.is-primary
      {:on-click
       (fn [& _]
         #_(.then (setup-archive)
                #(reset! -state :archive)))}
      "🗓️ Archive"]]
    ]
   [:div.level-right
    [:div.level-item
     [:div.field
      [:div.control.has-icons-right
       [prompt-text (r/atom "") setup-search]
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
  (.then (setup)
         #(if (seq @-entries)
            (reset! -state :index)
            (reset! -state :new-entry)))
  [:div.container>div.box>div.content
   [:h1 "Loading..."]])

(defn- new-entry [-text]
  [:div.container>div.box>div.content
   [:h3 "✨ New Entry"]
   [:div.field
    [:div.control
     [prompt-textarea -text]]
    [:p.help "Use markdown!"]]
   [:p
    [:button.button.is-link.is-fullwidth
     {:on-click
      (fn [& _]
        (-> (save-entry (random-uuid) @-text)
            (.then #(same-page))
            (.then #(reset! -state :index))))}
     "Save Entry"]]])

(defn- some-entry [id created-at updated-at -text -editing?]
  (if @-editing?
    [:div.box>div.content
     [:div.field
      [:div.control
       [prompt-textarea -text]]
      [:p.help "Use markdown!"]]
     [:p
      [:button.button.is-primary.is-fullwidth
       {:on-click
        (fn [& _]
          (-> (update-entry id @-text)
              (.then #(same-page))
              (.then #(reset! -editing? false))))}
       "Update Entry"]]
     [:p
      [:button.button.is-caution.is-light.is-fullwidth
       {:on-click #(reset! -editing? false)}
       "Cancel"]]]
    [:div.container>div.box>div.content
     [:div {:dangerouslySetInnerHTML {:__html (md/md->html @-text)}}]
     [:hr]
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
        {:on-click #(.then (db/remove-id! -db id) same-page)}
        "Delete Entry"]]]]))

(defn- list-entries [title]
  [:div.container>div.box>div.content
   [:h3 title]
   (for [entry @-entries
         :let [{:keys [_id]} (-> entry :doc (js->clj :keywordize-keys true))
               {:keys [text created-at updated-at]} (-> entry :doc db/unmarshal-doc)]]
     ^{:key _id}
     [some-entry _id created-at updated-at (r/atom text) (r/atom false)])])

(defn- app []
  [:section.section
   [navbar]
   [:hr]
   [:div.block
    (case @-state
      :loading   [loading]
      :new-entry [new-entry (r/atom "")]
      :index     [list-entries "Recent"]
      :search    [list-entries "Search"]
      ;;:archive [view-archive]
      )]])

(defn- main []
  (rd/render [app] (js/document.getElementById "app")))

(main)
