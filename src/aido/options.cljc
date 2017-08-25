(ns aido.options
  (:require [clojure.string :as str]))

(defn missing-options [options required]
  "Checks the options map has all the required keys. Returns a vector of missing keys."
  (reduce (fn [rval option]
            (if (contains? options option)
              rval
              (conj rval option))) [] required))

(comment (defn has-required-options? [options required]
           "Tests that the options map has all of the keys specified in the required vector."
           (reduce (fn [rval option]
                     (and rval (contains? options option))) true required)))

(defn parse-options [args & {:keys [required] :or {required []}}]
  "Returns a tuple containing an options map and a sequence of arguments. Where the first
  passed in argument is a map returns that as the options map, otherwise returns an empty
  options map. Optionally verifies the options contain specified keys."

  (let [options        (if (map? (first args))
                         (first args)
                         {})
        remaining-args (if (map? (first args))
                         (into [] (rest args))
                         args)
        missing        (missing-options options required)]
    (if (empty? missing)
      [options remaining-args]
      (throw (Exception. (str "Required options missing: " (str/join ", " missing)))))))

(defmulti required-options
          "The options function specifies the expected options for a given node type."
          (fn [[node & _]]
            (println "required-options.node:" node)
            node))

(defmethod required-options :default [& _]
  [])
