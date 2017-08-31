(ns aido.compile
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]
            [aido.error :refer [throw-error]]
            [aido.options :as ao]))

(defn walk [inner outer form]
  (if (vector? form)
    (outer (mapv inner form))
    (outer form)))

(defn postwalk
  "A variation on clojure.walk/postwalk that uses our custom walk function."
  [f form]
  (walk (partial postwalk f) f form))

(defn verify-children [required actual]
  (let [child-count (count actual)]
    (cond
      (set? required) (if-not (get required child-count) (throw-error (str "children required:" required " actual:" child-count)))
      (= 0 required) (if (not= child-count 0) (throw-error (str "children required:0 actual:" child-count)))
      (= 1 required) (if (not= child-count 1) (throw-error (str "children required:1 actual:" child-count)))
      (= :some required) (if (< child-count 1) (throw-error (str "children required:>1 actual:0")))
      (= :any required) nil
      true (throw-error (str "invalid children specified:" required)))))

(defn compile [t]
  (let [id-source (atom 0)]
    (postwalk (fn [node]
                (if (vector? node)
                  (let [[node-type & args] node
                        id           (swap! id-source inc)
                        req-opts     (ao/required-options node)
                        req-children (ao/required-children node)]
                    (try
                      (let [[opts children] (ao/parse-options args :required req-opts)
                            new-opts (assoc opts :id id)]
                        (verify-children req-children children)
                        (into [node-type new-opts] children))
                      #?(:clj  (catch Exception ex
                                 (throw-error (str "While processing " node " " (.getMessage ex))))
                         :cljs (catch :default e
                                 (throw-error (str "While processing " node " " e))))))
                  node)) t)))

