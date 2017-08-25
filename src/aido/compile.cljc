(ns aido.compile
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]
            [aido.options :as ao]))

(defn walk
  "A variation on clojure.walk/walk that treats maps as discrete values
  rather than as key-value pairs to be walked."
  [inner outer form]
  (cond
    (list? form) (outer (apply list (map inner form)))
    (instance? clojure.lang.IMapEntry form) (outer (vec (map inner form)))
    (seq? form) (outer (doall (map inner form)))
    (map? form) (outer form)
    (instance? clojure.lang.IRecord form)
    (outer (reduce (fn [r x] (conj r (inner x))) form form))
    (coll? form) (outer (into (empty form) (map inner form)))
    :else (outer form)))

(defn postwalk
  "A variation on clojure.walk/postwalk that uses our custom walk function."
  [f form]
  (walk (partial postwalk f) f form))

(defn compile [t]
  (let [id-source (atom 0)]
    (postwalk (fn [node]
                (if (vector? node)
                  (let [[node-type & args] node
                        id       (swap! id-source inc)
                        req-opts (ao/required-options node)]
                    (try
                      (let [[opts children] (ao/parse-options args :required req-opts)
                            new-opts (assoc opts :id id)]
                        (into [node-type new-opts] children))
                      (catch Exception ex
                        (throw (Exception. (str "While processing " node " " (.getMessage ex))))))) node)) t)))

