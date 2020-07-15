(ns aido.core
  (:require [clojure.string :as str]
            [aido.error :refer [throw-error]]
            [aido.options :as ao]
            [aido.compile :as ac]
            [aido.tick :as at]))

(def STS-KEY ::working-memory)
(def LTS-KEY ::node-memory)

(defn set-memory
  "Store a set of assignments in a fixed location, namespaced to aido."
  [db length key mem]
  (assoc-in db [length key] mem))

(defn get-memory
  "Get one of the namespaced working-memory assignments."
  ([db length key not-found]
   (get-in db [length key] not-found))
  ([db length key]
   (get-memory db length key nil)))

(defn set-working-memory
  "Sets a key-value pair in working memory. This memory is reset at the end of the tick."
  [db key mem]
  (set-memory db STS-KEY key mem))

(defn get-working-memory
  ([db key not-found]
   (get-memory db STS-KEY key not-found))
  ([db key]
   (get-memory db STS-KEY key)))

(defn set-node-memory
  "Sets memory that is associated with a specific node. This memory persists in the database between tree ticks."
  [db node-id mem]
  (set-memory db LTS-KEY node-id mem))

(defn get-node-memory
  ([db node-id not-found]
   (get-memory db LTS-KEY node-id not-found))
  ([db node-id]
   (get-memory db LTS-KEY node-id)))

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

(defn in-progress?
  [result]
  (= (:status result) RUNNING))

(def has-failed? (complement has-succeeded?))




(defn tick-child [db child]
  "The tick function should not be called directly because the child may contain unrealised options.
  Calling tick-child ensures options are realised before the child tick function is called."
  (let [child* (ac/realise-options db child)
        rv     (at/tick db child*)]
    (assert (and (contains? rv :status)
                 (contains? rv :db))
            (str "Invalid return value [" rv "] from ticking child: " child))
    rv))

(defn run-tick
  "The run-tick function is a top-level function for sending a tick to a node tree. Its purpose is to
  temporarily assocation a map of working definitions for use during that tick into a well-known part of
  the working memory database and then remove them once the tick is complete. The map is associated
  under the key `:aido/wmem`."
  ([db tree]
   (run-tick db tree {}))
  ([db tree local-defs]
   (let [{:keys [status db]} (tick-child (assoc db STS-KEY local-defs) tree)]
     (tick-result status (dissoc db STS-KEY)))))


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

(defmethod at/tick :loop [db [node-type {:keys [count]} & [child & _]]]
  (loop [db db
         n  0]
    (if (= count n)
      (tick-success db)
      (let [result (tick-child db child)]
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

(defmethod at/tick :loop-until-success [db [node-type {:keys [count]} & [child & _]]]
  (loop [db db
         n  0]
    (if (= n count)
      (tick-failure db)
      (let [result (tick-child db child)]
        (if (has-succeeded? result)
          result
          (recur (:db result) (inc n)))))))

; PARALLEL
;
; The parallel node executes all of its children in parallel
;
; Parameters
; :mode [:success|:failure]
; :how-many <n> (1…)
;
; The PARALLEL node executes all of its children.
;
; When the :mode option is :success the PARALLEL node returns SUCCESS if enough children return SUCCESS
; When the :mode option is :failure the PARALLEL node returns SUCCESS if enough children return FAILURE
;
; The :how-many option determines how many are required to succeed or fail.
;

(defmethod ao/options :parallel [& _]
  [:mode :how-many])

(defmethod ao/children :parallel [& _]
  :some)

(defmethod at/tick :parallel [db [node-type {:keys [mode how-many]} & children]]
  (let [c            (count children)
        {:keys [success failure db]} (reduce (fn [{:keys [success failure db]} child]
                                               (let [result (tick-child db child)]
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

(defmethod at/tick :selector [db [node-type options & children]]
  (loop [db        db
         child     (first children)
         remaining (rest children)]
    (let [{:keys [status db] :as rval} (tick-child db child)]
      (if (has-succeeded? rval)
        rval
        (if (empty? remaining)
          (tick-failure db)
          (recur db (first remaining) (rest remaining)))))))

; SELECTOR-P
;
; Like the :selector, the :selector-p behaviour ticks its children sequentially stopping after the first one that
; returns SUCCESS or RUNNING. However before ticking any child it first does a probability test to determine whether
; to attempt to tick that child. If the test fails, it moves on to the next child.
;
; Parameters:
; :p [0.0 .. 1.0] probability of ticking any given child node
;

(defmethod ao/children :selector-p [& _]
  :some)

(defmethod ao/options :selector-p [& _]
  [:p])

(defmethod at/tick :selector-p
  [db [node-type {:keys [p] :as options} & children]]
  (loop [db        db
         child     (first children)
         remaining (rest children)]
    (if (< (rand) p)
      (let [{:keys [status db] :as rval} (tick-child db child)]
        (if (has-succeeded? rval)
          rval
          (if (empty? remaining)
            (tick-failure db)
            (recur db (first remaining) (rest remaining)))))
      (if (empty? remaining)
        (tick-failure db)
        (recur db (first remaining) (rest remaining))))))

; SEQUENCE
;
; The :sequence node executes all of its children in turn.
;
; If a child returns FAILURE the sequence halts and returns FAILURE
; If a child returns RUNNING the sequence halts and returns RUNNING
; If all children return SUCCESS the sequence returns SUCCESS

(defmethod ao/children :sequence [& _]
  :some)

(defmethod at/tick :sequence [db [node-type options & children]]
  (reduce (fn [{:keys [db]} child]
            (let [result (tick-child db child)]
              (if (or (in-progress? result) (has-failed? result))
                (reduced result)
                result))
            ) {:db db} children))

; ALWAYS
;
; The :always node takes a single child and ticks it but returns SUCCESS regardless
; of what the child returns

(defmethod ao/children :always [& _]
  1)

(defmethod at/tick :always
  [db [node-type options child]]
  (let [{:keys [status db]} (tick-child db child)]
    (tick-success db)))

; NEVER
;
; The :never node takes a single child and ticks it but returns FAILURE regardless
; of what the child returns

(defmethod ao/children :never [& _]
  1)

(defmethod at/tick :never
  [db [node-type options child]]
  (let [{:keys [status db]} (tick-child db child)]
    (tick-failure db)))

; INVERT
;
; The :invert node takes a single child and ticks it. When the child returns SUCCESS
; :invert returns FAILURE and vice verca.

(defmethod ao/children :invert [& _]
  1)

(defmethod at/tick :invert
  [db [node-type options child]]
  (let [{:keys [status db]} (tick-child db child)
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

(defmethod at/tick :randomly [db [node-type options & children]]
  (case (count children)
    1 (if (< (rand) (:p options))
        (tick-child db (first children))
        (tick-failure db))
    2 (if (< (rand) (:p options))
        (tick-child db (first children))
        (tick-child db (second children)))))

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

(defmethod at/tick :choose [db [node-type options & children]]
  (tick-child db (rand-nth children)))

; FAILURE
;
; The :failure node always fails immediately

(defmethod at/tick :failure [db [node-type options & _]]
  (tick-failure db))

; SUCCESS
;
; The :success node always succeeds immediately

(defmethod at/tick :success [db [node-type options & _]]
  (tick-success db))

; CHOOSE-EACH
; Stateful
;
; The :choose-each node takes a number of children which are put into a pool.
; Each time the node is ticked it removes one child from the pool, ticks it,
; and returns the result.
;
; In this way every child will eventually be ticked, but in a random order.
;
; Parameters
; :repeat - if true, when the pool is emptied the original children are re-added
; and a new random choice is made. If false, when the pool is empty it is not
; refilled and the node will return FAILURE in future ticks
;
; Returns
; SUCCESS if it ticks a child and the child returns SUCCESS
; FAILURE if it ticks a child and the child returns FAILURE
; FAILURE if there is no child to tick
;

(defmethod ao/options :choose-each [& _]
  [:repeat])

(defmethod ao/children :choose-each [& _]
  :some)

(defmethod at/tick :choose-each [db [node-type options & children]]
  (let [node-id     (:id options)
        repeat?     (get options :repeat false)
        mem         (get-node-memory db node-id {})
        pool        (:pool mem)
        first-fill? (get mem :first-fill? true)]
    (if (and (not repeat?) (empty? pool) (and (not first-fill?)))
      (tick-failure db)
      (let [pool'  (if (empty? pool) children pool)
            choice (rand-nth pool')
            pool'' (remove #(= choice %) pool')
            mem'   (-> mem
                       (assoc :first-fill? false)
                       (assoc :pool pool''))
            result (tick-child db choice)
            db'    (:db result)
            status (:status result)
            db''   (set-node-memory db' node-id mem')]
        (tick-result status db'')))))
