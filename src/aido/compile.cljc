(ns aido.compile
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]
            [aido.options :as ao]))

; We use a top-level atom because we want the ID of all behaviours across any tree to
; be unique since they may be referred to from a global map without context.
;
(def ^:private id-source (atom 0))

(defn verify-children [node required actual]
  (let [child-count (count actual)]
    (cond
      (set? required) (if-not (get required child-count) (throw (ex-info (str node " -- children required:" required " actual:" child-count) {})))
      (= 0 required) (if (not= child-count 0) (throw (ex-info (str node " -- children required:0 actual:" child-count) {})))
      (= 1 required) (if (not= child-count 1) (throw (ex-info (str node " -- children required:1 actual:" child-count) {})))
      (= :some required) (if (< child-count 1) (throw (ex-info (str node " -- children required:>1 actual:0") {})))
      (= :any required) nil
      true (throw (ex-info (str node " -- invalid children specified:" required) {})))))

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

(defn next-auto-id []
  (swap! id-source inc))

(comment
  (if-not (empty? tail)
    (if (map? (first tail))
      (let [[options & children] tail]
        (if (empty? children)
          [node-type (merge {:id (next-auto-id)} options)]
          [node-type (merge {:id (next-auto-id)} options) (mapv compile2 children)]))
      [node-type {:id (next-auto-id)} (mapv compile2 tail)]) ; assigned auto-id and process the kids
    [node-type {:id (next-auto-id)}]                   ; no children, just add the auto-id
    ))

(defn compile
  ([tree]
    (compile tree {}))
  ([tree opt-fns]
   (if-not (vector? tree)
     (throw (ex-info (str "Unexpected input: " tree) {}))
     (let [[node-type & tail] tree]
       (if-not (keyword? node-type)
         (throw (ex-info (str "Expected behaviour keyword: " node-type " in tree:" tree) {})))
       (let [req-opts     (ao/options tree)
             req-children (ao/children tree)
             [opts children] (ao/parse-options tail :required req-opts)
             opts*        (->> opts
                               (replace-fn-options opt-fns)
                               (merge {:id (next-auto-id)}))]
         (verify-children tree req-children children)
         (into [node-type opts*] (map compile children)))))))
