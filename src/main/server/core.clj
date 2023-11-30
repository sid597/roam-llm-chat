
(ns server.core
  (:require [compojure.core :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [wkok.openai-clojure.api :as api]
            [cheshire.core :as json]
            [server.env :as env :refer [oai-key]]
            [ring.adapter.jetty :as jetty]))


(defn oai [request]
  (let [messages  (-> request
                    :body
                    slurp
                    (json/parse-string true)
                    :documents)

        res (api/create-chat-completion
              {:model "gpt-4-1106-preview"
               :messages  messages
               :temperature 1
               :max_tokens 256
               :top_p 1
               :frequency_penalty 0
               :presence_penalty 0}
              {:api-key oai-key})]

   {:status 200
    :headers {"Content-Type" "text/plain"}
    :body   (-> res
              :choices
              first
              :message
              :content)}))


(defn- handle-preflight [_]
  {:status 200
   :headers {"Access-Control-Allow-Origin" "https://roamresearch.com"   ; Allow requests from this origin
             "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"
             "Access-Control-Allow-Headers" "Content-Type, Authorization"}})

(defn- cors-headers [response]
  (let [updated-response (update response :headers merge
                           {"Access-Control-Allow-Origin" "https://roamresearch.com"})]  ; Add CORS header
    updated-response))

(defroutes app-routes
  (OPTIONS "/chat-complete" [] handle-preflight)   ; Handle preflight OPTIONS request
  (POST "/chat-complete" request (cors-headers (oai request))))

(def app
  (-> app-routes
    (wrap-params)))

(defn -main [& args]
  (println "Starting server")
  (jetty/run-jetty app {:port 3000}))
