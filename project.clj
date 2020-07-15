(defproject sandbags/aido "0.4.0"
  :description "AIDO: AI do - behaviour tree library for Clojure and ClojureScript"
  :url "http://mattmower.com/aido"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [expectations "2.1.10"]]
  :plugins [[lein-expectations "0.0.8"]]
  :deploy-repositories [["clojars" {:sign-releases false}]])

