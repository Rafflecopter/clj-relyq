(ns qb.relyq.core
  (:require [clojure.core.async :refer (pipe chan)]
            [qb (core :refer (init!))
                (util :refer (wrap-ack-chan-xf blocking-listener ack-blocking-op*))]
            [qb.relyq.relyq :as relyq])
  (:import [qb.core Sender Listener]))

(defn configure-prefix [cfg pre]
  (-> cfg (assoc :prefix pre) relyq/configure))

(defrecord RelyQ [cfg]
  Sender
  (send! [this dest task]
    (ack-blocking-op*
      (relyq/push (configure-prefix cfg dest) task)))

  Listener
  (listen [this source]
    (let [cfg (configure-prefix cfg source)]
      (update (blocking-listener #(relyq/process cfg :block true))
              :data pipe
                (chan 1 (wrap-ack-chan-xf
                          #(relyq/finish cfg %)
                          #(relyq/fail cfg %1 assoc :error %2)))))))

(defmethod init! :relyq [cfg] (RelyQ. cfg))