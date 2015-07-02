(ns qb.relyq.simpleq
  (:refer-clojure :exclude [pop list])
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.java.io :as io]))

(def ^:private lua-safepullpipe
  (-> "relyq/safepullpipe.lua" io/resource slurp))

;; Operations

(defn push
  "Push an element onto a queue.
  Returns length of queue."
  [cfg key el]
  (wcar cfg (car/lpush key el)))

(defn pop
  "Pop an element off the queue.
  Returns almost immediately.
  Return element or nil (if none available)."
  [cfg key]
  (wcar cfg (car/rpop key)))

(defn bpop
  "Blocking version of pop.
  Timeout is optional and in seconds."
  ([cfg key] (bpop cfg key 0))
  ([cfg key timeout]
    (second (wcar cfg (car/brpop key timeout)))))

(defn pull
  "Pull an element out of the queue.
  If element is repeated. Pull the oldest one.
  Returns number of elements removed (0 or 1)"
  [cfg key el]
  (wcar cfg (car/lrem key -1 el)))

(defn spullpipe
  "Pull an element out of a queue
  And put into another queue atomically.
  Return number of elements in other queue or 0 (if no element found)
  Note: Only puts new element into other queue if it exists in the first queue.
  Allows a new element to replace old element in other queue."
  [cfg key otherkey el & [el-new]]
  (wcar cfg (car/eval* lua-safepullpipe 2 key otherkey el (or el-new el))))

(defn poppipe
  "Pop element out of queue and put on another queue.
  Returns almost immediately
  Return element being moved or nil (if none available)"
  [cfg key otherkey]
  (wcar cfg (car/rpoplpush key otherkey)))

(defn bpoppipe
  "Blocking verstion of poppipe.
  Timeout is optional and in seconds."
  ([cfg key otherkey] (bpoppipe cfg key otherkey 0))
  ([cfg key otherkey timeout]
    (wcar cfg (car/brpoplpush key otherkey timeout))))

(defn clear
  "Clear the queue of all elements"
  [cfg & keys]
  (wcar cfg (apply car/del keys)))

(defn list
  "List out all elements in the queue"
  [cfg key]
  (wcar cfg (car/lrange key 0 -1)))