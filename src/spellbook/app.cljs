(ns spellbook.app 
  (:require
   ["html-alchemist" :as alchemy :refer [snag]]
   ["marked" :as marked]
   [clojure.string :as string]
   [shadow.cljs.modern :refer [defclass]]
   [spellbook.db :as db]
   [spellbook.slurp :refer-macros [inline-slurp]]))

(defn alchemize [expr]
  (alchemy/alchemize (clj->js expr)))

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

;; DATABASE PREAMBLE

(def DDOC
  {:views
   {:archive
    {:map (inline-slurp "resources/views/archive.js")
     :reduce "_count"}
    :tags
    {:map (inline-slurp "resources/views/tags.js")
     :reduce "_count"}
    :text
    {:map (inline-slurp "resources/views/text.js")
     :reduce "_count"}}})

(def to-index [:type :text :created-at :tags])

(defn format-date [ms]
  (.toLocaleString (js/Date. ms)))

(defn format-tags [tags]
  (filter (comp pos-int? count) (string/split tags #",\s*")))

(def db (db/init-db "spellbook"))

(defn- setup-db []
  (db/upsert-ddoc! db "_design/spellbook" DDOC))

(defn- save-entry [id text tags]
  (let [value {:text text
               :type "entry"
               :tags (format-tags tags)
               :created-at (.now js/Date)
               :updated-at nil}]
    (db/save-value db id value to-index)))

(defn- update-entry [id text tags]
  (-> (db/resolve-value db id)
      (.then #(assoc %
                     :text text
                     :tags (format-tags tags)
                     :updated-at (.now js/Date)))
      (.then #(db/upsert-value db id % to-index))))

;; ROUTING

(defn goto [hash]
  (set! js/window.location (str "#/" hash)))

(defn handle-route [routes node hash]
  (let [matched (filter #(re-find % hash) (keys routes))]
    (if-let [handler (get routes (first matched))]
      (handler node hash)
      (goto "recent"))))

;; COMPONENTS

(defn prompt-text [-value & [on-submit]]
  (let [oninput
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
       :oninput oninput}
       on-submit (assoc :onkeydown onkeydown))]))

(defn- prompt-textarea [-value]
  [:textarea.textarea
   {:oninput #(reset! -value (-> % .-target .-value))
    :rows 10}
   @-value])

(defn navbar []
  (let [-search (atom "")]
    [:div.box
     [:div.level
      [:div.level-left
       [:div.level-item
        [:h1.title "📖 Spellbook"]]
       [:div.level-item
        [:a.button.is-primary
         {:href "#/compose"}
         "✍️ New Entry"]]
       [:div.level-item
        [:a.button.is-info
         {:href "#/recent"}
         "🔮 Recent"]]
       [:div.level-item
        [:a.button.is-info
         {:href "#/archive"}
         "🗓️ Archive"]]
       [:div.level-item
        [:a.button.is-info
         {:href "#/tags"}
         "🏷️ Tags"]]]
      [:div.level-right
       [:div.level-item
        [:div.field
         [:div.control.has-icons-right
          (prompt-text -search #(goto (str "search/" %)))
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
           "DFB 💖"]]]]]]]))

(defn container []
  [:div
   (navbar)
   [:div.block#main]])

(defn- entry-form [-text -tags]
  [:div
   [:div.field
    [:label.label "What's on your mind?"]
    [:div.control
     (prompt-textarea -text)]
    [:p.help "Use markdown!"]]
   [:div.field
    [:label.label "Tags, for searching and organizing."]
    [:div.control
     (prompt-text -tags)]
    [:p.help "Separate tags with commas!"]]])

(defn- new-entry [-text -tags]
  [:div.container>div.box>div.content
   [:h3 "✨ New Entry"]
   (entry-form -text -tags)
   [:p
    [:button.button.is-link.is-fullwidth
     {:onclick #(.then (save-entry (str (random-uuid)) @-text @-tags)
                        (fn [& _] (goto "recent")))}
     "Save Entry"]]])

(defn- edit-entry [-text -tags on-update on-cancel]
  [:div.card
   [:div.card-content>div.content
    (entry-form -text -tags)]
   [:div.card-footer
    [:span.card-footer-item
     [:button.button.is-primary.is-fullwidth.is-light
      {:onclick on-update}
      [:span
       [:i.fa-solid.fa-circle-check]
       " Update"]]]
    [:span.card-footer-item
     [:button.button.is-warning.is-fullwidth.is-light
      {:onclick on-cancel}
      [:span
       [:i.fa-solid.fa-circle-xmark]
       " Cancel"]]]]])

(defn- show-entry [{:keys [text tags created-at updated-at]} on-edit on-delete]
  [:div.card
   [:div.card-content>div.content
    (alchemy/profane "p" (marked/parse text))
    (when (seq tags)
      [:p.tags
       (for [tag tags]
         ^{:key tag}
         [:a.tag.is-info
          {:href (str "#/tag/" tag)}
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
      {:onclick on-edit}
      "📝 Edit"]]
    [:span.card-footer-item
     [:button.button.is-danger.is-fullwidth.is-light
      {:onclick on-delete}
      "🗑 Delete"]]]])

(defn- some-entry [id -doc -editing? delete!]
  (if @-editing?
    (let [{:keys [text tags]} @-doc
          -text (atom text)
          -tags (atom (string/join ", " tags))
          on-update
          (fn [& _]
            (-> (update-entry id @-text @-tags)
                (.then #(db/snag-doc db id))
                (.then #(do (reset! -doc %)
                            (reset! -editing? false)))))
          on-cancel #(reset! -editing? false)]
      (edit-entry -text -tags on-update on-cancel))
    (let [on-edit #(reset! -editing? true)
          on-delete #(when (js/confirm "Delete this entry?")
                       (delete!))]
      (show-entry @-doc on-edit on-delete))))

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
                 -doc (atom (db/unmarshal-doc (:doc entry)))
                 -editing? (atom false)
                 delete! #(.then (db/remove-id! db _id) same-page!)
                 list-entry (partial some-entry _id -doc -editing? delete!)
                 on-edit #(.replaceChildren (snag (str "entry-" _id)) (alchemize (list-entry)))]]
       ^{:key _id}
       (do
         (add-watch -editing? :watcher on-edit)
         [(str "div#entry-" _id)
          (list-entry)]))
     (when (and (seq @-entries)
                (or (:prev-page? @-paginator)
                    (:next-page? @-paginator)))
       [[:hr]
        [:div.columns
         [:div.column.is-half
          [:button.button.is-link.is-light.is-fullwidth
           {:disabled (not (:prev-page? @-paginator))
            :onclick prev-page!}
           "Previous Page"]]
         [:div.column.is-half
          [:button.button.is-link.is-light.is-fullwidth
           {:disabled (not (:next-page? @-paginator))
            :onclick next-page!}
           "Next Page"]]]])]))

(defn- archive [results]
  [:div.container>div.box>div.content
   [:h3 "Archive"]
   (cons
    :div.field.is-grouped.is-grouped-multiline
    (for [row results
          :let [[year month] (:key row)
                n (:value row)]]
      ^{:key (:key row)}
      [:div.control
       [:div.tags.has-addons
        [:a
         {:href (str "#/archive/" year "/" month)}
         [:span.tag.is-dark (str year ", " (month->name month))]
         [:span.tag.is-info n]]]]))])

(defn- tags [results]
  [:div.container>div.box>div.content
   [:h3 "Tags"]
   (cons
    :div.field.is-grouped.is-grouped-multiline
    (for [row results
          :let [[tag] (:key row)
                n (:value row)]]
      ^{:key (:key row)}
      [:div.control
       [:div.tags.has-addons
        [:a
         {:href (str "#tag/" tag)}
         [:span.tag.is-dark tag]
         [:span.tag.is-info n]]]]))])

;; VIEWS

(defn- compose-entry [node _hash]
  (.replaceChildren node (alchemize (new-entry (atom "") (atom "")))))

(defn- list-results [node title view-name page-options]
  (let [-paginator
        (atom
         (db/paginate-view db view-name page-options))
        -entries (atom [])
        view (partial list-entries title -paginator -entries)
        refresh-page #(.replaceChildren node (alchemize (view)))]
    (add-watch -entries :refresh refresh-page)
    (.then (db/next-page @-paginator)
           (fn [[paginator rows]]
             (reset! -paginator paginator)
             (reset! -entries rows)))))

(defn- list-recent [node _hash]
  (list-results node "Recent" "spellbook/archive" {:reduce false
                                                   :include_docs true
                                                   :descending true}))

(defn- list-archive [node hash]
  (let [[year month] (rest (re-find #"archive/(.+?)/(.+?)$" hash))
        page-options {:reduce false
                      :startkey [year month "\uffff"]
                      :endkey [year month]
                      :include_docs true
                      :descending true}
        title (str year ", " (month->name month))]
    (list-results node title "spellbook/archive" page-options)))

(defn- list-tags [node hash]
  (let [tag (js/decodeURIComponent (second (re-find #"tag/(.+)$" hash)))
        page-options {:reduce false
                      :startkey (str tag "\uffff")
                      :endkey (str tag "\u0000")
                      :include_docs true
                      :descending true}
        title (str "#" tag)]
    (list-results node title "spellbook/tags" page-options)))

(defn- list-search [node hash]
  (let [word (second (re-find #"search/(.+)$" hash))
        page-options {:reduce false
                      :startkey (str word "\uffff")
                      :endkey (str word "\u0000")
                      :include_docs true
                      :descending true}
        title (str "\"" word "\"")]
    (list-results node title "spellbook/text" page-options)))

(defn- show-archive [node _hash]
  (.then (db/query-view db "spellbook/archive" {:group true
                                                :group_level 2})
         (fn [results]
           (.replaceChildren node (alchemize (archive results))))))

(defn- show-tags [node _hash]
  (.then (db/query-view db "spellbook/tags" {:group true
                                             :group_level 1})
         (fn [results]
           (.replaceChildren node (alchemize (tags results))))))

(defn- landing []
  (.then (db/query-view db "spellbook/archive" {:reduce false :limit 1})
         (fn [result]
           (if (seq result)
             (goto "recent")
             (goto "compose")))))

;; FINALLY...

(def ROUTES
  {#"compose$" compose-entry
   #"recent$" list-recent
   #"archive$" show-archive
   #"archive/.+?/.+?$" list-archive
   #"tags$" show-tags
   #"tag/.+$" list-tags
   #"search/.+$" list-search
   #"^$" landing})

(defn spellbook-view [node]
  (.appendChild node (alchemize (container)))
  (let [main-div (snag "main")
        router (partial handle-route ROUTES main-div)
        refresh #(router js/document.location.hash)]
    (js/window.addEventListener "popstate" refresh)
    (.then (setup-db) #(refresh))))

;; WEBCOMPONENT

(defclass SpellbookApp
  (extends js/HTMLElement)
  (constructor [this] (super this))
  Object
  (connectedCallback [this] (spellbook-view this)))

(try
  (js/customElements.define "spellbook-app" SpellbookApp)
  (catch js/Error _ (js/window.location.reload)))
