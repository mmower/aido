(ns aido.nodes
  (:require [clojure.test :refer :all]
            [aido.options :as ao]
            [aido.core :as ai]))

(defmethod ao/options :present? [& _] [:key])

(defmethod ai/tick :present?
  [db [node-type {:keys [key] :as options} children] wmem]
  (ai/tick-result (ai/succeed-if (contains? db key)) db))


(defmethod ao/options :test? [& _] [:key :oper :val])

(defmethod ai/tick :test?
  [db [node-type {:keys [key oper val] :as options} & _] wmem]
  (let [result (oper val (get db key))]
    (ai/tick-result (ai/succeed-if result) db)))


(defmethod ao/options :less-than? [& _] [:key :val])

(defmethod ai/tick :less-than?
  [db [node-type {:keys [key val]} & _] wmem]
  (ai/tick-result (ai/succeed-if (< (get db key) val)) db))


(defmethod ao/options :greater-than? [& _] [:key :val])

(defmethod ai/tick :greater-than?
  [db [node-type {:keys [key val]}] wmem]
  (ai/tick-result (ai/succeed-if (> (get db key) val)) db))


(defmethod ao/options :define! [& _] [:key :val])

(defmethod ai/tick :define!
  [db [node-type {:keys [key val] :as options}] wmem]
  (ai/tick-success (assoc db key val)))


(defmethod ao/options :counter! [& _] [:key])

(defmethod ai/tick :counter!
  [db [node-type {:keys [key] :as options} _] wmem]
  #_(println "counter:" key " <" (get db key))
  (ai/tick-success (update db key (fnil inc 0))))
