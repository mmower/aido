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

(defn replace-fn-options
  "Given a map of options replace-fn-options replaces those that are intended to be dynamic. A dynamic
  option is an option whose value is a vector of the form [:fn/name ...] or [:fn*/name ...]. In this
  case 'name' should be the name of a function passed to the compile function. Where the name is in
  the 'fn' namespace the replacement is a function call. Where the name is in the 'fn*' namespace the
  replacement is the result of calling the function. Hence [:fn*/name ...] does a dymamic replacement
  at compile time and [:fn/name ...] does a dynamic replacement each time the behaviour tree is walked.

  Note that, in order that behaviour trees can be serialised to EDN where a dynamic replacement is
  created the function is added as metadata to the value. This means that the function will need to
  be regenerated each time the tree is deserialised. Compile time replacements are replacements by
  values that can be serialised."
  [fns opts]
  (reduce (fn [opts* [opt val]]
            (let [actual (if (and (vector? val) (keyword? (first val)))
                           (let [op    (first val)
                                 op-ns (namespace op)
                                 op-fn (get fns (keyword (name op)))]
                             (cond
                               ; [:fn/foo ...] remains [:fn/foo ...] with an attached function
                               ; in it's metadata under the :fn key that, when called, returns
                               ; the appropriate value for the option
                               (and (= "fn" op-ns) op-fn) (with-meta val {:fn (fn [] (apply op-fn (rest val)))})
                               ; [:ifn/foo ...] is translated into the value of applying the
                               ; function to its arguments. This means that the value is immediately
                               ; replaced in the compiled behaviour tree and [:ifn*/foo ...] is lost
                               (and (= "ifn" op-ns) op-fn) (apply op-fn (rest val))
                               :else val))
                           val)]
              (assoc opts* opt actual))) {} opts))

(defn realise-options
  "Given an options map where some options may represent dynamic function values (see replace-fn-options)
  of the form [:fn/foo ...] replace the function values with the result of the function applications."
  [[node-type options & children]]
  (let [options* (reduce (fn [opts* [opt val]]
                           (if-let [f (:fn (meta val))]
                             (assoc opts* opt (f))
                             (assoc opts* opt val))) {} options)]
    (into [node-type options*] children)))

(defn compile
  ([btree]
   (compile btree {}))
  ([btree fns]
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
                                      (assoc opts :id id))
                           new-opts* (replace-fn-options fns new-opts)]
                       (verify-children req-children children)
                       (into [node-type new-opts*] children))
                     #?(:clj  (catch Exception e
                                (throw (ex-info (str "While processing: " node) {} e)))
                        :cljs (catch :default e
                                (throw (ex-info (str "While processing: " node) {} e))))))
                 node)) btree)))

