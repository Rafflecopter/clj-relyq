(ns qb.relyq.core
  (:require [clojure.core.async :refer (pipe chan)]
            [qb (core :refer (init!))
                (util :refer (wrap-result-chan-xf blocking-listener) :as util)]
            [qb.relyq.relyq :as relyq])
  (:import [qb.core Sender Listener]))

(defn configure-prefix [cfg pre]
  (-> cfg (assoc :prefix pre) relyq/configure))

(def closed-result-chan
  (let [rc (util/result-chan)] (util/success rc) rc))

(defrecord RelyQ [cfg]
  Sender
  (send! [this dest task]
    (relyq/push (configure-prefix cfg dest) task)
    closed-result-chan)

  Listener
  (listen [this source]
    (let [cfg (configure-prefix cfg source)]
      (update (blocking-listener #(relyq/process cfg :block true))
              :data pipe
                (chan 1 (wrap-result-chan-xf
                          #(relyq/finish cfg %)
                          #(relyq/fail cfg %1 assoc :error %2)))))))

(defmethod init! :relyq [cfg] (RelyQ. cfg))