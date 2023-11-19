(ns spellbook.db
  (:require [clojure.edn :as edn]
            ["pouchdb" :as pouchdb]
            ["pouchdb-jsonviews" :as jsonview]
            ["pouchdb-paginators" :as paginators]))

(.plugin pouchdb jsonview)
(.plugin pouchdb paginators)

(defn init-db
  ([name]
   (let [db (new pouchdb name)]
     (.paginate db)
     db))
  ([name opts]
   (let [db (new pouchdb name opts)]
     (.paginate db)
     db)))

(defn marshal-doc [base id value & to-index]
  (->> (select-keys value (or to-index []))
       (merge base
              {:_id (str id)
               :-value (pr-str value)})
       clj->js))

(defn save-doc [db id value & to-index]
  (.put db (marshal-doc {} id value to-index)))

(defn upsert-doc [db id value & to-index]
  (.catch (save-doc db id value to-index)
          (fn [_]
            (.then (.get db id)
                   #(.put db (marshal-doc % id value to-index))))))

(defn resolve-id [db id]
  (.then (.get db id)
         (fn [doc]
           (edn/read-string (.-value doc)))))

(defn remove-id! [db id]
  (-> (.get db id)
      (.then #(.remove db %))))
