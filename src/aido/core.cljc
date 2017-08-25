(ns aido.core
  (:require [clojure.string :as str]
            [aido.options :as ao]
            [aido.compile :as ac]))

(def SUCCESS :success)
(def FAILURE :failure)
(def RUNNING :running)
(def ERROR :error)

(def ... nil)

(defrecord Result [status db])

(defn tick-result [status db]
  "Wraps a status (SUCCESS, FAILURE, etc..) and a db state to return them together."
  (Result. status db))







(defn has-not-failed? [result]
  (let [status (:status result)]
    (or (= status SUCCESS)
        (= status RUNNING))))

(def has-failed? (complement has-not-failed?))



(defn tick-node-type [db [node & _]]
  (println "decode: " node)
  node)


(defmulti tick
          "The tick function sends the tick to a node of different types."
          {:arglists '([db options? node])}
          tick-node-type)


; node types


; LOOP
;
; The loop node allows to execute one child ... a multiple number of times, specified by the
; {:count n} parameter or until the child returns FAILURE.
;
; If no count is specified looping continues indefinitely
; The loop returns SUCCESS if the specified number of iterations succeed
; The loop returns FAILURE if the child fails on any iteration

[:loop {:count 3}
 ...]

(defmethod ao/required-options :loop [& _]
  [:count])

(defmethod tick :loop [db [node-type & args]]
  (println "db:" db)
  (println "node-type:" node-type)
  (println "args:" args)
  (let [[{:keys [count]} [child & _]] (parse-options args :required [:count])]
    (println "count:" count)
    (println "child:" child)
    (loop [db db
           n  0]
      (println "n:" n)
      (if (= n count)
        (tick-result FAILURE db)
        (let [result (tick db child)]
          (println "result:" result)
          (if (has-failed? result)
            (do
              (println "HAS FAILED")
              result)
            (recur (:db result) (inc n))))))))

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

[:parallel {:success-mode :any :failure-mode :any}
 ...]

; SELECT
;
; The :select node executes its children sequentially stopping after the first one that
; returns SUCCESS or RUNNING
;
; If no child returns SUCCESS the :select returns FAILURE

[:selector
 ...]

(defmethod tick :selector [db _ & args]
  (let [[options children] (parse-options args)]
    (loop [db        db
           child     (first children)
           remaining (rest children)]
      (let [{:keys [status db] :as rval} (tick db child)]
        (if (has-not-failed? rval)
          rval
          (if (empty? remaining)
            (tick-result FAILURE db)
            (recur db (first remaining) (rest remaining))))))))

; SEQUENCE
;
; The :sequence node executes all of its children in turn.
;
; If any child returns FAILURE the sequence halts and returns FAILURE
; If all children return SUCCESS the sequence returns SUCCESS


; ALWAYS
;
; The :always node can have a single child and returns a fixed value regardless of whether the child
; returns SUCCESS or FAILURE
;
; :returns SUCCESS | FAILURE

[:always {:returning FAILURE}
 ...]

(defmethod ao/required-options :always [& _]
  [:returning])

(defmethod tick :always [db [op & args]]
  (let [[{:keys [returning]} children] (parse-options args :required [:returning])]
    (if (= 1 (count children))
      (tick db (first children))
      (throw (Exception. "Node :always must have exactly one child!")))
    returning))

; TIMEOUT
;
; The :timeout node returns FAILURE after a certain amount of time has passed
;
; :duration <ticks>
;

[:timeout {:duration 5}]

; WAIT
;
; The :wait node returns SUCCESS after a certain amount of time has passed
;
; :duration <ticks>

[:wait]

[:wait-until]

[:if-time]


; SPEECH
;
; The :speech node will have the current agent speak
;
; :say [:template (params...)]
;
; Returns
; SUCCESS

[:speech {:say [:hello]}]

; MOVE
;
; The :move node moves the current agent to another location
;
; Parameters
; :to - ID of location to move to
;
; Returns
; SUCCESS if the agent has moved
; FAILURE if the agent is unable to move

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

[:random {:p 0.8}
 ...]

(defmethod ao/required-options :randomly [& _]
  [:p])

(defmethod tick :randomly [db [op & args]]
  (let [[{:keys [p]} children] (parse-options args :required [:p])]
    (case (count children)
      1 (if (< (rand) p)
          (tick db (first children))
          (tick-result FAILURE db))
      2 (if (< (rand) p)
          (tick db (first children))
          (tick db (second children))))))

; IF
;
; The :if node checks a condition. If the condition is true it runs the first child
; otherwise it runs the second child if specified.
;
; Parameters
; :condition <exp>
;
; Returns
; SUCCESS if either child is executed and returns SUCCESS
; FAILURE if either child is executed and returns FAILURE or if the condition fails and no second child is specified

[:if {:condition ...}
 ...
 ...]

; WAIT-UNTIL
;
; The :wait-until node executes until the time condition is satisifed
;
; Parameters
; :
;
; Returns
; SUCCESS
; FAILURE -





(defmethod tick :nop [db _ & args]
  (println "NOP")
  (tick-result SUCCESS db))