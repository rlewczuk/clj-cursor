(ns clj-cursor.samples
  (:require [clojure.test :refer :all]
            [clj-cursor.core :refer :all]
            [clojure.core.async :refer [>! <! >!! <!! chan go]]))


(defn async-process [conf]
  "Async process with immediate (implicit) reconfiguration."
  (let [comm-ch (chan)]
    (go
      (loop []
        (case (<! comm-ch)
          :hello (do (>! comm-ch (str "Hello, " @(:msg conf) "!")) (recur))
          :term (>! comm-ch :bye)
          (>! comm-ch :error)
          )))
    comm-ch))


(deftest async-process-test
  (let [conf (cursor {:msg "world"})
        proc-ch (async-process conf)]
    (>!! proc-ch :hello)
    (is (= "Hello, world!" (<!! proc-ch)))
    (update! (:msg conf) "async")
    (>!! proc-ch :hello)
    (is (= "Hello, async!" (<!! proc-ch)))
    (>!! proc-ch :term)
    (is (= :bye (<!! proc-ch)))
  ))


(defn async-process-2 [conf-cur]
  "Async process with explicit reconfiguration."
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


(deftest async-process-2-test
  (let [conf (cursor {:msg "world"})
        proc-ch (async-process-2 conf)]
    (>!! proc-ch :hello)
    (is (= "Hello, world!" (<!! proc-ch)))
    (update! (:msg conf) "async")
    (>!! proc-ch :hello)
    (is (= "Hello, world!" (<!! proc-ch)))
    (>!! proc-ch :reload)
    (is (= :ok (<!! proc-ch)))
    (>!! proc-ch :hello)
    (is (= "Hello, async!" (<!! proc-ch)))
    (>!! proc-ch :term)
    (is (= :bye (<!! proc-ch)))
    ))

