(ns aido.compile
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]
            [aido.options :as ao]))

; We use a top-level atom because we want the ID of all behaviours across any tree to
; be unique since they may be referred to from a global map without context.
;
(def ^:private id-source (atom 0))

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
      (set? required) (if-not (get required child-count) (throw (ex-info (str "children required:" required " actual:" child-count) {})))
      (= 0 required) (if (not= child-count 0) (throw (ex-info (str "children required:0 actual:" child-count) {})))
      (= 1 required) (if (not= child-count 1) (throw (ex-info (str "children required:1 actual:" child-count) {})))
      (= :some required) (if (< child-count 1) (throw (ex-info (str "children required:>1 actual:0") {})))
      (= :any required) nil
      true (throw (ex-info (str "invalid children specified:" required) {})))))

(defn compile [t]
  (postwalk (fn [node]
              (if (vector? node)
                (let [[node-type & args] node
                      id           (swap! id-source inc)
                      req-opts     (ao/options node)
                      req-children (ao/children node)]
                  (try
                    (let [[opts children] (ao/parse-options args :required req-opts)
                          new-opts (if (contains? opts :id) ; don't override a manually assigned :id
                                     opts
                                     (assoc opts :id id))]
                      (verify-children req-children children)
                      (into [node-type new-opts] children))
                    #?(:clj  (catch Exception e
                               (throw (ex-info (str "While processing: " node) {} e)))
                       :cljs (catch :default e
                               (throw (ex-info (str "While processing: " node) {} e))))))
                node)) t))

