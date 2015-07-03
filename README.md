# relyq [![Build Status][1]][2]

A reliable message queue that uses redis as its storage. A compatible clojure implementation of [node-relyq](https://github.com/Rafflecopter/node-relyq). Also implements the `qb` interface.

```
[com.rafflecopter/relyq "0.1.0"]
```

## Usage

### Usage with QB

```clojure
(ns your-namespace-here
  (:require [qb.core :as qb]
            [qb.util :as qbutil]
            qb.relyq.core
            [clojure.core.async :refer (go-loop <! close!)]))

;; See below for config options
(def config {:type :relyq :redis redis-cfg})
(def q (qb/init! config))

;; Send messages to a destination
;; Destinations are relyq prefixes
(qb/send! q "qb:me:name" {:some :message})

;; Start a message listener at a source location
;; Sources are relyq prefixes
(let [{:keys [data stop]} (qb/listen q "qb:me:name"))]

  ;; data is a channel of {:result result-chan :msg msg}
  (go-loop []
    (let [{:keys [result msg]} (<! data)]
      (try (handle-msg msg)
           ;; Notify the queue of successful processing
           (qbutil/success result)
        (catch Exception e
          ;; Notify the queue of an error in processing
          (qbutil/error result (.getMessage e))))
    (recur))

  ;; At some point, you can stop the listener by closing the stop channel
  ;; Some implementations take a bit to close, so you should wait
  ;; until the data channel is closed to exit gracefully.
  (close! stop))
```

### Raw Usage

```clojure
(ns your-namespace-here
  (:require [qb.relyq.relyq :as relyq]))

(def config {:redis redis-cfg}) ; See below for more options
(def q (relyq/configure config))

(relyq/push q {:task "object"})
(relyq/process q) ; => {:task "object"} (may also have :id field if configured)
(relyq/process q) ; => nil (task has been moved out of queue)
(relyq/process q :block true) ; Blocks for processing of a task (see configuration for length of time)
(relyq/finish q task-object) ; remove from "doing" list
(relyq/fail q task-object) ; move to a "failed" list to analyze later
(relyq/fail q task-object assoc :error "error") ; move to "failed" list with update
```

## Configuration Options

Relyq is made up of two parts, a `QueueStore` and a `TaskStore`. Each has its own options:

`QueueStore` options

- `:qs-pref` Preference on store
- for any `:qs-pref`, `QSSimpleq` is used. This store uses multiple simple redis queues (see [simpleq.clj](https://github.com/Rafflecopter/relyq/blob/master/src/clj/qb/relyq/simpleq.clj)) to move tasks around without losing them (using atomic operations).
    + `:redis` Redis config (see [wcar docstring](https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine.clj#L29))
    + `:prefix` Required string prefix for the key in redis. (Delimeted by `:`)
    + `:btimeout` Timeout (in seconds) of blocking process. Defaults to 1 second.

`TaskStore` options

- `:ts-pref` Preference on store
- if `:ts-pref => :redis` or `nil`, `TSRedis` is used. This store puts encoded tasks in redis keys via `set` and `get`. It references them with ID's put in an `id-field`
    + `:redis` Redis config (see [wcar docstring](https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine.clj#L29))
    + `:fmt` Format for encoding task (`:json` or `:edn`, `:json` default)
    + `:prefix` Prefix for the key in redis. (Delimeted by `:`)
    + `:id-field` Field to attach an ID on. (Default to `:id`)
    + `:make-id` Function of no arguments to make an ID. Or it can be `:uuid` for UUID v4. (Defaults to `:uuid`)
- if `:ts-pref => :ref`, `TSRef` is used. This store encodes the whole task into the `QueueStore`.
    + `:fmt` Format for encoding task (`:json` or `:edn`, `:json` default)

## License

See [LICENSE](https://github.com/Rafflecopter/clj-relyq/blob/master/LICENSE) file


[1]: https://travis-ci.org/Rafflecopter/clj-relyq.png?branch=master
[2]: http://travis-ci.org/Rafflecopter/clj-relyq
