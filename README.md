# clj-cursor

This library implements cursor on Clojure data structures. It is based on David Nolen's concept
implemented in the [om library](https://github.com/swannodette/om). Cursor allows storing whole
application state in a single structure, yet still be able to swap underlying application state
and allow application components receiving changes without hassle (think online reconfiguration etc.).

This is especially important for async processes/threads/loops/closures that receive some
initial configuration and then keep it forever. When using raw immutable data structures
programmer needs either implement online reconfiguration in each and every
process instance, pass atomic reference to whole application state or scatter atoms over a
data structure representing application state. Each of these approaches has its own shortcomings:
either some boilerplate code needs to be implemented, application components see state of other
components (and need additional information where their own state is located) or application
state isn't really immutable and data under references to some parts of app state can change
in uncontrolled way. Cursor is an attempt to supply application components with configuration/state
they need and at the same time retain proper isolation of component data without boilerplate code
nor special conventions.


## Usage

As project is still not available in clojars repository, it needs to be compiled and installed
into local repository:

```bash
git clone https://github.com/rlewczuk/clj-cursor clj-cursor
cd clj-cursor
lein jar
cd target/provided
mvn deploy:deploy-file -DgroupId=clj-cursor -DartifactId=clj-cursor -Dversion=$VER -Dpackaging=jar -Dfile=clj-cursor-0.0.1.jar -Durl=file:~/.m2/repository
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
=> {:a "UH!", :c {:m 1, :d ["WHAA" "WOOO" "WEEE"]}}
@another-cur
=> "UH!"
```

Note that this can be for implementing LOL-style (Let-Over-Lambda) closures and callbacks, for example:

```clj
; define function using cursor
(defn wookie-says [mood]
  (let [{m :m d :d} @mood]
    (get d m "HUH?")))
=> (var user/wookie-says)
; it should work cursor to a structure containing two keys:
; :m - wookie mood (integer index), :d - wookie dictionary (vector)
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

### Core.async example

Async processes spawned by `go` macro expose the same problem as LOL closures (as they are implemented
as LOL closures in many cases): once passed data will never change unless it is mutable (or atomic
reference). Cursor can be used the same way as in ordinary closure. Let's load `core.async` functions
first:

```clj
(require '[clojure.core.async :refer [>! <! >!! <!! chan go]])
```

Now we create a process spawning function:

```clj
(defn async-process [conf]
  (let [comm-ch (chan)]
    (go
      (loop []
        (case (<! comm-ch)
          :hello (do (>! comm-ch (str "Hello, " @(:msg conf) "!")) (recur))
          :term (>! comm-ch :bye)
          (>! comm-ch :error)
          )))
    comm-ch))
```
Function accepts cursor to a structure, spawns new process and returns communication
channel to spawned process:

```clj
(def conf (cursor {:msg "world"}))
(def proc-ch (async-process conf))
```
So now we can communicate with process:

```clj
(>!! proc-ch :hello)
=> true
(<!! proc-ch)
=> "Hello, world!"
```

Now we change value under cursor and ask process again:

```clj
(update! (:msg conf) "async")
=> "async"
(>!! proc-ch :hello)
=> true
(<!! proc-ch)
=> "Hello, async!"
```

It is easy to implement more control over process reconfiguration:

```clj
(defn async-process-2 [conf-cur]
  (let [comm-ch (chan)]
    (go
      (loop [conf @conf-cur]
        (case (<! comm-ch)
          :hello (do (>! comm-ch (str "Hello, " (:msg conf) "!")) (recur conf))
          :reload (do (>! comm-ch :ok) (recur @conf-cur))
          :term (>! comm-ch :bye)
          (>! comm-ch :error)
          )))
    comm-ch))
```

In above example process needs to be explicitly notified about configuration change.
This still has advantages over manual config data provision as it is just signalling that
can be easily implemented using pub/sub channels.


### Ring webapp example

TBD


## Caveats

First thing to remember is that cursors are NOT data. Cursors implement only subset of operations
(mostly read operations) on a subset of Clojure data structures (maps, vectors). More
operations and supported data structures may (or may not) be added over time.


### Cursor reads and data consistency

Cursor - if not properly used - may neglect advantages of having immutable data structures. In order
to ensure full consistency, it is recommended to dereference cursor once per application processing
cycle (eg. http request). Resulting (dereferenced) data will be immutable and thus it is guaranteed to
be consistent. It is also compatible with purely functional style while cursors are not. Try to keep
your code in pure function as far as it can go. Dereference cursor once and as early as possible.


### Long lived closures and processes

Long lived closures either contain some kind of application processing loop (main loop) or are passed
to processing loop and repeatedly called. Also ring handlers if created in LOL-style fall into this
category. Try identifying such objects and pass cursors to them instead of ordinary data to ensure
easy online reconfiguration.


## Shortcomings (too little hammock)

This is very early implementation that still suffers from too-little-hammock syndrome, so it propably
has quite a lot of shortcomings. Identified ones have been described below and can be treated as TODO
list for future development.


### Data consistency

Current cursor implementation is a dead simple wrapper over maps and vectors. It (still) lacks lots of features,
in particular guards against changes of underlying data structure when some cursors inside such structure
already exist. Cursors operating on vectors os prime example of this behavior:

```clj
(def c (cursor [1 2 3]))
=> (var user/c)
(def c1 (get c 1))
=> (var user/c1)
@c1
=> 2
(transact! c #(vec (rest %)))
=> [2 3]
@c1
=> 3
```


### Merging cursors

There are situations where some application component needs data scattered around application state.
A component might require its own configuration and state, some dictionary (cache) data (shared across
several components) and database connection. Most trivial approach might be just pass all parts or
bind them with a structure but this way programmer loses consistency guarantees. Some kind of composite
cursor might be useful in such cases. TBD (To Be Discussed)


### Read only cursors

This might be useful for preventing abuses of cursors in certain situations. TBD (To Be Discussed).


### Destroying cursors (and underlying data)

This is fairly simple: `(destroy! cursor)` can be implemented which will disassociate data structure
beneath cursor. This might be useful for lifecycle management of application components - especially
when coupled with possibility of defining destructor function (eg. closing database connection pool).


## Thanks and credits

This projects borrows heavily from David Nolen's om cursor. Previous attempts at
tackling problem of managing state of non-trivial applications by Stuart Sierra also influenced
me to some extent. So, great thanks for that.


## License

Copyright © 2014 Rafał Lewczuk <rafal.lewczuk@jitlogic.com>

Distributed under the Eclipse Public License, the same as Clojure.

