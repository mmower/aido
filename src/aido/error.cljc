(ns aido.error)

(defn throw-error [^String message]
  (let [error #?(:clj (Exception. message)
                 :cljs (js/Error. message))]
    (throw error)))
