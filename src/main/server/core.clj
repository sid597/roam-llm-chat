(ns server.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [wkok.openai-clojure.api :as api]
            [cheshire.core :as json]
            [tolkien.core :as token]
            [server.env :as env :refer [oai-key pass-key]]
            [ring.adapter.jetty :as jetty]))


(defn extract-gpt-version [s]
  (let [pattern (re-pattern "(gpt-3\\.5-turbo|gpt-4)")]
    (first (re-find pattern s))))
(extract-gpt-version "\"gpt-3.5\"")



(defn count-tokens [request]
  (println "got request to count tokens")
  (let [rq       (-> request
                    :body
                    slurp
                    (json/parse-string true))
        message   (str (-> rq
                         :message))
        model     (extract-gpt-version (str (-> rq
                                              :model)))
        count    (token/count-tokens model message)]
    (println "message" message "model" model "count" count)
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body   (str count)}))


(defn oai [request]
  (let [rq        (-> request
                    :body
                    slurp
                    (json/parse-string true))
        messages  (-> rq
                    :documents)
        passphrase (-> rq
                      :passphrase)
        {:keys [model
                max-tokens
                temperature]} (-> rq
                                :settings)
        res (if (= passphrase pass-key)
              (api/create-chat-completion
                {:model model
                 :messages messages
                 :temperature temperature
                 :max_tokens max-tokens
                 :top_p 1
                 :frequency_penalty 0
                 :presence_penalty 0}
                {:api-key oai-key})
              "Nice try!")]
   {:status 200
    :headers {"Content-Type" "text/plain"}
    :body   (-> res
              :choices
              first
              :message
              :content)}))



#_(comment
    ;;Returns
    {:id "chatcmpl-8NiFe8FGpUIGLGZXETzr0VuMcz2ZN",
     :object "chat.completion",
     :created 1700662338,
     :model "gpt-4-1106-preview",
     :choices
     [{:index 0,
       :message
       {:role "assistant",
        :content
        "Hello! As an AI, I'm here to assist and provide you with information, help answer your questions, and engage in conversation about a wide range of topics. How can I assist you today?"},
       :finish_reason "stop"}],
     :usage {:prompt_tokens 14, :completion_tokens 40, :total_tokens 54},
     :system_fingerprint "fp_a24b4d720c"})


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
  (POST "/chat-complete" request (cors-headers (oai request)))
  (OPTIONS "/count-tokens" [] handle-preflight)
  (POST "/count-tokens" request (cors-headers (count-tokens request))))

(def app
  (-> app-routes
    (wrap-params)))

(defn -main [& args]
  (println "Starting server")
  (jetty/run-jetty app {:port 8080}))
