(ns aido.nodes
  (:require [clojure.test :refer :all]
            [aido.options :as ao]
            [aido.core :as ai]))

(defmethod ao/options :equal? [& _] [:v1 :v2])

(defmethod ai/tick :equal?
  [db [node-type {:keys [v1 v2]} & _]]
  (ai/tick-result (ai/succeed-if (= v1 v2)) db))

(defmethod ao/options :present? [& _] [:key])

(defmethod ai/tick :present?
  [db [node-type {:keys [key] :as options} children]]
  (ai/tick-result (ai/succeed-if (contains? db key)) db))


(defmethod ao/options :test? [& _] [:key :oper :val])

(defmethod ai/tick :test?
  [db [node-type {:keys [key oper val] :as options} & _]]
  (let [val*   (get db key)
        result (oper val val*)]
    (ai/tick-result (ai/succeed-if result) db)))


(defmethod ao/options :less-than? [& _] [:key :val])

(defmethod ai/tick :less-than?
  [db [node-type {:keys [key val]} & _]]
  (ai/tick-result (ai/succeed-if (< (get db key) val)) db))


(defmethod ao/options :greater-than? [& _] [:key :val])

(defmethod ai/tick :greater-than?
  [db [node-type {:keys [key val]}]]
  (ai/tick-result (ai/succeed-if (> (get db key) val)) db))


(defmethod ao/options :define! [& _] [:key :val])

(defmethod ai/tick :define!
  [db [node-type {:keys [key val] :as options}]]
  (ai/tick-success (assoc db key val)))


(defmethod ao/options :counter! [& _] [:key])

(defmethod ai/tick :counter!
  [db [node-type {:keys [key] :as options} _]]
  #_(println "counter:" key " <" (get db key))
  (ai/tick-success (update db key (fnil inc 0))))


(defmethod ao/children :parent-2 [& _] 2)

(defmethod ao/children :parent-+ [& _] :+)

(defmethod ao/children :parent-set [& _] #{2 3})
