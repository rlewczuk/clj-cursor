# clj-cursor

This library implements cursor on Clojure data structures. It is based on David Nolen concept
implemented in the [om library](https://github.com/swannodette/om). Cursor allows storing whole
application state in a signle structure, yet still be able to swap underlying application state
without much hassle (think online reconfiguration etc.).

This is especially important for async processes/threads/loops/closures that receive some
initial configuration and then keep it forever. When using raw immutable data structures
programmer needs either implement online reconfiguration in each and every
process instance, pass atomic reference to whole application state or scatter atoms over a
data structure representing application state. Each of these approaches has its own shortcomings:
either some boilerplate code needs to be implemented, application components see state of other
components (and need additional information where their own state is located) or application
state isn't really immutable and data under references to some parts of app state can change
in uncontrolled way. Cursor is an attempt to retain


## Usage

As project is still not available in clojars repository, it needs to be compiled and installed
into local repository:

```bash
git clone https://github.com/rlewczuk/clj-cursor clj-cursor
cd clj-cursor
lein install
mvn install
```

Add the following dependency to your `project.clj` file:

```clj
[clj-cursor "0.0.1"]
```

First import some functions from `clj-cursor` library:

```clj
(require '[clj-cursor.core :refer [cursor, transact!, update!]])
```

Let's declare a cursor in a REPL:

```clj
(def cur (cursor {:a "BORK!", :c {:m 1, :d ["WHAA", "WOOO", "WEEE"]}}))
=> (var user/cur)
```

Dereference cursor in order to get current values under it:

```clj
@cur
=> {:a "BORK!", :c {:m 1, :d ["WHAA" "WOOO" "WEEE"]}}
```

Cursor representing data structures can be accessed in the same way ordinary as data structures,
with exception that cursors pointing to underlying data structures are returned instead:

```clj
(def a-cur (:a cur))
=> (var user/a-cur)
@a-cur
=> "BORK!"
```

Any function holding reference to a cursor can change underlying data in atomic way.

```clj
; create another cursor pointing to the same data
(def another-cur (:a cur))
=> (var user/another-cur)
@another-cur
=> "BORK!"
; now update a-cur
(update! a-cur "UH!")
=> "UH!"
```

Both `transact!` and `update!` functions return updated value underneath cursor. All cursors
should now reflect change:

```clj
@cur
=> {:a "UH!", :b {:c 1, :d ["WHAA" "WOOO" "WEEE"]}}
@another-cur
=> "UH!"
```

Note that this can be for implementing LOL-style closures and callbacks, for example:

```clj
; define function using cursor
(defn wookie-says [mood]
  (let [{m :m d :d} @mood]
    (get d m "HUH?")))
=> (var user/wookie-says)
; it should work cursor to a structure containing two keys:
; :m - wookie mood, :d - wookie dictionary
(wookie-says (:c cur))
=> "WOOO"
; now we can create a function that returns what Chewbacca has to say at this moment:
(def chewbacca-says (partial wookie-says (:c cur)))
=> (var user/chewbacca-says)
(chewbacca-says)
=> "WOOO"
```

Such closures will reflect current values underneath cursor. Now we can set up another cursor
representing Chewbacca's mood ...

```clj
(def chewbacca-mood (-> cur :c :m))
=> (var user/chewbacca-mood)
@chewbacca-mood
=> 1
```

... and by changing its value we'll change how `chewbacca-says` function behaves:

```clj
; update! function overwrites value under cursor with another value
(update! chewbacca-mood 2)
=> 2
(chewbacca-says)
=> "WEEE"
; transact! function will get previous value under cursor and transform it using supplied function
(transact! chewbacca-mood inc)
=> 3
; Chewbacca's mood is now in illegal state (3), so Chewbacca doesn't know what to tell
(chewbacca-says)
=> "HUH?"
```

## Caveats



### Live code updates

TODO problems stemming from having too many long-lived closures


## Shortcomings (too little hammock)

There are several as right now the whole thing is just a two-evening effort aimed at solving
data refresh / reconfiguration problem in ring applications and at learning more about Clojure
data structure internals.

### Cursor consistency

TODO what happens when cursor points at some element in a vector and someone removes some prior elements ?

### Merging cursors

TODO how to synchronously pass two distinct elements of a cursor as a whole - how to ensure consistent reads ?

### Read only cursors

TODO how to prevent potential abuses and treating


## Thanks to

* David Nolen - for cursor concept in om;



## License

Copyright © 2014 Rafał Lewczuk <rafal.lewczuk@jitlogic.com>

Distributed under the Eclipse Public License, the same as Clojure.

