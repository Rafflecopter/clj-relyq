(ns qb.relyq.simpleq-test
  (:require [midje.sweet :refer :all]
            [qb.relyq.simpleq :as simpleq]))

(def cfg {:pool {} :spec {:host "localhost" :port 6379}})
(defmacro timeit [& form]
  `(let [t# (System/currentTimeMillis)]
    ~@form
    (- (System/currentTimeMillis) t#)))
(defn less-than [n]
  (chatty-checker [t] (< t n)))
(defn greater-than [n]
  (chatty-checker [t] (> t n)))

;; only do timeout tests when this file is freshly loaded
(def do-timeout-tests (atom true))

(facts "about push and pop"
  (let [k "test:simpleq:pushpop"]
    (fact "pop up front returns nil"
      (simpleq/pop cfg k) => nil)
    (fact "first push returns 1"
      (simpleq/push cfg k "el1") => 1)
    (fact "second push returns 2"
      (simpleq/push cfg k "el2") => 2)
    (fact "first pop returns el1"
      (simpleq/pop cfg k) => "el1")
    (fact "second pop returns el2"
      (simpleq/pop cfg k) => "el2")
    (simpleq/clear cfg k)))

(facts "about list and clear"
  (let [k "test:simpleq:listclear"]
    (fact "list on new queue is empty"
      (simpleq/list cfg k) => [])
    (fact "list after pushes lists them"
      (simpleq/push cfg k "el1")
      (simpleq/push cfg k "el2")
      (simpleq/list cfg k) => ["el2" "el1"])
    (fact "list didnt remove elements"
      (simpleq/list cfg k) => ["el2" "el1"])
    (fact "list after clear returns empty"
      (simpleq/clear cfg k)
      (simpleq/list cfg k) => [])))

(facts "about pull"
  (let [k "test:simpleq:pull"]
    (simpleq/push cfg k "el1")
    (simpleq/push cfg k "el2")
    (fact "pull finds its element and removes it"
      (simpleq/pull cfg k "el2") => 1
      (simpleq/list cfg k) => ["el1"])
    (fact "pull doesnt find element not there"
      (simpleq/pull cfg k "el3") => 0
      (simpleq/list cfg k) => ["el1"])
    (fact "pull finds last element in queue"
      (simpleq/pull cfg k "el1") => 1
      (simpleq/list cfg k) => [])
    (simpleq/clear cfg k)))

(facts "about spullpipe"
  (let [k1 "test:simpleq:spullpipe1"
        k2 "test:simpleq:spullpipe2"]
    (simpleq/push cfg k1 "el1")
    (simpleq/push cfg k1 "el2")
    (fact "spullpipe moves el1"
      (simpleq/spullpipe cfg k1 k2 "el1") => 1
      (simpleq/list cfg k1) => ["el2"]
      (simpleq/list cfg k2) => ["el1"])
    (fact "spullpipe doesnt move unknown el"
      (simpleq/spullpipe cfg k1 k2 "el3") => 0
      (simpleq/list cfg k1) => ["el2"]
      (simpleq/list cfg k2) => ["el1"])
    (fact "spullpipe moves last element and changes it"
      (simpleq/spullpipe cfg k1 k2 "el2" "el3") => 2
      (simpleq/list cfg k1) => []
      (simpleq/list cfg k2) => ["el3" "el1"])
    (simpleq/clear cfg k1 k2)))

(facts "about poppipe"
  (let [k1 "test:simpleq:poppipe1"
        k2 "test:simpleq:poppipe2"]
    (simpleq/push cfg k1 "el1")
    (simpleq/push cfg k1 "el2")
    (fact "poppipe moves el1"
      (simpleq/poppipe cfg k1 k2) => "el1"
      (simpleq/list cfg k1) => ["el2"]
      (simpleq/list cfg k2) => ["el1"])
    (fact "poppipe moves last element"
      (simpleq/poppipe cfg k1 k2) => "el2"
    (fact "poppipe doesnt if list empty"
      (simpleq/poppipe cfg k1 k2) => nil
      (simpleq/list cfg k1) => []
      (simpleq/list cfg k2) => ["el2" "el1"])
    (simpleq/clear cfg k1 k2))))

(facts "about bpop"
  (let [k "test:simpleq:bpop"]
    (simpleq/push cfg k "el1")
    (fact "bpop returns immediately if el avail"
      (timeit (simpleq/bpop cfg k) => "el1") => (less-than 100)
      (simpleq/list cfg k) => [])
    (when @do-timeout-tests
      (fact "bpop times out if no el avail"
        (timeit (simpleq/bpop cfg k 1) => nil) => (greater-than 1000)))
    (simpleq/clear cfg k)))


(facts "about bpoppipe"
  (let [k1 "test:simpleq:bpoppipe1"
        k2 "test:simpleq:bpoppipe2"]
    (simpleq/push cfg k1 "el1")
    (fact "bpoppipe returns immediately if el avail"
      (let [t (timeit (simpleq/bpoppipe cfg k1 k2) => "el1")]
        (< t 100) => true
        (simpleq/list cfg k1) => []
        (simpleq/list cfg k2) => ["el1"]))
    (when @do-timeout-tests
      (fact "bpoppipe times out if no el avail"
          (let [t (timeit (simpleq/bpoppipe cfg k1 k2 1) => nil)]
            (> t 1000) => true
            (simpleq/list cfg k1) => []
            (simpleq/list cfg k2) => ["el1"])
          (reset! do-timeout-tests false)))
    (simpleq/clear cfg k1 k2)))
