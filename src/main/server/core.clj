(ns server.core
  (:require
    [wkok.openai-clojure.api :as api]
    [clojure.string     :as str]
    [ring.middleware.defaults]
    [ring.middleware.anti-forgery :as anti-forgery]
    [compojure.core     :as comp :refer [defroutes GET POST]]
    [compojure.route    :as route]
    [clojure.core.async :as async  :refer [<! <!! >! >!! put! chan go go-loop]]
    [taoensso.encore    :as encore :refer [have have?]]
    [taoensso.timbre    :as timbre]
    [server.oai        :as oai]
    [org.httpkit.server :as http-kit]
    [taoensso.sente :as sente] ; <--- Add this
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]] ; <--- Recommended
    [taoensso.sente.server-adapters.http-kit      :refer [get-sch-adapter]]))

;;; Add this: --->
(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket-server! (get-sch-adapter) {})]

  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids)) ; Watchable, read-only atom



(defn call-openai-api [ messages]
  (let [events (api/create-chat-completion
                 {:model "gpt-4-1106-preview"
                  :messages messages
                  :temperature 1
                  :max_tokens 256
                  :top_p 1
                  :frequency_penalty 0
                  :stream true
                  :presence_penalty 0}
                 {:api-key "GM"})]
    (go
      (loop []
        (let [event (<! events)
              uid   (first @connected-uids)]
          (when (not= :done event)
            ;; Send the event token to the client
            (chsk-send! uid [:openai-event event])
            (recur)))))))



(defroutes my-app-routes
  ;; <other stuff>
  (POST "/api/openai" [messages] (call-openai-api messages))
  ;;; Add these 2 entries: --->
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req)))


(def my-app
  (-> my-app-routes
    ;; Add necessary Ring middleware:
    ring.middleware.keyword-params/wrap-keyword-params
    ring.middleware.params/wrap-params
    ring.middleware.anti-forgery/wrap-anti-forgery
    ring.middleware.session/wrap-session))

(defn -main [& args]
  (println "Starting server on port 3000")
  (http-kit/run-server my-app {:port 3000}))