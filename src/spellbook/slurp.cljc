(ns spellbook.slurp)

(defmacro inline-slurp [path]
  (clojure.core/slurp path))
