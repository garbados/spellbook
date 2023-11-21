(ns spellbook.db
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            ["pouchdb" :as pouchdb]
            ["pouchdb-jsonviews" :as jsonview]))

(.plugin pouchdb jsonview)

(defn init-db
  ([name]
   (new pouchdb name))
  ([name opts]
   (new pouchdb name opts)))

(defn unmarshal-doc [doc]
  (edn/read-string (:-value (js->clj doc :keywordize-keys true))))

(defn marshal-doc [base value to-index]
  (clj->js
   (merge base
          (select-keys value to-index)
          {:-value (pr-str value)})))

(defn save-doc
  ([db id value]
   (save-doc db id value []))
  ([db id value to-index]
   (.put db (marshal-doc {:_id (str id)} value to-index))))

(defn upsert-doc
  ([db id value]
   (upsert-doc db id value []))
  ([db id value to-index]
   (.catch (save-doc db id value to-index)
           (fn [e]
             (if (= 409 (.-status e))
               (.then (.get db id)
                      #(.put db (marshal-doc (js->clj % {:keywordize-keys true})
                                             value to-index)))
               (throw e))))))

(defn resolve-id [db id]
  (.then (.get db id) unmarshal-doc))

(defn remove-id! [db id]
  (-> (.get db id)
      (.then #(.remove db %))))

(defn put-view [db id view-name json-view]
  (.putJsonView ^js/Object db id view-name (clj->js json-view)))

(defn query-view
  ([db view-id]
   (query-view db view-id {}))
  ([db view-id opts]
   (.query ^js/Object db view-id (clj->js opts))))


(s/def ::f ifn?)
(s/def ::skip nat-int?)
(s/def ::next-page? boolean?)
(s/def ::prev-page? boolean?)
(s/def ::paginator (s/keys :req-un [::f
                                    ::skip
                                    ::initial
                                    ::next-page?
                                    ::prev-page?]))

(defn make-paginator [f limit opts]
  {:f f
   :skip 0
   :limit limit
   :initial opts
   :next-page? true
   :prev-page? false})

(defn normalize-page [results]
  (js->clj (.-rows results) :keywordize-keys true))

(defn call-paginator [paginator opts]
  (.then ((:f paginator) (clj->js opts)) normalize-page))

(defn paginate-view
  ([db view-id]
   (paginate-view db view-id {}))
  ([db view-id opts]
   (let [f #(.paginateQuery ^js/Object db view-id (clj->js %))]
     (make-paginator f 20 opts))
   ))

(defn next-page [paginator]
  (let [skip (:skip paginator)
        opts (assoc (:initial paginator)
                    :limit (:limit paginator)
                    :skip skip)]
    (.then
     (call-paginator paginator opts)
     (fn [rows]
       [(-> paginator
            (update :skip + (:limit paginator))
            (assoc :next-page? (= (count rows)
                                  (:limit paginator))))
        rows]))))

(defn same-page [paginator]
  (let [skip (:skip paginator)
        opts (assoc (:initial paginator)
                    :limit (:limit paginator)
                    :skip skip)]
    (.then
     (call-paginator paginator opts)
     (fn [rows]
       [paginator
        rows]))))

(defn prev-page [paginator]
  (let [skip (max 0 (- (:skip paginator) (:limit paginator)))
        opts (assoc (:initial paginator)
                    :limit (:limit paginator)
                    :skip skip)]
    (.then
     (call-paginator paginator opts)
     (fn [rows]
       [(-> paginator
            (update :skip - (:limit paginator))
            (update :skip max 0)
            (assoc :prev-page? (= 0 skip)))
        rows]))))
