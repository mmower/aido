# aido

`aido` stands for "AI do" and is a behaviour tree library suitable
for implementing AI game behaviour.

`aido` behaviour trees are implemented as Clojure data structures much in the
style of Hiccup markup. As such they can be implemented in EDN notation.

The structure of a behaviour is

    [:ns/keyword {options}* children*]
    
The core `aido` behaviours do not have a namespace prefix. Domain specific behaviours
are recommended to be namespaced.

It is recommended that behaviours that form conditions should have a name with a `?` suffix
and that behaviours that represent actions have a `!` name suffix.

Examples

    [:sequence
      [:time/after? {:t :teatime}]
      [:beverage/drink! {:beverage :tea}]]
      
    [:loop {:count 5}
      [:map/ping-random-location!]]
      
    [:succeeds]

A behaviour can optionally specify a map of options that will be passed to the
appropriate tick function.

A behaviour can have zero or more child behaviours. For example the `[:selector]`
and `[:sequence]` behaviour expect at least one child.
    
## Return values and flow control

Any behaviour is expected to return one of four values:

    SUCCESS
    FAILURE
    RUNNING
    ERROR
    
Most commonly behaviours are going to return `SUCCESS` or `FAILURE`.

`ERROR` is intended to be a severe form of `FAILURE` indicating a problem processing the behaviour tree.
In the current version of `aido` the two are interchangable and `ERROR` is essentially unused.

`RUNNING` is intended to be an alternative to `SUCCESS` that indicates that a behaviour has neither succeeded
nor failed but is in-progress. The main difference between `SUCCESS` and `RUNNING` is that a sequence will
terminate with the `RUNNING` status if any of its children returns `RUNNING`.

Flow control is primilarily implemented in terms of `:selector` and `:sequence` behaviours.

## Built-ins

### :selector

The SELECTOR node executes its children in turn until one of them succeeds at
which point execution stops and the SELECTOR succeeds. If none of the children
suceeds the SELECTOR fails.

### :sequence

The SEQUENCE node executes its children in turn. If a child fails execution
stops and the SEQUENCE fails. If all of the children succeed the SEQUENCE
succeeds.

### :sequence-p

### :loop

The LOOP node executes a single child a specified number of times. It is
successful if it completes the specified iterations. If the child fails
then the LOOP fails.

### :loop-until-success

The LOOP-UNTIL-SUCCESS node executes a child up to a specified number of times.
If the child succeeds then the LOOP-UNTIL-SUCCESS succeeds. Otherwise, after
the specified number of iterations the LOOP-UNTIL-SUCCESS fails.

### :parallel

The PARALLEL node executes all of its children.

### :randomly

The RANDOMLY node operates in one of two modes depending on whether it has
one or two children.

With one child RANDOMLY evaluates the child if the p test passes and succeeds
or fails if the child succeeds or fails.

With two children RANDOMLY evaluates the first child if the p test passes or
the second child if it fails. RANDOMLY succeeds or fails based on the child
succeeding or failing. 

### :choose

The CHOOSE node takes one or more children and, when evaluated, randomly
selects one child and ticks it. CHOOSE succeeds or fails if the child
succeeds or fails.

### :invert

### :always

The ALWAYS node expects one child that it ticks and then suceeds regardless of
whether the child succeeds.

### :never

The NEVER node expects one child that it ticks and then fails regardless of
whether the child fails.

## Not yet implemented

The following are extensions of the choice idea that provide for non-uniform behaviour. They are given
as separate nodes but, in practice, could be implemented by extending the existing `:choice` node type.

### :weighted-choice

The `:weighted-choice` node randomly selects a child to tick based on some weighting algorithm.

### :choice-without-repetition

The `:choice-without-repetition` node randomly selects a child to tick excluding children that
have been ticked before. This is then a stateful node that uses the working memory to track which
of its children have already been ticked. We may anticipate that a requirement might exist for
some way to reset the memory.

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
      ...)
                
See `aido.core` source for definitions of the built in node-types.

## Usage

    (require '[aido.core :as aido]
              [aido.compile :as ac])
    
    (let [tree (ac/compile ...)]
      (tick {} tree {}))

## License

Copyright 2017 Matthew Mower <self@mattmower.com>
