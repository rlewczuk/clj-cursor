(ns clj-cursor.core
  (:import (clojure.lang IDeref Atom ILookup Counted IFn AFn Indexed)))

; TODO not sure if these methods are needed at all; ICursor is used solely as a marker right now
(defprotocol ICursor
  (path [cursor])
  (state [cursor]))


(defprotocol ITransact
  (-transact! [cursor f]))


(declare to-cursor cursor?)


(deftype ValCursor [value state path]
  IDeref
  (deref [_]
    (get-in @state path value))
  ICursor
  (path [_] path)
  (state [_] state)
  ITransact
  (-transact! [_ f]
    (get-in
      (swap! state (if (empty? path) f #(update-in % path f)))
      path)))


(deftype MapCursor [value state path]
  Counted
  (count [_]
    (count (get-in @state path value)))
  ICursor
  (path [_] path)
  (state [_] state)
  IDeref
  (deref [_]
    (get-in @state path value))
  IFn
  (invoke [this key]
    (get this key))
  (invoke [this key defval]
    (get this key defval))
  (applyTo [this args]
    (AFn/applyToHelper this args))
  ILookup
  (valAt [obj key]
    (.valAt obj key nil))
  (valAt [_ key defv]
    (let [value (get-in @state path value)]
      (to-cursor (get value key defv) state (conj path key) defv)))
  ITransact
  (-transact! [cursor f]
    (get-in
      (swap! state (if (empty? path) f #(update-in % path f)))
      path)))


(deftype VecCursor [value state path]
  Counted
  (count [_]
    (count (get-in @state path)))
  ICursor
  (path [_] path)
  (state [_] state)
  IDeref
  (deref [_]
    (get-in @state path))
  IFn
  (invoke [this i]
    (nth this i))
  (applyTo [this args]
    (AFn/applyToHelper this args))
  ILookup
  (valAt [this i]
    (nth this i))
  (valAt [this i not-found]
    (nth this i not-found))
  Indexed
  (nth [_ i]
    (let [value (get-in @state path value)]
      (to-cursor (nth value i) state (conj path i) nil)))
  (nth [_ i not-found]
    (let [value (get-in @state path value)]
      (to-cursor (nth value i not-found) state (conj path i) not-found)))
  ITransact
  (-transact! [cursor f]
    (get-in
      (swap! state (if (empty? path) f #(update-in % path f)))
      path)))


(defn- to-cursor
  ([v state path value]
   (cond
     (cursor? v) v
     (map? v) (MapCursor. value state path)
     (vector? v) (VecCursor. value state path)
     :else (ValCursor. value state path)
     )))


(defn cursor? [c]
  "Returns true if c is a cursor."
  (satisfies? ICursor c))


(defn cursor [v]
  "Creates cursor from supplied value v. If v is an ordinary
   data structure, it is wrapped into atom. If v is an atom,
   it is used directly, so all changes by cursor modification
   functions are reflected in supplied atom reference."
  (to-cursor (if (instance? Atom v) @v v)
             (if (instance? Atom v) v (atom v))
             [] nil))


(defn transact! [cursor f]
  "Changes value beneath cursor by passing it to a single-argument
   function f. Old value will be passed as function argument. Function
   result will be the new value."
  (-transact! cursor f))


(defn update! [cursor v]
  "Replaces value supplied by cursor with value v."
  (-transact! cursor (constantly v)))

; TODO (destroy! ... ) - removes data structure from underneath cursor



