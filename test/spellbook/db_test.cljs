(ns spellbook.db-test
  (:require [cljs.test
             :refer-macros [deftest is testing use-fixtures async run-tests]]
            [spellbook.db :as db]))

(defonce -db (atom nil))

(use-fixtures :each
  {:before
   #(reset! -db (db/init-db ".test"))
   :after
   #(async done (.then (.destroy ^js/Object @-db) done))})

(deftest save-resolve-test
  (testing "Save and retrieve doc"
    (async
     done
     (let [id (str (random-uuid))
           value {:omg :wow}]
       (-> (db/save-value @-db id value [])
           (.then #(db/resolve-value @-db id))
           (.then #(is (= value %)))
           (.finally done))))))

(deftest save-delete-test
  (testing "Save and delete doc"
    (async
     done
     (let [id (str (random-uuid))
           value {:omg :wow}]
       (-> (db/save-doc @-db id value)
           (.then #(db/remove-id! @-db id))
           (.then #(db/snag-doc @-db id))
           (.then #(throw (ex-info "Should not work!" {})))
           (.catch #(is (= 404 (.-status %))))
           (.finally done))))))

(deftest upsert-test
  (testing "Upsert works"
    (async
     done
     (let [id (str (random-uuid))
           value {:omg :wow}]
       (-> (db/save-value @-db id value)
           (.then #(db/upsert-value @-db id (assoc value :omg :bogus)))
           (.then #(db/resolve-value @-db id))
           (.then #(is (= :bogus (:omg %))))
           (.finally done))))))

(deftest indexing-test
  (testing "Indexing works"
    (async
     done
     (-> (.all
          js/Promise
          (concat (for [_ (range 100)
                        :let [doc {:a (rand-int 10)
                                   :b (rand-nth ["a" "b" "c"])}]]
                    (db/save-value @-db (str (random-uuid)) doc [:a]))
                  [(db/upsert-ddoc!
                    @-db "testing"
                    {:views
                     {:a
                      {:map "function (doc) { emit(doc.b, doc.a) }"
                       :reduce "_sum"}}})]))
         (.then #(db/paginate-view @-db "testing/a" {:reduce false}))
         (.then #(db/next-page %))
         (.then #(is (= 20 (count (second %)))))
         (.then #(db/paginate-view @-db "testing/a"))
         (.then #(db/next-page %))
         (.then #(let [[{:keys [key value]}] (second %)]
                   (is (and (number? value)
                            (nil? key)))))
         ; selectively paginate
         (.then #(db/save-value @-db (str (random-uuid))
                                {:a 0 :b "a"}
                                [:a :b]))
         (.then #(db/paginate-view @-db "testing/a" {:group true}))
         (.then #(db/next-page %))
         (.then #(is (= 2 (count %))))
         ; include docs!
         (.then #(db/paginate-view @-db "testing/a" {:reduce false
                                                     :include_docs true}))
         (.then #(db/next-page %))
         (.then #(and (is (= 20 (count (second %))))
                      (is (-> % second first :doc db/unmarshal-doc :a number?))))
         (.finally done)))))

(enable-console-print!)
(run-tests)
