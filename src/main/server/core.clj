(ns server.core
  (:refer-clojure :exclude [abs update-keys])
  (:require
    [clojure.core.async :as async :refer [<! >!]]
    [org.httpkit.server :as server :refer [run-server]]))


#_(defn stream-counter
    []
    (let [ch (async/chan)]
      (async/go-loop [ctr 0]
        (async/>! ch ctr)
        (Thread/sleep 1000)
        (recur (inc ctr)))
     ch))


(defn ws-handler [ws]
  (let [ctr (atom 0)]
    (future
      (while true
        (Thread/sleep 1000) ; wait for 1 second
        (swap! ctr inc) ; increment counter
        (when (not= @ctr 10) ; send until ctr reaches 10
          (server/send! ws (str @ctr))))))) ; corrected line here

(defn -main [& args]
  (println "running main")
  (server/run-server
    {:port 1337
     :websocket ws-handler}
    {:timeout 600000}))

