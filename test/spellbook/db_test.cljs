(ns spellbook.db-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures] :refer [async]]
            [spellbook.db :as db]))

(defonce -db (db/init-db ".test"))

(use-fixtures :once
  (fn [f]
    (async done
           (f)
           (.then (.destroy -db)
                  #(done)))))

(deftest ok?
  (testing "DB exists!"
    (is (some? -db))))
