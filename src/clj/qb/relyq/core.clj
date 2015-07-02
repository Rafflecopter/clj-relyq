(ns qb.relyq.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [qb.relyq.simpleq :as simpleq])
  (:import [java.util UUID]))

(def delim ":")
(defn uuid [] (str (UUID/randomUUID)))

;; QueueStore

(defprotocol ^:private QueueStore
  "Queue for task references"
  (qs-push [qs tref] "Push a reference onto todo queue")
  (qs-process [qs block?]
    "pull task reference out of todo queue
    Push into doing list. Return task reference
    Block if block?")
  (qs-remove [qs tref] "Remove reference from doing list.")
  (qs-fail [qs tref tref-new]
    "Remove reference from doing list.
    Add new reference to fail list."))

(defrecord ^:private QSSimpleq [redis btimeout todo doing failed]
  QueueStore
  (qs-push [_ tref] (simpleq/push redis todo tref))
  (qs-process [_ block?] (if block? (simpleq/bpoppipe redis todo doing btimeout)
                                    (simpleq/poppipe redis todo doing)))
  (qs-remove [_ tref] (simpleq/pull redis doing tref))
  (qs-fail [_ tref tref-new] (simpleq/spullpipe redis doing failed tref tref-new)))

(defn- queue-store [{:keys [qs-pref redis btimeout prefix]
                    :or {btimeout 1 prefix "relyq"}}]
  (case qs-pref
    (QSSimpleq. redis btimeout (str prefix delim "todo")
                               (str prefix delim "doing")
                               (str prefix delim "failed"))))

;; TaskStore


(defprotocol ^:private TaskStore
  "Store task objects with references"
  (ts->tref [ts task] "Get reference for task object.")
  (ts-save [ts tref task] "Save a task object. Return reference.")
  (ts-get [ts tref] "Retrieve a task object. Return object (or nil if not found).")
  (ts-remove [ts tref] "Remove a task object from storage."))

(defrecord ^:private TSRedis [redis key encoder decoder id-field make-id]
  TaskStore
  (ts->tref [_ task] (or (get task id-field) (make-id)))
  (ts-save [_ tref task]
    (wcar redis (->> (encoder (assoc task id-field tref))
                     (car/set (str key delim tref)))))
  (ts-get [_ tref]
    (->> (wcar redis (car/get (str key delim tref)))
         decoder))
  (ts-remove [_ tref]
    (wcar redis (car/del (str key delim tref)))))

(defrecord ^:private TSRef [encode decode]
  TaskStore
  (ts->tref [_ task] (encode task))
  (ts-get [_ tref] (decode tref))
  (ts-save [_ _ _] nil)
  (ts-remove [_ _] nil))

(defn- task-store [{:keys [ts-pref fmt redis prefix id-field make-id]
                    :or {prefix "relyq" id-field :id}}]
  (let [[encode decode] (case fmt :edn [pr-str edn/read-string]
                                  [json/write-str #(json/read-str % :key-fn keyword)])
        make-id (if (fn? make-id) make-id uuid)]
    (case ts-pref
      :ref (TSRef. encode decode)
      (TSRedis. redis (str prefix delim "jobs") encode decode id-field make-id))))

;; External interface

(defn configure
  "Create a config for a relyq instance based on:
  QueueStore:
    QSSimpleq (:qs-pref default, original node-relyq):
      - :redis carmine redis config
      - :btimeout blocking timeout in seconds (should be 1)
      - :prefix string redis prefix
      - :delim string redis delim (default \":\")
  TaskStore:
    TSRedis (:ts-pref default, original node-relyq):
      - :redis carmine redis config
      - :prefix string redis prefix
      - :delim string redis delim (default \":\")
      - :fmt encode/decode format (:json :edn)
      - :idfield id field of tasks (default :id)
    TSRef (:ts-pref => :ref, see https://gist.github.com/leroix/f223a1c5e295ae52e95d):
      - :redis carmine redis config
      - :fmt encode/decode format (:json :edn)"
  [cfg]
  {:qs (queue-store cfg)
   :ts (task-store cfg)})

(defn push
  "Push a task object onto the todo queue.
  Returns length of todo queue."
  [{:keys [ts qs]} task]
  (let [tref (ts->tref ts task)]
    (ts-save ts tref task)
    (qs-push qs tref)))

(defn process
  "Process a task from todo to doing queue
  If block? process will block? with timeout set during config"
  [{:keys [ts qs]} & {:keys [block]}]
   (if-let [tref (qs-process qs block)]
     (ts-get ts tref)))

(defn finish
  "Finish a task by removing it from all subqueues and storage"
  [{:keys [ts qs]} task]
  (let [tref (ts->tref ts task)]
    (qs-remove qs tref)
    (ts-remove ts tref)))

(defn fail
  "Fail a task by moving it to fail subqueue.
  Also update it in storage"
  ([cfg task] (fail cfg task identity))
  ([{:keys [ts qs]} task update-fn & update-args]
    (let [tref (ts->tref ts task)
          task-update (apply update-fn task update-args)
          tref-update (ts->tref ts task-update)]
      (if (qs-fail qs tref tref-update)
        (ts-save ts tref-update task-update)))))