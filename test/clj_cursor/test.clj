(ns clj-cursor.test
  (:require [clojure.test :refer :all]
            [clj-cursor.core :refer :all]))


(deftest map-cursor-getters-test
  (testing "Simple getters and type checks."
    (is (cursor? (cursor {})))
    (is (= {:a 1} @(cursor {:a 1})))
    (is (= {:a 1} @(cursor (atom {:a 1}))))
    (is (= 1 @(:a (cursor {:a 1}))))
    (is (= 1 @((cursor {:a 1}) :a)))
    (is (= nil @((cursor {:a 1}) :b)))
    (is (= 2 @((cursor {:a 1}) :b 2)))
    (is (= 1 @(get (cursor {:a 1}) :a)))
    (is (= nil @(get (cursor {:a 1}) :b)))
    (is (= 2 @(get (cursor {:a 1}) :b 2)))
    (is (= 1 (count (cursor {:a 1}))))
  ))


(deftest map-cursor-updates-test
  (testing "Updates of a map cursor."
    (let [a1 (atom {:a 0}), c1 (cursor a1), c2 (:a c1)]
      (is (= 0 @c2))
      (swap! a1 #(assoc % :a 1))
      (is (= 1 @c2))
      (is (= 2 (update! c2 2)))
      (is (= 2 @c2))
      (is (= {:a 2} @c1))
      (is (= {:a 2} @a1))
      (is (= 3 (transact! c2 inc)))
      (is (= 3 @c2))
      (is (= {:a 3} @c1))
      (is (= {:a 3} @a1))
  )))


(deftest vec-cursor-getters-test
  (testing "Simple getters and type checks for vector cursor."
    (is (cursor? (cursor [])))
    (is (= [1 2] @(cursor [1 2])))
    (is (= [3 4] @(cursor (atom [3 4]))))
    (is (= 3 @(get (cursor [3 4]) 0)))
    (is (= 3 @((cursor [3 4]) 0)))
    (is (= 2 (count (cursor [1 2]))))
  ))


(deftest vec-cursor-updates-test
  (testing "Updates of a vector cursor."
    (let [a1 (atom [1 2]), c1 (cursor a1), c2 (get c1 0)]
      (is (= 1 @c2))
      (reset! a1 [3 4])
      (is (= 3 @c2))
      (is (= 5 (update! c2 5)))
      (is (= 5 @c2))
      (is (= [5 4] @c1))
  )))


