# aido

`aido` stands for "AI do" and is a behaviour tree library suitable
for implementing AI game behaviour.

`aido` is based on reading about a number of implementations of behaviour trees
and modular behaviour trees.

`aido` behaviour trees are implemented as Clojure data structures much in the
style of hiccup markup. As such they can be implemented in EDN notation.

Because functions are not allowed in EDN the use of inline functions is not
permitted however functions can be supplied for parameters using a set of
named functions that are registered, in advance, with the `ticker`.

To distinguish from regular keyword arguments functions are specified as
keywords in the `fn` namespace, e.g.

    [:loop {:count [:fn/random 5]}
      ...]

{:fn/random (fn [n] (inc (rand n)))}


## Usage

    (require '[aido.core :as aido])
    
    [:root]

## License

Copyright 2017 Matthew Mower <self@mattmower.com>

