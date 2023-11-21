(ns spellbook.db
  (:require [clojure.edn :as edn]
            ["pouchdb" :as pouchdb]
            ["pouchdb-jsonviews" :as jsonview]
            ["pouchdb-paginators" :as paginators]))

(.plugin pouchdb jsonview)
(.plugin pouchdb paginators)

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

(defn paginate-view
  ([db view-id]
   (paginate-view db view-id {}))
  ([db view-id opts]
   (.paginateQuery ^js/Object db view-id (clj->js opts))))

(defn has-prev-page? [paginator]
  (< 1 (.-length (.-lastopts ^js/Object paginator))))

(defn has-next-page? [paginator]
  (.-hasNextPage ^js/Object paginator))

(defn normalize-page [results]
  (js->clj (.-rows results) :keywordize-keys true))

(defn next-page [paginator]
  (.then (.getNextPage ^js/Object paginator)
         normalize-page))

(defn prev-page [paginator]
  (.then (.getPrevPage ^js/Object paginator)
         normalize-page))

(defn same-page [paginator]
  (.then (.getSamePage ^js/Object paginator)
         normalize-page))
