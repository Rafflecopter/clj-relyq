(ns qb.relyq.listener-test
  (:require [midje.sweet :refer :all]
            [qb.relyq.listener :refer (blocking-listener)]
            [clojure.core.async :refer (<!! >!! chan close! alt!!) :as async]))

(defn take! [c]
  (async/alt!! c ([v] v)
               :default :na))
(defn put! [c v]
  (async/alt!! [[c v]] :wrote
               :default :na))

(defn blocking-testfn [c]
  (let [t (async/timeout 50)]
    (async/alt!! c ([v] v)
                 t ([_] nil))))

(facts "about blocking-listener"
  (let [c (chan)
        {:keys [data stop]} (blocking-listener blocking-testfn c)]
    (fact "data channel empty to start"
      (take! data) => :na)
    (fact "stop channel empty to start"
      (take! stop) => :na)
    (fact "can put a value on input chan"
      (put! c 10) => :wrote)
    (fact "cant put channel immediately on chan before take"
      (put! c 11) => :na)
    (fact "can pull same value off of data chan"
      (take! data) => 10)
    (fact "nothing more on data chan"
      (take! data) => :na)
    (fact "now can put another value on input chan"
      (put! c 11) => :wrote)
    (fact "stop channel still empty"
      (take! stop) => :na)
    (close! stop)
    (fact "second value still sitting on data chan after close"
      (take! data) => 11)
    (fact "now data chan is closed after stop is closed"
      (take! data) => nil)))