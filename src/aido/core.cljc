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
  (assert (valid-statuses status) "Invalid status") 
  (TickResult. status db))

(defn tick-success
  "Called when a tick function has succeeded. Takes the new db state to be returned."
  [db]
  (tick-result SUCCESS db))

(defn tick-failure [db]
  "Called when a tick function has failed. Takes the new db state to be returned."
  (tick-result FAILURE db))

(defn tick-error [db error]
  "Called when a tick function has experienced an error. Takes the new db state to be returned and
  also an error that can be retrieved using the key :aido.core/error."
  (tick-result ERROR (assoc db :aido.core/error error)))

(defn tick-running [db id]
  "Called when a tick function is returning success and that it is continuing to run. Takes the new
  db state and the id of the tick function that can be retrieved using the key :aido.core/running-id.
  Note that ids are auto assigned when a behaviour tree is compiled so these cannot be relied upon
  between runs."
  (tick-result RUNNING (assoc db :aido.core/running-id id)))

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

(defmethod ao/options :loop [& _]
  [:count])

(defmethod ao/children :loop [& _]
  1)

(defmethod tick :loop [db [node-type options & [child & _]]]
  (loop [db db
         n  0]
    (if (= n (:count options))
      (tick-success db)
      (let [result (tick db child)]
        (if (has-failed? result)
          result
          (recur (:db result) (inc n)))))))

; LOOP-UNTIL-SUCCESS
;
; The :loop-until-success node executes a single child over and over until it returns
; success or, after, :count repetitions returns a FAILURE
;

(defmethod ao/options :loop-until-success [& _]
  [:count])

(defmethod ao/children :loop-until-success [& _]
  1)

(defmethod tick :loop-until-success [db [node-type options & [child & _]]]
  (loop [db db
         n  0]
    (if (= n (:count options))
      (tick-failure db)
      (let [result (tick db child)]
        (if (has-succeeded? result)
          result
          (recur (:db result) (inc n)))))))

; PARALLEL
;
; The parallel node executes all of its children in parallel
;
; Parameters
; :mode [:success|:failure]
; :test [<n>|:any|:all]
;
; The PARALLEL node executes all of its children.
;
; When the :mode option is :success the PARALLEL node returns SUCCESS if enough children return SUCCESS
; When the :mode option is :failure the PARALLEL node returns SUCCESS if enough children return FAILURE
;
; The :how-many option determines how many are required to succeed or fail.
;
pos?
(defmethod ao/options :parallel [& _]
  [:mode :test])

(defmethod ao/children :parallel [& _]
  :some)

(defmethod tick :parallel [db [node-type {:keys [mode how-many]} & children]]
  (let [c            (count children)
        {:keys [success failure db]} (reduce (fn [{:keys [success failure db]} child]
                                               (let [result (tick db child)]
                                                 {:success (if (has-succeeded? result) (inc success) success)
                                                  :failure (if (has-failed? result) (inc failure) failure)
                                                  :db      (:db result)})
                                               ) {:success 0 :failure 0 :db db} children)
        compare-with (if (= mode :success) success failure)
        pass-fn      (if (= mode :success) tick-success tick-failure)
        fail-fn      (if (= mode :success) tick-failure tick-success)]
    (if (>= compare-with how-many)
      (pass-fn db)
      (fail-fn db))))

; SELECTOR
;
; The :selector node executes its children sequentially stopping after the first one that
; returns SUCCESS or RUNNING
;
; If no child returns SUCCESS the :select returns FAILURE

(defmethod ao/children :selector [& _]
  :some)

(defmethod tick :selector [db [node-type options & children]]
  (loop [db        db
         child     (first children)
         remaining (rest children)]
    (let [{:keys [status db] :as rval} (tick db child)]
      (if (has-succeeded? rval)
        rval
        (if (empty? remaining)
          (tick-failure db)
          (recur db (first remaining) (rest remaining)))))))

; SEQUENCE
;
; The :sequence node executes all of its children in turn.
;
; If any child returns FAILURE the sequence halts and returns FAILURE
; If all children return SUCCESS the sequence returns SUCCESS

(defmethod ao/children :sequence [& _]
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

(defmethod ao/children :always [& _]
  1)

(defmethod tick :always
  [db [node-type options & [child & _]]]
  (let [{:keys [status db]} (tick db child)]
    (tick-success db)))

; NEVER
;
; The :never node takes a single child and ticks it but returns FAILURE regardless
; of what the child returns

(defmethod ao/children :never [& _]
  1)

(defmethod tick :never
  [db [node-type options & [child & _]]]
  (let [{:keys [status db]} (tick db child)]
    (tick-failure db)))

; INVERT
;
; The :invert node takes a single child and ticks it. When the child returns SUCCESS
; :invert returns FAILURE and vice verca.

(defmethod ao/children :invert [& _]
  1)

(defmethod tick :invert
  [db [node-type options & [child & _]]]
  (let [{:keys [status db]} (tick db child)
        inverted-status (condp = status
                          :SUCCESS :FAILURE
                          :FAILURE :SUCCESS)]
    (tick-result inverted-status db)))

; RANDOMLY
;
; The :randomly node takes one or two children.
;
; In the case of one child it uses the probability parameter 'p' to determine whether
; to run the child. In the case of two children it uses it to determine whether to run
; the first or second child.
;
; Parameters
; :p - chance of executing the first child from 0.0 (never) to 1.0 (always)
;
; Returns
; SUCCESS if the child is executed and returns SUCCESS
; FAILURE if the child is not executed or the child returns FAILURE

(defmethod ao/options :randomly [& _]
  [:p])

(defmethod ao/children :randomly [& _]
  #{1 2})

(defmethod tick :randomly [db [node-type options & children]]
  (case (count children)
    1 (if (< (rand) (:p options))
        (tick db (first children))
        (tick-failure db))
    2 (if (< (rand) (:p options))
        (tick db (first children))
        (tick db (second children)))))

; CHOOSE
;
; The :choose node takes a number of children and chooses one at random
; ticks it and returns the result.
;
; Returns
; SUCCESS if the child returns SUCCESS
; FAILURE if the child returns FAILURE

(defmethod ao/children :choose [& _]
  :some)

(defmethod tick :choose [db [node-type options & children]]
  (tick db (rand-nth children)))




(defmethod tick :failure [db [node-type options & _]]
  (tick-failure db))

(defmethod tick :success [db [node-type options & _]]
  (tick-success db))
