(ns aido.compile
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]
            [aido.options :as ao]))

; We use a top-level atom because we want the ID of all behaviours across any tree to
; be unique since they may be referred to from a global map without context.
;
(def ^:private id-source (atom 0))

(defn verify-children [node required actual]
  (let [child-count (count actual)
        passes      (cond (set? required) (get required child-count)
                          (number? required) (= required child-count)
                          (= :some required) (> child-count 0)
                          (= :+ required) (> child-count 0)
                          (= :any required) true
                          (= :* required) true
                          :else (throw (ex-info "Invalid children specification" {:node      node
                                                                                  :specifier required})))]
    (if-not passes
      (let [message (str "Incorrect children passed! expected:"
                         required
                         " saw:"
                         child-count
                         " for behaviour:\""
                         node
                         "\"")]
        (throw (ex-info message {:node-type (first node)
                                 :children  actual
                                 :expected  required
                                 :actual    child-count}))))))

; a dynamic option value looks like: [:aido/fn ..] or [:aido/db ..]

(defn replace-dynamic-option
  "Given an option opt val where val is of the form [:aido/... ...] perform a dynamic replacement."
  [do-fns opts [opt val]]
  (let [opts* (assoc opts opt val)]
    (if (and (vector? val) (keyword? (first val)) (= "aido" (namespace (first val))))
      (let [op (keyword (name (first val)))]
        (case op
          :db (let [key-path (rest val)]
                (vary-meta opts* update :db-opts assoc opt (fn [db] (get-in db key-path))))
          :fn (let [[fn-id & args] (rest val)
                    do-fn (get do-fns fn-id)]
                (if (nil? do-fn)
                  (throw (ex-info (str "No such function '" fn-id "' found in dynamic functions [" do-fns "]") {})))
                (vary-meta opts* update :fn-opts assoc opt (fn [] (apply do-fn args))))))
      opts*)))

(defn replace-dynamic-options
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
  (let [replacer (partial replace-dynamic-option fns)]
    (reduce replacer {} opts)))

(defn next-auto-id
  "Return next sequential id."
  []
  (swap! id-source inc))

(defn assign-auto-id
  "Given a map object, if it does not have a value for key :id then assign an auto-id value."
  [mo]
  (if (:id mo)
    mo
    (assoc mo :id (next-auto-id))))

(defn- realise-fn-opt [opts [opt f]]
  (assoc opts opt (f)))

(defn- realise-db-opt [db opts [opt f]]
  (assoc opts opt (f db)))

(defn realise-options
  "Given an options map where some options may represent dynamic function values (see replace-fn-options)
   'realise' those dynamic values and pass them instead."
  [db [node-type options & children]]
  (let [{:keys [fn-opts db-opts]} (meta options)
        options* (as-> options $
                       (reduce realise-fn-opt $ fn-opts)
                       (reduce (partial realise-db-opt db) $ db-opts))]
    (into [node-type options*] children)))

(defn compile
  ([tree]
   (compile tree {}))
  ([tree opt-fns]
   (if-not (vector? tree)
     (throw (ex-info (str "Unexpected input: " tree) {}))
     (let [[node-type & tail] tree]
       (if-not (keyword? node-type)
         (throw (ex-info (str "Expected behaviour keyword: " node-type " (" (type node-type) ") in tree:" tree) {})))
       (let [req-opts     (ao/options tree)
             req-children (ao/children tree)
             [opts children] (ao/parse-options tree tail :required req-opts)
             opts*        (->> opts
                               (replace-dynamic-options opt-fns)
                               (assign-auto-id))]
         (verify-children tree req-children children)
         (into [node-type opts*] (map (fn [child] (compile child opt-fns)) children)))))))
