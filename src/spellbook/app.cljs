(ns spellbook.app
  (:require [clojure.string :as string]
            [markdown.core :as md]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [spellbook.db :as db]))

(def to-index [:type :text :created-at :tags])

(def month->name
  {"01"  "January"
   "02"  "February"
   "03"  "March"
   "04"  "April"
   "05"  "May"
   "06"  "June"
   "07"  "July"
   "08"  "August"
   "09"  "September"
   "10" "October"
   "11" "November"
   "12" "December"})

(defonce db (db/init-db "spellbook"))
(defonce -state (r/atom :loading))
(defonce -arg (atom nil))

(defn format-date [ms]
  (.toLocaleString (js/Date. ms)))

(defn format-tags [tags]
  (filter (comp pos-int? count) (string/split tags #",\s*")))

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

(defn setup-db []
  (-> (.resolve js/Promise)
      (.then #(db/put-view db "spellbook" "archive"
                           {:map {:key [{:access "type" :emit true :equals "entry"}
                                        {:access "created-at" :transform :datetime :flatten true}]}
                            :reduce "_count"}))
      (.then #(db/put-view db "spellbook" "tags"
                           {:map {:key [{:access "type" :emit true :equals "entry"}
                                        {:access "tags" :splay true}
                                        "created-at"]}
                            :reduce "_count"}))
      (.then #(db/put-view db "spellbook" "text"
                           {:map {:key [{:access "type" :emit true :equals "entry"}
                                        {:access "text" :transform :words :splay true}
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
  (let [-search (r/atom "")]
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
      [:div.level-item
       [:button.button.is-info
        {:on-click #(reset! -state :archive)}
        "🗓️ Archive"]]
      [:div.level-item
       [:button.button.is-info
        {:on-click #(reset! -state :tags)}
        "🏷 Tags"]]]
     [:div.level-right
      [:div.level-item
       [:div.field
        [:div.control.has-icons-right
         [prompt-text -search #(do (reset! -arg (.toLowerCase @-search))
                                   (reset! -state :search))]
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
          "DFB 💖"]]]]]]))

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
      [:div.card
       [:div.card-content>div.content
        [entry-form -text -tags]]
       [:div.card-footer
        [:span.card-footer-item
         [:button.button.is-primary.is-fullwidth.is-light
          {:on-click
           (fn [& _]
             (-> (update-entry id @-text @-tags)
                 (.then #(db/resolve-id db id))
                 (.then #(do (reset! -editing? false)
                             (reset! -doc %)))))}
          [:span
           [:i.fa-solid.fa-circle-check]
           " Update"]]]
        [:span.card-footer-item
         [:button.button.is-warning.is-fullwidth.is-light
          {:on-click #(reset! -editing? false)}
          [:span
           [:i.fa-solid.fa-circle-xmark]
           " Cancel"]]]]])
    (let [{:keys [text tags created-at updated-at]} @-doc]
      [:div.card
       [:div.card-content>div.content
        [:p {:dangerouslySetInnerHTML {:__html (md/md->html text)}}]
        (when (seq tags)
          [:p.tags
           (for [tag tags]
             ^{:key tag}
             [:a.tag.is-info
              {:on-click #(do (reset! -arg [tag])
                              (reset! -state :tag))}
              tag])])
        [:div.level
         [:div.level-left
          [:div.level-item
           [:p [:em (str "Created " (format-date created-at))]]]]
         (when updated-at
           [:div.level-right
            [:div.level-item
             [:p [:em (str "Updated " (format-date updated-at))]]]])]]
       [:div.card-footer
        [:span.card-footer-item
         [:button.button.is-warning.is-fullwidth.is-light
          {:on-click #(reset! -editing? true)}
          "📝 Edit"]]
        [:span.card-footer-item
         [:button.button.is-danger.is-fullwidth.is-light
          {:on-click #(when (js/confirm "Delete this entry?")
                        (delete!))}
          "🗑 Delete"]]]])))

(defn- list-entries [title -paginator -entries]
  (let [page-f! (fn [f]
                  #(.then (f @-paginator)
                          (fn [[paginator rows]]
                            (reset! -paginator paginator)
                            (reset! -entries rows))))
        next-page! (page-f! db/next-page)
        prev-page! (page-f! db/prev-page)
        same-page! (page-f! db/same-page)]
    [:div.container>div.box>div.content
     [:h3 title]
     (for [entry @-entries
           :let [{:keys [_id]} (-> entry :doc (js->clj :keywordize-keys true))
                 -doc (r/atom (db/unmarshal-doc (:doc entry)))
                 delete! #(.then (db/remove-id! db _id) same-page!)]]
       ^{:key _id}
       [some-entry _id -doc (r/atom false) delete!])
     (when (and (seq @-entries)
                (or (:prev-page? @-paginator)
                    (:next-page? @-paginator)))
       [:<>
        [:hr]
        [:div.columns
         [:div.column.is-half
          [:button.button.is-link.is-light.is-fullwidth
           {:disabled (not (:prev-page? @-paginator))
            :on-click prev-page!}
           "Previous Page"]]
         [:div.column.is-half
          [:button.button.is-link.is-light.is-fullwidth
           {:disabled (not (:next-page? @-paginator))
            :on-click next-page!}
           "Next Page"]]]])]))

(defn- list-recent []
  ;; only re-render the page when -entries changes; no r/atom for paginator
  (let [-paginator
        (atom
         (db/paginate-view db "spellbook/archive" {:reduce false
                                                   :include_docs true
                                                   :descending true}))
        -entries (r/atom [])]
    (.then (db/next-page @-paginator)
           (fn [[paginator rows]]
             (reset! -paginator paginator)
             (if (seq rows)
               (reset! -entries rows)
               (reset! -state :new-entry))))
    [list-entries "Recent" -paginator -entries]))

(defn- show-archive [-results]
  (when (nil? @-results)
    (.then (db/query-view db "spellbook/archive" {:group true
                                                  :group_level 2})
           #(reset! -results %)))
  [:div.container>div.box>div.content
   [:h3 "Archive"]
   [:div.field.is-grouped
    (for [row @-results
          :let [[year month] (:key row)
                n (:value row)]]
      ^{:key (:key row)}
      [:div.control
       [:div.tags.has-addons
        [:a
         {:on-click #(do (reset! -arg (:key row))
                         (reset! -state :month))}
         [:span.tag.is-dark (str year ", " (month->name month))]
         [:span.tag.is-info n]]]])]])

(defn list-archive [startkey]
  (let [[year month] startkey
        endkey [year month "\uffff"]
        -paginator
        (atom
         (db/paginate-view db "spellbook/archive" {:reduce false
                                                   :startkey endkey
                                                   :endkey startkey
                                                   :include_docs true
                                                   :descending true}))
        -entries (r/atom [])]
    (.then (db/next-page @-paginator)
           (fn [[paginator rows]]
             (reset! -paginator paginator)
             (reset! -entries rows)))
    [list-entries (str year ", " (month->name month)) -paginator -entries]))

(defn show-tags [-results]
  (when (nil? @-results)
    (.then (db/query-view db "spellbook/tags" {:group true
                                               :group_level 1})
           #(reset! -results %)))
  [:div.container>div.box>div.content
   [:h3 "Tags"]
   [:div.field.is-grouped
    (for [row @-results
          :let [[tag] (:key row)
                n (:value row)]]
      ^{:key (:key row)}
      [:div.control
       [:div.tags.has-addons
        [:a
         {:on-click #(do (reset! -arg (:key row))
                         (reset! -state :tag))}
         [:span.tag.is-dark tag]
         [:span.tag.is-info n]]]])]])

(defn- list-tags [startkey]
  (let [[tag] startkey
        endkey [tag "\uffff"]
        -paginator
        (atom
         (db/paginate-view db "spellbook/tags" {:reduce false
                                                :startkey endkey
                                                :endkey startkey
                                                :include_docs true
                                                :descending true}))
        -entries (r/atom [])]
    (.then (db/next-page @-paginator)
           (fn [[paginator rows]]
             (reset! -paginator paginator)
             (reset! -entries rows)))
    [list-entries (str "#" tag) -paginator -entries]))

(defn- list-search [word]
  (let [startkey [word]
        endkey [word "\uffff"]
        -paginator
        (atom
         (db/paginate-view db "spellbook/text" {:reduce false
                                                :startkey endkey
                                                :endkey startkey
                                                :include_docs true
                                                :descending true}))
        -entries (r/atom [])]
    (.then (db/next-page @-paginator)
           (fn [[paginator rows]]
             (reset! -paginator paginator)
             (reset! -entries rows)))
    [list-entries (str "\"" word "\"") -paginator -entries]))

(defn- app []
  [:section.section
   [navbar]
   [:hr]
   [:div.block
    (case @-state
      :loading   [loading]
      :new-entry [new-entry (r/atom "") (r/atom "")]
      :index     [list-recent]
      :archive   [show-archive (r/atom nil)]
      :month     [list-archive @-arg]
      :tags      [show-tags (r/atom nil)]
      :tag       [list-tags @-arg]
      :search    [list-search @-arg])]])

(defn- main []
  (rd/render [app] (js/document.getElementById "app")))

(main)
