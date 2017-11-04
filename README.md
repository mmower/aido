# aido -- v0.3.0

## Introduction

`aido` stands for "AI do" and is a behaviour tree library suitable
for implementing AI behaviours into games or applications. It could be
used to model the behaviour of a game character or for a chabot to
control the different responses it might employ.

I won't introduce behaviour trees here (see
[Google](https://www.google.co.uk/search?safe=off&dcr=0&source=hp&q=introduction+to+behaviour+trees&oq=introduction+to+behaviour+trees))
beyond the basics. A behaviour tree is a tree-like data structure that
specifies conditions we are interested in, actions that might be taken
in responsento a given set of conditions, and some control flow mechanisms 
hat govern how the tree "makes decisions".

Those heart of the control flow is the **sequence** and the **selector** and
the notion that any element of the behaviour tree either results in **success**
or **failure**. In particular, a sequence considers each of its children until
one of them fails while a selector selects among its children until one
succeeds. From such a simple premise quite complex behaviours can emerge.

## Specifying a tree

`aido` behaviour trees are implemented as Clojure data structures much in the
style of Hiccup markup. As such they can be implemented in EDN notation.

The structure of a behaviour is

    [:ns/keyword {options}* children*]
    
The core `aido` behaviours do not have a namespace prefix. Domain specific
behaviours are recommended to be namespaced.

It is recommended that behaviours that form conditions should have a name
with a `?` suffix and that behaviours that represent actions have a `!` name
suffix.

Examples

    [:sequence
      [:time/after? {:t :teatime}]
      [:beverage/drink! {:beverage :tea}]]
      
    [:loop {:count 5}
      [:map/ping-random-location!]]
      
    [:succeeds]
    
A behaviour can have zero or more child behaviours. For example the `[:selector]`
and `[:sequence]` behaviour expect at least one child.

## Options

A behaviour can optionally specify a map of options that will be passed to the
appropriate tick function. For example the `loop` behaviour uses an option `count` to
specify how many times the loop should iterate.

Because functions are not permitted in EDN there is a way to specify functions that
 should be used to dynamically generate option values.
 
To implement a fully dynamic option that will be re-evaluated each time the behaviour
receives a tick specify the option like this:

    [:loop {:count [:fn/rand 5]} ...]
    
Any vector with a first element being a keyword in the `fn` namespace will be treated
as a dynamic option and will call the named function (in this case `rand`) as long
as an appropriate function has been registered with the compiler (see below).

Alternatively:

    [:loop {:count [:ifn/rand 5]} ...]
   
When a function is specified in the `ifn` namespace the option value will be calculated
when the tree is being compiled. Therefore it will be set dynamically once, at compile
time, but remain the same on each tick of the tree.
    
Functions are specified by passing an optional map to `compile` for example to satisfy the
trees above you might use:

    (aido.compile/compile tree {:rand rand-int})
    
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

## Usage

In basic usage you must compile a behaviour tree using `aido.compile/compile` and then to run it
use the `aido.core/run-tick` function. It is not recommend to call the `tick` function directly.

    (ns 'aido.example
      (:require [aido.core :as ai]
                [aido.compile :as ac]))
    
    (let [tree (ac/compile [:selector
                             [:sequence
                               [:even? {:fn/coin}]
                               [:heads!]
                             [:tails!]]]) {:coin #(< (rand) 0.5)})
          db*  {:foo :bar}]
      (let [{:keys [db status]} (ai/run-tick db* tree)]
        (if (= ai/SUCCESS status)
          ; extract from or use db
          ; otherwise...))

## Built-ins

### :selector

The `:selector` node executes its children in turn until one of them succeeds at
which point execution stops and the `:selector` succeeds. If none of the children
suceeds the `:selector` fails.

### :sequence

The `:sequence` node executes its children in turn. If a child fails execution
stops and the `:sequence` fails. If all of the children succeed the `:sequence`
succeeds.

### :selector-p

### :loop

The `:loop` node executes a single child a specified number of times. It is
successful if it completes the specified iterations. If the child fails
then the `:loop` fails.

### :loop-until-success

The `:loop-until-success` node executes a child up to a specified number of times.
If the child succeeds then the `:loop-until-success` succeeds. Otherwise, after
the specified number of iterations the `:loop-until-success` fails.

### :parallel

The `:parallel` node executes all of its children.

### :randomly

The `:randomly` node operates in one of two modes depending on whether it has
one or two children.

With one child `:randomly` evaluates the child if the p test passes and succeeds
or fails if the child succeeds or fails. If the `p` test fails `:randomly` fails.

With two children `:randomly` evaluates the first child if the p test passes or
the second child if it fails. `:randomly` succeeds or fails based on the selected
child succeeding or failing. 

### :choose

The `:choose` node takes one or more children and, when evaluated, randomly
selects one child and ticks it. `:choose` succeeds or fails if the child
succeeds or fails.

### :invert

### :always

The `:always` node expects one child that it ticks and then suceeds regardless of
whether the child succeeds.

### :never

The `:never` node expects one child that it ticks and then fails regardless of
whether the child fails.

## Not yet implemented

The following are extensions of the choice idea that provide for non-uniform behaviour. They are given
as separate nodes but, in practice, could be implemented by extending the existing `:choice` node type
with additional options.

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
