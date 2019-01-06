(ns aido.nodes
  (:require [clojure.test :refer :all]
            [aido.options :as ao]
            [aido.core :as ai]
            [aido.tick :as at]))

(defmethod ao/options :equal? [& _] [:v1 :v2])

(defmethod at/tick :equal?
  [db [node-type {:keys [v1 v2]} & _]]
  (ai/tick-result (ai/succeed-if (= v1 v2)) db))

(defmethod ao/options :present? [& _] [:key])

(defmethod at/tick :present?
  [db [node-type {:keys [key] :as options} children]]
  (ai/tick-result (ai/succeed-if (contains? db key)) db))


(defmethod ao/options :test? [& _] [:key :oper :val])

(defmethod at/tick :test?
  [db [node-type {:keys [key oper val] :as options} & _]]
  (let [val*   (get db key)
        result (oper val val*)]
    (ai/tick-result (ai/succeed-if result) db)))


(defmethod ao/options :less-than? [& _] [:key :val])

(defmethod at/tick :less-than?
  [db [node-type {:keys [key val]} & _]]
  (ai/tick-result (ai/succeed-if (< (get db key) val)) db))


(defmethod ao/options :greater-than? [& _] [:key :val])

(defmethod at/tick :greater-than?
  [db [node-type {:keys [key val]}]]
  (ai/tick-result (ai/succeed-if (> (get db key) val)) db))


(defmethod ao/options :define! [& _] [:key :val])

(defmethod at/tick :define!
  [db [node-type {:keys [key val] :as options}]]
  (ai/tick-success (assoc db key val)))


(defmethod ao/options :counter! [& _] [:key])

(defmethod at/tick :counter!
  [db [node-type {:keys [key] :as options} _]]
  #_(println "counter:" key " <" (get db key))
  (ai/tick-success (update db key (fnil inc 0))))


(defmethod ao/children :parent-2 [& _] 2)

(defmethod at/tick :parent-2
  [db & _]
  (ai/tick-success db))

(defmethod ao/children :parent-+ [& _] :+)

(defmethod at/tick :parent-+
  [db & _]
  (ai/tick-success db ))

(defmethod ao/children :parent-set [& _] #{2 3})

(defmethod at/tick :parent-set
  [db & _]
  (ai/tick-success db))
