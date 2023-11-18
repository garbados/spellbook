(ns spellbook.core
  (:require [clj-cbor.core :as cbor]
            [clojure.spec.alpha :as s]
            [reagent.core :as r]
            [reagent.dom :as rd]
            ["pouchdb" :as pouchdb]))

(defn- app []
  [:h1 "Hello world"])

(defn- main []
  (rd/render [app] (js/document.getElementById "app")))

(main)
