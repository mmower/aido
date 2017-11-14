(ns aido.core-test
  (:require [expectations :refer :all]
            [aido.core :refer :all]
            [aido.compile :as aidoc]
            [aido.options :refer [options children]]
            [aido.nodes]))

; In the 'nodes' namespaces we define some conditions and actions that can be used for behaviour testing.

(defn tick-status [db tree]
  (:status (run-tick db tree {})))

(defn tick-db [db tree]
  (:db (run-tick db tree {})))

(def t1 (aidoc/compile [:present? {:key :foo}]))
(expect FAILURE (tick-status {} t1))

(def t2 (aidoc/compile [:test? {:key :foo :oper = :val 42}]))
(expect FAILURE (tick-status {:foo 41} t2))
(expect SUCCESS (tick-status {:foo 42} t2))

(def t3 (aidoc/compile [:define! {:key :foo :val 42}]))
(expect SUCCESS (tick-status {} t3))
(expect 42 (:foo (tick-db {} t3)))

(def t4 (aidoc/compile [:sequence
                        [:counter! {:key :n}]
                        [:counter! {:key :n}]]))
(expect SUCCESS (tick-status {} t4))
(expect 2 (:n (tick-db {} t4)))

; SELECTOR & SEQUENCE

(def t5 (aidoc/compile [:selector
                        [:sequence
                         [:present? {:key :foo}]
                         [:define! {:key :baz :val 42}]]
                        [:sequence
                         [:present? {:key :bar}]
                         [:define! {:key :baz :val "What is six times seven?"}]]]))
(expect :failure (tick-status {} t5))
(expect :success (tick-status {:foo true} t5))
(expect 42 (:baz (tick-db {:foo true} t5)))
(expect :success (tick-status {:bar true} t5))
(expect "What is six times seven?" (:baz (tick-db {:bar true} t5)))

; LOOP

(def t6 (aidoc/compile [:loop {:count 3} [:present? {:key :foo}]]))
(expect FAILURE (tick-status {} t6))

(def t7 (aidoc/compile [:loop {:count 4} [:sequence
                                          [:counter! {:key :foo}]
                                          [:less-than? {:key :foo :val 5}]]]))
(expect SUCCESS (tick-status {} t7))
(expect 4 (:foo (tick-db {} t7)))

; LOOP-UNTIL-SUCCESS

(def t8 (aidoc/compile [:loop-until-success {:count 4} [:sequence
                                                        [:counter! {:key :foo}]
                                                        [:greater-than? {:key :foo :val 4}]]]))

(expect FAILURE (tick-status {} t8))

(def t9 (aidoc/compile [:loop-until-success {:count 5} [:sequence
                                                        [:counter! {:key :foo}]
                                                        [:greater-than? {:key :foo :val 4}]]]))

(expect SUCCESS (tick-status {} t9))




; SUCCESS

(let [tree (aidoc/compile [:success])
      {:keys [status db]} (tick {} tree)]
  (expect SUCCESS status)
  (expect {} db))



#_(expect 0.1 (with-redefs [rand (constantly 0.1)]
                (rand)))


; Function options

(def op-fns {:x (constantly 99)})

(def t10 (aidoc/compile [:test? {:key :foo :val [:fn/x] :oper =}] op-fns))

(expect SUCCESS (tick-status {:foo 99} t10))
(expect FAILURE (tick-status {:foo 0} t10))


; Check that walking doesn't stray into options

(def t11 (aidoc/compile [:selector
                         [:sequence [:test? {:key [:foo :bar] :val :baz :oper =}]]
                         [:sequence [:test? {:key :foo :val [:bar :baz] :oper =}]]]))
; we check we don't throw here by defining this

; Check child count verification, should pass with 2

; Requires exactly 2 children. Should not raise an exception
(aidoc/compile [:parent-2
                [:success]
                [:success]])

; Should raise an exception
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-2]))
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-2 [:success]]))
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-2 [:success] [:success] [:success]]))

; Requires at least one child. Should not raise an exception
(aidoc/compile [:parent-+
                [:success]])

; Should raise an exception
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-+]))

; Requries 2 or 3 children. Should not raise an exception
(aidoc/compile [:parent-set [:success] [:success]])
(aidoc/compile [:parent-set [:success] [:success] [:success]])

; Should raise an exception
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-set]))
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-set [:success]]))
(expect clojure.lang.ExceptionInfo (aidoc/compile [:parent-set [:success] [:success] [:success] [:success]]))

