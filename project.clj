(defproject com.rafflecopter/relyq "0.1.0"
  :description "Implementation of relyq, a reliable redis-based queue"
  :url "http://github.com/Rafflecopter/clj-relyq"
  :license {:name "MIT"
            :url "http://github.com/Rafflecopter/clj-relyq/blob/master/LICENSE"}
  :scm {:name "git"
        :url "https://github.com/Rafflecopter/clj-relyq"}
  :deploy-repositories [["clojars" {:creds :gpg}]]

  :resource-paths ["src/lua"]
  :source-paths ["src/clj"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/carmine "2.11.1"]
                 [org.clojure/data.json "0.2.6"]
                 [com.rafflecopter/qb "0.1.0"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.3"]]}})
