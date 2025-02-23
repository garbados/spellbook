(ns spellbook.db
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            ["pouchdb" :as pouchdb]))

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
  ([db id doc]
   (.put db (clj->js (merge {:_id id} doc)))))

(defn snag-doc [db id]
  (.then (.get db id) #(js->clj % :keywordize-keys true)))

(defn resolve-value [db id]
  (.then (.get db id) unmarshal-doc))

(defn save-value
  ([db id value]
   (save-value db id value []))
  ([db id value to-index]
   (.put db (marshal-doc {:_id (str id)} value to-index)))
  ([db id value to-index rev]
   (.put db (marshal-doc {:_id (str id) :_rev rev} value to-index))))

(defn upsert-value
  ([db id value]
   (upsert-value db id value []))
  ([db id value to-index]
   (.then (snag-doc db id)
          (fn [{rev :_rev}]
            (save-value db id value to-index rev)))))

(defn upsert-doc!
  ([db id doc]
   (upsert-doc! db id doc #(assoc doc :_rev (:_rev %))))
  ([db id doc mergefn]
   (.catch (save-doc db id doc)
           (fn [e]
             (if (= 409 (.-status e))
               (.then (snag-doc db id)
                      #(save-doc db id (mergefn % doc)))
               (throw e))))))

(defn upsert-ddoc!
  [db ddoc-id ddoc]
  (.catch
   (.put db (clj->js (assoc ddoc :_id ddoc-id)))
   (fn [e]
     (if (= 409 (.-status e))
       (.then (.get db ddoc-id)
              (fn [other-ddocjs]
                (let [{rev :_rev :as other-ddoc}
                      (js->clj other-ddocjs :keywordize-keys true)]
                  (when (not= (select-keys other-ddoc [:views]) ddoc)
                    (.put db (clj->js (assoc ddoc :_id ddoc-id :_rev rev)))))))
       (throw e)))))

(defn remove-id! [db id]
  (-> (.get db id)
      (.then #(.remove db %))))

(defn normalize-results [results]
  (js->clj (.-rows results) :keywordize-keys true))

(defn query-view
  ([db view-id]
   (query-view db view-id {}))
  ([db view-id opts]
   (.then (.query ^js/Object db view-id (clj->js opts)) normalize-results)))

(s/def ::f ifn?)
(s/def ::skip nat-int?)
(s/def ::limit nat-int?)
(s/def ::initial map?)
(s/def ::next-page? boolean?)
(s/def ::prev-page? boolean?)
(s/def ::paginator (s/keys :req-un [::f
                                    ::skip
                                    ::limit
                                    ::initial
                                    ::next-page?
                                    ::prev-page?]))

(defn make-paginator [f opts]
  {:f f
   :skip 0
   :limit (:limit opts 20)
   :initial opts
   :next-page? true
   :prev-page? false})

(s/fdef make-paginator
  :args (s/cat :f ifn?
               :limit nat-int?
               :opts ::initial)
  :ret ::paginator)

(defn paginate-view
  ([db view-id]
   (paginate-view db view-id {}))
  ([db view-id opts]
   (let [f #(.query ^js/Object db view-id (clj->js %))]
     (make-paginator f opts))))

(defn- call-paginator [paginator opts]
  (.then ((:f paginator) (clj->js opts)) normalize-results))

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
                                  (:limit paginator)))
            (assoc :prev-page? (not= 0 skip)))
        rows]))))

(defn same-page [paginator]
  (let [skip (max 0 (- (:skip paginator) (:limit paginator)))
        opts (assoc (:initial paginator)
                    :limit (:limit paginator)
                    :skip skip)]
    (.then
     (call-paginator paginator opts)
     (fn [rows]
       [paginator
        rows]))))

(defn prev-page [paginator]
  (let [skip (max 0 (- (:skip paginator) (* 2 (:limit paginator))))
        opts (assoc (:initial paginator)
                    :limit (:limit paginator)
                    :skip skip)]
    (.then
     (call-paginator paginator opts)
     (fn [rows]
       [(-> paginator
            (update :skip - (:limit paginator))
            (update :skip max 0)
            (assoc :prev-page? (not= 0 skip)))
        rows]))))
