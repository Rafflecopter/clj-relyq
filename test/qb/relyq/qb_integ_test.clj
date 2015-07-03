(ns qb.relyq.qb-integ-test
  (:require [midje.sweet :refer :all]
            [qb.relyq.core]
            [qb.core :as qb]
            [qb.util :as util]
            [qb.relyq.simpleq-test :refer (cfg timeit less-than greater-than) :rename {cfg redis-cfg}]
            [qb.relyq.relyq-test :refer (clear-relyq is-uuid)]
            [clojure.core.async :as async :refer (close! <!!)]))

(def do-timeout-tests (atom true))

(defn take! [c]
  (async/alt!! c ([v] v)
               :default :na))

(def config {:type :relyq
             :redis redis-cfg})
(def dest "test:relyq:qbinteg:1")

(facts "about relyq's qb implementation"
  (let [q (qb/init! config)
        {:keys [data stop]} (qb/listen q dest)]
    (fact "send a message to dest"
      (take! (qb/send! q dest {:foo "bar1"})) => nil
      (take! (qb/send! q dest {:foo "bar2"})) => nil)
    (fact "receive message from listener"
      (let [rec (take! data)]
        rec => (contains {:result some? :msg (contains {:foo "bar1" :id is-uuid})})
        (fact "pass success back"
          (util/success (:result rec)))))
    (fact "receive 2nd message from listener"
      (let [rec (take! data)]
        rec => (contains {:result some? :msg (contains {:foo "bar2" :id is-uuid})})
        (fact "pass error back"
          (util/error (:result rec) "testerror"))))
    (fact "nothing received from listener"
      (take! data) => :na)
    (fact "stop should close data chan"
      (close! stop)
      (when @do-timeout-tests
        (<!! data) => nil
        (reset! do-timeout-tests false)))
    (clear-relyq (:cfg q))))