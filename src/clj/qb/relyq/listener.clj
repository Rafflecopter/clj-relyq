(ns qb.relyq.listener
  (:require [clojure.core.async :refer (go go-loop chan close! <! >! alt!) :as async]))

;; Operations

(defn blocking-listener
  "Start a listener executing a blocking call.
  Returns a an object containing keys:
    - :data channel of messages from the queue
    - :stop channel that when closed, will stop
            the listener and close the data chan"
  [block-op & args]
  (let [data (chan)
        stopper (chan)]

    (go-loop [datum nil]
      (when datum (>! data datum))
      (when (= :continue (alt! stopper ([_] (close! data))
                               :default :continue))
        (recur (apply block-op args))))

    {:data data :stop stopper}))