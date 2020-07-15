(ns aido.core-test
  (:require [expectations :refer :all]
            [aido.core :refer :all]
            [aido.tick :refer [tick]]
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

(expect SUCCESS (tick-status {} (aidoc/compile [:always [:failure]])))



(def t10 (aidoc/compile [:failure]))
(expect FAILURE (tick-status {} t10))

(def t11 (aidoc/compile [:success]))
(expect SUCCESS (tick-status {} t11))



; Function options

(def op-fns {:x (constantly 99)
             :+ +})

(def t12 (aidoc/compile [:test? {:key :foo :val [:aido/fn :x] :oper =}] op-fns))

(expect (:fn-opts (meta (second t12))))
(expect SUCCESS (tick-status {:foo 99} t12))
(expect FAILURE (tick-status {:foo 0} t12))

(def t12-2 (aidoc/compile [:sequence [:test? {:key :foo :val [:aido/fn :+ 5 6 2] :oper =}]] op-fns))

(expect (:fn-opts (meta (get-in t12-2 [2 1]))))
(expect SUCCESS (tick-status {:foo 13} t12-2))
(expect FAILURE (tick-status {:foo 12} t12-2))

(expect clojure.lang.ExceptionInfo (aidoc/compile [:test? {:key :foo :val [:aido/fn :unknown] :oper =}] op-fns))

; DB lookup options

(def t13 (aidoc/compile [:equal? {:v1 99 :v2 [:aido/db :data]}]))

(expect (:db-opts (meta (second t13))))
(expect SUCCESS (tick-status {:data 99} t13))

; Test CHOOSE-EACH

;
;
;
(def t14 (aidoc/compile [:choose-each {:repeat true}
                         [:inc! {:key :foo}]
                         [:inc! {:key :bar}]
                         [:inc! {:key :baz}]]))

(loop [db {}
       n  0]
  (let [result (run-tick db t14)
        status (:status result)
        db'    (:db result)]
    (expect SUCCESS status)
    (if (< n 5)
      (recur db' (inc n))
      (do
        (expect 2 (:foo db'))
        (expect 2 (:bar db'))
        (expect 2 (:baz db'))))))

(def t15 (aidoc/compile [:choose-each {:repeat false :debug true}
                         [:inc! {:key :foo}]
                         [:inc! {:key :bar}]
                         [:inc! {:key :baz}]]))

(expect SUCCESS (:status (run-tick {} t15)))

;(loop [db {}
;       n 0]
;  (let [result (run-tick db t15)
;        status (:status result)
;        db'    (:db result)]
;    (expect SUCCESS status)
;    (if (< n 2)
;      (recur db' (inc n))
;      (do
;        (expect 1 (:foo db'))
;        (expect 1 (:bar db'))
;        (expect 1 (:baz db'))
;        (let [result (run-tick db' t15)
;              status (:status result)]
;          (expect FAILURE status))))))

; Check that walking doesn't stray into options

(aidoc/compile [:selector
                [:sequence [:test? {:key [:foo :bar] :val :baz :oper =}]]
                [:sequence [:test? {:key :foo :val [:bar :baz] :oper =}]]])
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

