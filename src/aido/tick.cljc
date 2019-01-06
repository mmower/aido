(ns aido.tick)

(defn tick-node-type
  "Given a node [node-type & _] return the node-type for multi-method discrimination"
  [db [node-type & _]] node-type)

; The tick method has the signature
;
; [db node]
;
; where node can be destructured into
;
; [node-type options & children]
;
; db is the persistent database that conditions and actions can query about the domain
; node-type is the behaviour node type keyword (e.g. :selector)
; options is a map of options. Every node is guaranteed to have an :id value
; children is the zero or more child nodes of this node
;

(defmulti tick
          "The tick function sends the tick to a node of different types."
          tick-node-type)
