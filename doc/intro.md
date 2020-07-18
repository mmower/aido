# Introduction to AIDO

`aido` stands for "AI do" and is a behaviour tree library suitable
for implementing AI game behaviour.

`aido` behaviour trees are implemented as Clojure data structures much in the
style of hiccup markup, i.e. in EDN notation.

The structure of a behaviour is

    [:ns/keyword {options}? children*]

for example

    [:loop {:count 5} [:map/ping-random-location]] 

or

    [:succeeds]

A behaviour can have zero or more child behaviours. For example the `[:selector]`
and `[:sequence]` behaviour expect at least one child while the `[:loop]` behaviour
expects exactly one child.

A behaviour can optionally specify a map of options that will be passed to the
appropriate tick function. It is possible to specify dynamic values for options
however, because functions are not allowed in EDN, functions are, instead, specified
using the following syntax 

Where a regular function would be written

    (random 5)
    
An AIDO function call is written:

    [:fn/random 5]
    
For example:

    [:loop {:count [:fn/random 5]}
      ...]
   
The use of the `:fn` namespace helps distinguish such arguments structures from
regular vectors with keywords. The definition of the `random` function would be
provided to the call to `compile` at runtime. E.g.

    {:random (fn [n] (inc (rand n)))}

## Core Concepts

Behaviours are specified as a tree of nodes that can be "ticked". Ticking a node
evaluates it and, depending on the behaviour of the specific node, any children.

When a node is ticked it generally returns a status of either SUCCESS or FAILURE. Under
certain circumstances is may also return one of RUNNING or ERROR but these are special
values that will be covered later.

The SUCCESS or FAILURE of child nodes is how decisions flow through the tree. For
example. a SELECTOR node looks for the first child returning SUCCESS to know when
to stop, and so on.

Nodes can be passed options that customise how they behave. For example the
`[:loop]` node should be passed a `:count` parameter specifying how many times it
should loop.

All nodes have an `:id` option that uniquely identifies them. If no `:id` is
specified an auto-assigned `:id` is provided.

Before using a tree it must be compiled. This verifies that required options
are present (or defaults are supplied) and that each node has the correct amount
of children.
 
## Running

In descriptions of Behaviour Trees the RUNNING status represents a process that has
begun but cannot be completed in the current tick. When a node returns the RUNNING
status evaluation of the tree should be halted with the entire tree returning the
RUNNING status. At the next tick, processing of the tree should begin at the node
that originally returned RUNNING.

This status & behaviour is not yet supported and returning RUNNING is equivalent to
returning SUCCESS.

## Error

The ERROR status is intended to represent a serious problem that prevents normal
processing of the behaviour tree. None of the built-in nodes returns ERROR and, at
present, it is equivalent to returning FAILURE. 


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

### CHOOSE-EACH

The `[:choose-each]` node is designed to select from among its children at
random but without replacement. Each time it is ticked a child is selected
and ticked with `[:choose-each]` returning SUCCESS or FAILURE as per the child. In
subsequent ticks that child is no longer eligible to be selected.

Once all children have been ticked, further ticks of the `[:choose-each]` will
return FAILURE. Alternatively if the `repeat` option is specified as `true` then
when all children have been ticked the node is replenished with all its children
again. In this case the order of children will be again random and not the same
order as previous ticks.  

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