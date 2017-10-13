# Introduction to AIDO

`aido` stands for "AI do" and is a behaviour tree library suitable
for implementing AI game behaviour.

`aido` is based on reading about a number of implementations of behaviour trees
and modular behaviour trees.

`aido` behaviour trees are implemented as Clojure data structures much in the
style of hiccup markup. As such they can be implemented in EDN notation.

The structure of a behaviour is

    [:ns/keyword {options}* children*]

for example

    [:loop {:count 5} [:map/ping-random-location]] 

or

    [:succeeds]

A behaviour can optionally specify a map of options that will be passed to the
appropriate tick function. If no map of options is specified a default map
will be provided.

A behaviour can have zero or more child behaviours. For example the `[:selector]`
and `[:sequence]` behaviour expect at least one child.

Because functions are not allowed in EDN the use of inline functions is not
permitted however functions can be supplied for parameters using a set of
named functions that are registered, in advance, with the compiler`.

To distinguish from regular keyword arguments functions are specified as
keywords in the `fn` namespace, e.g.

    [:loop {:count [:fn/random 5]}
      ...]
      
Where the `random` function is specified to the compiler as, for example,

    {:random (fn [n] (inc (rand n)))}

## Core Concepts

Behaviours are specified as a tree of nodes that can be "ticked". Ticking a node
evaluates it and, depending on the behaviour of the specific node, any children.

When a node is ticked it returns a status that is either SUCCESS or FAILURE (in
fact it may also return RETURNING or ERROR but we'll cover those later).

The SUCCESS or FAILURE of child nodes is how decisions flow through the tree. For
example a SELECTOR node looks for the first child returning SUCCESS to know when
to stop, and so on.

Nodes can be passed options that customise how they behave. For example the
LOOP node should be passed a `:count` parameter specifying how many times it
should loop.

All nodes have an `:id` option that uniquely identifies them. If no `:id` is
specified an auto-assigned `:id` is provided.

Before using a tree it must be compiled. This verifies that required options
are present (or defaults are supplied) and that each node has the correct amount
of children.
 




## Running

We do not yet support the RUNNING status and treat it as success. A node
that wants to return RUNNING could store and look for its `:id` in the database.

However it seems like when a node returns RUNNING processing of the tree should
halt and, at the next tick, restart from this node. It's not yet clear how to
implement this behaviour.

## Built-ins

### SELECTOR

The SELECTOR node executes its children in turn until one of them succeeds at
which point execution stops and the SELECTOR succeeds. If none of the children
suceeds the SELECTOR fails.

### SEQUENCE

The SEQUENCE node executes its children in turn. If a child fails execution
stops and the SEQUENCE fails. If all of the children succeed the SEQUENCE
succeeds.

### LOOP

The LOOP node executes a single child a specified number of times. It is
successful if it completes the specified iterations. If the child fails
then the LOOP fails.

### LOOP-UNTIL-SUCCESS

The LOOP-UNTIL-SUCCESS node executes a child up to a specified number of times.
If the child succeeds then the LOOP-UNTIL-SUCCESS succeeds. Otherwise, after
the specified number of iterations the LOOP-UNTIL-SUCCESS fails.

### PARALLEL

The PARALLEL node executes all of its children.

### RANDOMLY

The RANDOMLY node operates in one of two modes depending on whether it has
one or two children.

With one child RANDOMLY evaluates the child if the p test passes and succeeds
or fails if the child succeeds or fails.

With two children RANDOMLY evaluates the first child if the p test passes or
the second child if it fails. RANDOMLY succeeds or fails based on the child
succeeding or failing. 

### CHOOSE

The CHOOSE node takes one or more children and, when evaluated, randomly
selects one child and ticks it. CHOOSE succeeds or fails if the child
succeeds or fails.

### ALWAYS

The ALWAYS node expects one child that it ticks and then suceeds regardless of
whether the child succeeds.

### NEVER

The NEVER node expects one child that it ticks and then fails regardless of
whether the child fails.

### INVERT

The INVERT node expects one child that it ticks. It then returns the opposite
status of it's child, that is if the child fails it succeeds and vice verca.

## Extending AIDO

Behaviours in AIDO are defined by creating new tick node types. A node type is defined by implementing 3
multimethods: `tick`, `options`, and `children`. Implementing `tick` is required, `options` and `children`
offer default behaviour ('no options' and 'no children' respectively).

Let's define a new node type that is a variation on a selector but has a probability check to determine
 whether to try and tick child nodes. If the probability check fails it moves on to the next child. It
 succeeds if a child succeeds, otherwise fails.
 
It would look something like:

    [:selector-p {:p 0.15} [child1] [child2] [child3]]
    
Here is how the node type would be defined:
    
    (defmethod options :selector-p [& _] [:p])
    
    (defmethod children :selector-p [& _] :some)
    
    (defmethod tick :selector-p [db [node-type {:keys [p]} & children]
      (loop [db db
             child (first children)
             remaining (rest children)]
        (if (< (rand) p)
        (let [{:keys [db status] :as rval} (if (< (rand) p)
                                             (tick db child)
                                             {:db db :status FAILURE}]
          (if (has-succeeded? rval)
            rval
            (if (empty? remaining)
              (tick-failure db)
              (recur db (first remaining) (rest remaining))))))))
                
See `aido.core` source for definitions of the built in node-types.


TODO: write [great documentation](http://jacobian.org/writing/what-to-write/)






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


;

;
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




Because functions are not allowed in EDN the use of inline functions is not
permitted however functions can be supplied for parameters using a set of
named functions that are registered, in advance, with the compiler`.

To distinguish from regular keyword arguments functions are specified as
keywords in the `fn` namespace, e.g.

    [:loop {:count [:fn/random 5]}
      ...]
      
Where the `random` function is specified to the compiler as, for example,

    {:random (fn [n] (inc (rand n)))}