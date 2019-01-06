# aido

[![Clojars Project](https://img.shields.io/clojars/v/sandbags/aido.svg)](https://clojars.org/sandbags/aido)

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

Those heart of the control flow are behaviours such as `sequence` and `selector`
 and the notion that any element of the behaviour tree either results in
 **success** or **failure**. In particular, a sequence considers each of its
 children until one of them fails while a selector selects among its children
 until one succeeds. From such a simple premise quite complex behaviours can
 emerge.

## Specifying a tree

`aido` behaviour trees are implemented as Clojure data structures much in the
style of Hiccup markup. As such they can be implemented in EDN notation.

The structure of a behaviour is

    [:ns/keyword {options}* children*]
    
The core `aido` behaviours do not have a namespace prefix. Domain specific
behaviours are recommended to be namespaced.

The author has developed a convention that behaviours that form conditions should
have a `?` suffix, e.g. `:time/after?` while behaviours that represent actions
with side-effects should have a `!` suffix, e.g. `:beverage/drink!`.

A behaviour can have zero or more child behaviours. For example the `[:selector]`
and `[:sequence]` behaviour expect at least one child although, in practice, both
only make sense with multiple children.

## Example

    [:selector
      [:sequence
        [:time/after? {:t :teatime}]
        [:beverage/drink! {:beverage :tea}]
      [:actor/say! {:message "Oh, how I wish it was time for tea!"}]
      
In this example the `:selector` runs and ticks its first child, the `:sequence`.
The `:sequence` ticks each of its children in turn. If `:time/after?` fails then
the `:sequence` fails and the `:selector` goes on to tick `:actor/say!`. On the
other hand if it succeeds then the `:beverage/drink!` child is ticked and, we
assume, succeeds leading to the `:sequence` succeeding and the `:selector`
succeeding without ticking the `:actor/say!` child.
      
## Options

In the example above we see that `:time/after?`, `:beverage/drink!`, and `:actor/say!`
all specify a map of options. These are assumed to be understood by the implementation
of the behaviour in question. The `:selector` and `:sequence` do not have options although
some of the built-in behaviours, e.g. `loop` do. In the case of `:loop` it has a `count`
 option to specify how many times the loop should iterate.

Sometimes it is advantageous to be able to specify options that are dynamic. aido offers
two approaches:

1. Specify a function value, the syntax for which is:

    `[:aido/fn function-id arg1 ... argN]`
    
    e.g.

    `[:loop {:count [:aido/fn rand 5]} ...]`

The `function-id` should correspond to a function registered with the compiler (see below).

These functions are stand-alone and executed each time the beheaviour is ticked with
the return value of the function being passed into the options maps.

2. Specify a database key-path

     `[:aido/db path1 ... pathN]`
     
     e.g.
     
     `[:loop {:count [:aido/db :settings :loop-count]]}]`
     
When the behaviour is ticked the appropriate value in the database will be passed in instead,
this example would be conceptually equivalent to:

      [:loop {:count (get-in db [:settings :loop-count])}]
              
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

Copyright 2017-2018 Matthew Mower <self@mattmower.com>
