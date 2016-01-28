# relyq [![Build Status][1]][2]

A reliable message queue that uses redis as its storage. A compatible clojure implementation of [node-relyq](https://github.com/Rafflecopter/node-relyq). Also implements the `qb` interface.

```
[com.rafflecopter/relyq "0.1.0"]
```

## Usage

### Barebones Getting Started Config

Here's a barebones config to get you going.

```clojure
{:type :relyq
 :redis {:pool {}
         :spec {:host Str
                :port Int}}
 :prefix Str}
```

### Usage with QB

```clojure
(ns your-namespace-here
  (:require [qb.core :as qb]
            [qb.util :refer (ack-success nack-error)]
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

  ;; data is a channel of {:ack ack-chan :msg msg}
  (go-loop []
    (let [{:keys [ack msg]} (<! data)]
      (try (handle-msg msg)
           ;; Notify the queue of successful processing
           (ack-success ack)
        (catch Exception e
          ;; Notify the queue of an error in processing
          (nack-error ack (.getMessage e))))
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

|key        | Type    | Desc                               |
|---        |---      |:---                                |
|:type      |Keyword  |The only available type is `:relyq`|
|:prefix    |Str      |Listen to redis key {prefix}{delim}todo. This is only used when using relyq directly (no qb).|
|:redis     |Map      |See [wcar docstring][3]|
|:btimeout  |Int      |Timeout (seconds) of blocking redis process (Defaults to 1)|
|:fmt       |Keyword  |Format for encoding task (`:json` or `:edn`, `:json` by defaut)|
|:id-field  |Keyword  |Field where task id can be found (Default: `:id`)|
|:make-id   |Function |Creates a random id for the task (Defaults to uuid)|

## License

See [LICENSE](https://github.com/Rafflecopter/clj-relyq/blob/master/LICENSE) file


[1]: https://travis-ci.org/Rafflecopter/clj-relyq.png?branch=master
[2]: http://travis-ci.org/Rafflecopter/clj-relyq
[3]: https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine.clj#L29
