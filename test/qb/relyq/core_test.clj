(ns qb.relyq.core-test
  (:require [midje.sweet :refer :all]
            [qb.relyq.core :as relyq]
            [qb.relyq.simpleq-test :refer (cfg timeit less-than greater-than) :rename {cfg redis-cfg}]
            [qb.relyq.simpleq :as simpleq]
            [clojure.edn :as edn]))

(def do-timeout-tests (atom true))

(defn make-tests
  [{:keys [name config cleanup get-list idcheck]}]
  (facts (str "about " name)
    (let [cfg (atom nil) tasks (atom [])]
      (fact "configure works"
        (reset! cfg (relyq/configure config))
        (:qs @cfg) => some?
        (:ts @cfg) => some?)
      (fact "push a task"
        (relyq/push @cfg {:some "task"}) => 1
        (relyq/push @cfg {:some "task2"}) => 2
        (get-list @cfg :todo) => (contains (contains {:some "task"}) (contains {:some "task2"})))
      (fact "process a task"
        (let [task (relyq/process @cfg)]
          task => (contains {:some "task"})
          (:id task) => idcheck
          (swap! tasks conj task)))
      (fact "blocking process a task"
        (timeit (let [task (relyq/process @cfg :block true)]
          task => (contains {:some "task2"})
          (:id task) => idcheck
          (swap! tasks conj task)))
         => (less-than 100))
      (fact "process returns nil on no task"
        (relyq/process @cfg) => nil)
      (when @do-timeout-tests
        (fact "blocking process times out"
          (timeit (relyq/process @cfg :block true) => nil)
           => (greater-than 1000))
        (reset! do-timeout-tests false))
      (fact "finish task 1"
        (relyq/finish @cfg (first @tasks)))
      (fact "fail task 2"
        (relyq/fail @cfg (second @tasks) assoc :failed true))
      (fact "todo list empty"
        (get-list @cfg :todo) => empty?)
      (fact "doing list empty"
        (get-list @cfg :doing) => empty?)
      (fact "failed list has task 2"
        (get-list @cfg :failed) => [(assoc (second @tasks) :failed true)])
      (cleanup @cfg))))

(make-tests
  {:name "simpleq/redis/json (default config)"
   :config {:redis redis-cfg
            :prefix "test:relyq:simpleqredisjson"
            :fmt :json}
   :idcheck #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
   :cleanup (fn [{{:keys [todo doing failed]} :qs}]
              (simpleq/clear redis-cfg todo doing failed))
   :get-list (fn [{:keys [ts qs]} k]
              (->> (k qs)
                   (simpleq/list redis-cfg)
                   (map #(relyq/ts-get ts %))
                   reverse))})
(make-tests
  {:name "simpleq/ref/edn"
   :config {:redis redis-cfg
            :prefix "test:relyq:simpleqrefedn"
            :ts-pref :ref
            :fmt :edn}
   :idcheck anything
   :cleanup (fn [{{:keys [todo doing failed]} :qs}]
              (simpleq/clear redis-cfg todo doing failed))
   :get-list (fn [{:keys [ts qs]} k]
              (->> (k qs)
                   (simpleq/list redis-cfg)
                   (map #(edn/read-string %))
                   reverse))})