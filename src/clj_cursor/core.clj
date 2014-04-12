(ns clj-cursor.core
  (:import (clojure.lang IDeref Atom ILookup Counted IFn AFn)))


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
    (get-in (swap! state #(update-in % path f)) path)))


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
    (.valAt this key))
  (invoke [this key defval]
    (.valAt this key defval))
  (applyTo [this args]
    (AFn/applyToHelper this args))
  ILookup
  (valAt [obj key]
    (.valAt obj key nil))
  (valAt [_ key defv]
    (let [value (get-in @state path value)
          newv (get value key defv)]
      (to-cursor newv state (conj path key))))
  ITransact
  (-transact! [cursor f]
    (get-in (swap! state #(update-in % path f)) path)))


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
  (invoke [this idx]
    (let [value (get-in @state path value)]
      (to-cursor (value idx) state (conj path idx))))
  ILookup
  (valAt [obj key]
    (.valAt obj key nil))
  (valAt [_ key defv]
    (let [value (get-in @state path value)]
      (to-cursor (get value key defv) state (conj path key))))
  ITransact
  (-transact! [cursor f]
    (get-in (swap! state #(update-in % path f)) path)))


(defn- to-cursor
  ([value state path]
   (cond
     (cursor? value) value
     (map? value) (MapCursor. value state path)
     (vector? value) (VecCursor. value state path)
     :else (ValCursor. value state path)
   )))


(defn cursor? [c]
  (satisfies? ICursor c))


(defn cursor [value]
  (to-cursor (if (instance? Atom value) @value value)
             (if (instance? Atom value) value (atom value)) []))


(defn transact! [cursor fn]
  (-transact! cursor fn))


(defn update! [cursor value]
  (-transact! cursor (constantly value)))

; TODO (destroy! ... ) - removes data structure from underneath cursor



