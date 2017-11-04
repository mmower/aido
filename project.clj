(defproject sandbags/aido "0.3.0"
  :description "AIDO: AI do - behaviour tree library for Clojure and ClojureScript"
  :url "http://mattmower.com/aido"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [expectations "2.2.0-rc1"]]
  :plugins [[lein-expectations "0.0.8"]]
  :deploy-repositories [["clojars" {:sign-releases false}]])

