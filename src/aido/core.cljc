(ns aido.core
  (:require [clojure.string :as str]
            [aido.error :refer [throw-error]]
            [aido.options :as ao]
            [aido.compile :as ac]))

(def SUCCESS :success)
(def FAILURE :failure)
(def RUNNING :running)
(def ERROR :error)

; All tick functions return an instance of the TickResult type wrapping the status of the tick for their
; specific node and the database state after the tick function has run.
(defrecord TickResult [status db])

(def valid-statuses #{SUCCESS FAILURE RUNNING ERROR})

(defn tick-result [status db]
  (assert (valid-statuses status) "Invalid status")
  (TickResult. status db))

(defn succeed-if [pred]
  (if pred SUCCESS FAILURE))

(defn has-succeeded?
  "Given a TickResult returns true if the status is SUCCESS or RUNNING"
  [result]
  (let [status (:status result)]
    (or (= status SUCCESS)
        (= status RUNNING))))

(def has-failed? (complement has-succeeded?))

(defn tick-node-type
  "Given a node [node-type & _] return the node-type for multi-method discrimination"
  [db [node-type & _]] node-type)

(defmulti tick
          "The tick function sends the tick to a node of different types."
          {:arglists '([db options node])}
          tick-node-type)

; Core node types

; LOOP
;
; The loop node allows to execute one child ... a multiple number of times, specified by the
; {:count n} parameter or until the child returns FAILURE.
;
; If no count is specified looping continues indefinitely
; The loop returns SUCCESS if the specified number of iterations succeed
; The loop returns FAILURE if the child fails on any iteration

(defmethod ao/required-options :loop [& _]
  [:count])

(defmethod ao/required-children :loop [& _]
  1)

(defmethod tick :loop [db [node-type options & [child & _]]]
  (loop [db db
         n  0]
    (if (= n (:count options))
      (TickResult. SUCCESS db)
      (let [result (tick db child)]

        (if (has-failed? result)
          result
          (recur (:db result) (inc n)))))))


(defmethod ao/required-options :loop-until-success [& _]
  [:count])

(defmethod ao/required-children :loop-until-success [& _]
  1)

(defmethod tick :loop-until-success [db [node-type options & [child & _]]]
  (loop [db db
         n  0]
    (if (= n (:count options))
      (TickResult. FAILURE db)
      (let [result (tick db child)]
        (if (has-succeeded? result)
          result
          (recur (:db result) (inc n)))))))

; PARALLEL
;
; The parallel node executes all of its children in parallel
;
; Parameters
; :success-mode :any (*) | :all
; :failure-mode :any | :all (*)
;
; When success-mode is :any the :parallel node returns SUCCESS if any child returns SUCCESS
; When success-mode is :all the :parallel node returns SUCCESS if all children return SUCCESS
; Similarly for :failure-mode
;
; If SUCCESS and FAILURE limits are hit at the same time SUCCESS wins

(defmethod ao/required-options :parallel [& _]
  [:mode :test])

(defmethod ao/required-children :parallel [& _]
  :some)

(defmethod tick :parallel [db [node-type {:keys [mode test]} & children]]
  (let [c (count children)
        {:keys [success failure db]} (reduce (fn [{:keys [success failure db]} child]
                                               (let [result (tick db child)]
                                                 {:success (if (has-succeeded? result) (inc success) success)
                                                  :failure (if (has-failed? result) (inc failure) failure)
                                                  :db      (:db result)})
                                               ) {:success 0 :failure 0 :db db} children)]
    (cond
      (and (= :success mode) (= :all test) (= c success)) (TickResult. SUCCESS db)
      (and (= :success mode) (= :any test) (> success 0)) (TickResult. SUCCESS db)
      (= :success mode) (TickResult. FAILURE db)
      (and (= :failure mode) (= :all test) (= c failure)) (TickResult. FAILURE db)
      (and (= :failure mode) (= :any test) (> failure 0)) (TickResult. FAILURE db)
      (= :failure mode) (TickResult. SUCCESS db))))

; SELECTOR
;
; The :selector node executes its children sequentially stopping after the first one that
; returns SUCCESS or RUNNING
;
; If no child returns SUCCESS the :select returns FAILURE

(defmethod ao/required-children :selector [& _]
  :some)

(defmethod tick :selector [db [node-type options & children]]
  (loop [db        db
         child     (first children)
         remaining (rest children)]
    (let [{:keys [status db] :as rval} (tick db child)]
      (if (has-succeeded? rval)
        rval
        (if (empty? remaining)
          (TickResult. FAILURE db)
          (recur db (first remaining) (rest remaining)))))))

; SEQUENCE
;
; The :sequence node executes all of its children in turn.
;
; If any child returns FAILURE the sequence halts and returns FAILURE
; If all children return SUCCESS the sequence returns SUCCESS

(defmethod ao/required-children :sequence [& _]
  :some)

(defmethod tick :sequence [db [node-type options & children]]
  (reduce (fn [{:keys [db]} child]
            (let [result (tick db child)]
              (if (has-failed? result)
                (reduced result)
                result))
            ) {:db db} children))


; ALWAYS
;
; The :always node takes a single child and ticks it but returns SUCCESS regardless
; of what the child returns

(defmethod ao/required-children :always [& _]
  1)

(defmethod tick :always
  [db [node-type options & [child & _]]]
  (let [{:keys [status db]} (tick db child)]
    (TickResult. SUCCESS db)))

; NEVER
;
; The :never node takes a single child and ticks it but returns FAILURE regardless
; of what the child returns

(defmethod ao/required-children :never [& _]
  1)

(defmethod tick :never
  [db [node-type options & [child & _]]]
  (let [{:keys [status db]} (tick db child)]
    (TickResult. FAILURE db)))

; RANDOMLY
;
; The :randomly node either
; takes one child and executes it if
;
; Parameters
; :p - chance of executing the child from 0.0 (never) to 1.0 (always)
;
; Returns
; SUCCESS if the child is executed and returns SUCCESS
; FAILURE if the child is not executed or the child returns FAILURE

(defmethod ao/required-options :randomly [& _]
  [:p])

(defmethod ao/required-children :randomly [& _]
  #{1 2})

(defmethod tick :randomly [db [node-type options & children]]
  (case (count children)
    1 (if (< (rand) (:p options))
        (tick db (first children))
        (TickResult. FAILURE db))
    2 (if (< (rand) (:p options))
        (tick db (first children))
        (tick db (second children)))))

(defmethod tick :failure [db [node-type options & _]]
  (TickResult. FAILURE db))

(defmethod tick :success [db [node-type options & _]]
  (TickResult. SUCCESS db))
