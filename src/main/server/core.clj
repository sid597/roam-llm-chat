(ns server.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [wkok.openai-clojure.api :as api]
            [cheshire.core :as json]
            [tolkien.core :as token]
            [clj-http.client :as client]
            [server.env :as env :refer [oai-key pass-key anthropic-key gemini-key]]
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


(defn gemini-flavoured-messages [messages]
  (println " generate gemini-flavoured-messages")
  (mapv (fn [message]
          (let [role (:role message)
                content (:content message)]
            {:role (if (= "user" role)
                     role "model")
             :parts [{:text content}]}))
    messages))

(defn extract-request [request]
  (let [rq         (-> request
                     :body
                     slurp
                     (json/parse-string true))
        messages   (-> rq
                     :documents)
        passphrase (-> rq
                     :passphrase)
        settings   (-> rq
                     :settings)
        {:keys [model
                max-tokens
                temperature]} settings]

    {:model model
     :messages messages
     :passphrase passphrase
     :temperature temperature
     :max-tokens max-tokens
     :settings settings}))


(defn oai [request]
  (let [{:keys [model
                messages
                passphrase
                temperature
                max-tokens]} (extract-request request)
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


(defn chat-anthropic [request]
  (let [{:keys
         [model
          messages
          temperature
          max-tokens]} (extract-request request)
        api-key        anthropic-key
        url            "https://api.anthropic.com/v1/messages"
        headers        {"x-api-key"         api-key
                        "anthropic-version" "2023-06-01"
                        "Content-Type"      "application/json"}
        body           (json/generate-string
                         {:model      model
                          :max_tokens max-tokens
                          :messages   messages
                          :temperature temperature})
        response       (client/post url
                         {:headers headers
                          :body    body
                          :content-type :json
                          :as :json
                          :throw-exceptions false})
        res-body       (-> response :body)
        tokens         (-> res-body
                         :usage)
        input-token    (:input_tokens tokens)
        output-token   (:output_tokens tokens)
        total-token    (+ input-token output-token)
        _              (println "input-token" input-token "output-token" output-token "total-token" total-token)
        _              (println "status " (:status response) "--" (:error res-body) "--" (:stop_reason res-body) "--" (-> res-body
                                                                                                                        :content
                                                                                                                        first
                                                                                                                        :text))
        reply-body     (cond
                         (not= 200 (:status response)) (str "^^ Error: code: " (:status response)
                                                         " and message: "
                                                         (-> res-body (json/parse-string-strict true) :error :message)
                                                         " ^^")
                         #_#_(= "max_tokens"
                               (:stop_reason res-body))    (str "ERROR:  MAX TOKENS REACHED")
                         :else                         (-> res-body
                                                         :content
                                                         first
                                                         :text))]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body   reply-body}))


(defn chat-gemini [request]
  (println "chat gemini")
  (let [{:keys [settings
                temperature
                max-tokens
                messages]} (extract-request request)
        api-key  gemini-key
        url      (str "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.0-pro:generateContent?key=" api-key)
        headers  {"Content-Type" "application/json"}
        body     (json/generate-string
                   {:contents         (gemini-flavoured-messages messages)
                    :generationConfig {:maxOutputTokens max-tokens
                                       :temperature temperature}
                    :safetySettings   (:safety-settings settings)})
        response (client/post url {:headers headers
                                   :body body
                                   :content-type :json
                                   :as :json
                                   :throw-exceptions false})
        res-body (-> response :body)
        _ (println "status " (:status response) "--" (:error res-body) "--" (-> res-body :candidates first :content :parts first :text))
        reply-body (cond
                     (not= 200 (:status response))
                     (str "^^ ERROR: code: " (:status response) " and message: "
                       (-> res-body (json/parse-string-strict true) :error :message)
                       " ^^")
                     :else
                     (-> res-body :candidates first :content :parts first :text))]
    {:status (:status response)
     :headers {"Content-Type" "text/plain"}
     :body reply-body}))

(comment
  (-> (client/post
        (str "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.0-pro:generateContent?key=" gemini-key)
        {:headers {"Content-Type" "application/json"}
         :body (json/generate-string {:contents
                                      [{:role "user",
                                        :parts [{:texts "\n Initial context: \n\n  \n \npm \n "}]}]
                                      :generationConfig {:maxOutputTokens 400, :temperature 0.9},
                                      :safetySettings
                                      [{:category "HARM_CATEGORY_HARASSMENT",
                                        :threshold "BLOCK_MEDIUM_AND_ABOVE"}
                                       {:category "HARM_CATEGORY_HATE_SPEECH",
                                        :threshold "BLOCK_MEDIUM_AND_ABOVE"}
                                       {:category "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                                        :threshold "BLOCK_MEDIUM_AND_ABOVE"}
                                       {:category "HARM_CATEGORY_DANGEROUS_CONTENT",
                                        :threshold "BLOCK_MEDIUM_AND_ABOVE"}]})
         :content-type :json
         :as :json
         :throw-exceptions false})
    :body
    (json/parse-string-strict true)
    :error
    :message))


(defn- handle-preflight [f]
   {:status 200
    :headers {"Access-Control-Allow-Origin" "https://roamresearch.com"
              "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"
              "Access-Control-Allow-Headers" "Content-Type, Authorization"}})

(defn- cors-headers [response]
  (let [updated-response (update response :headers merge
                           {"Access-Control-Allow-Origin" "https://roamresearch.com"})]  ; Add CORS header
    updated-response))

(defroutes app-routes
  (OPTIONS "/chat-complete" [] handle-preflight)
  (POST "/chat-complete" request (cors-headers (oai request)))
  (OPTIONS "/chat-anthropic" [] handle-preflight)
  (POST "/chat-anthropic" request (cors-headers (chat-anthropic request)))
  (OPTIONS "/chat-gemini" [] handle-preflight)
  (POST "/chat-gemini" request (cors-headers (chat-gemini request)))
  (OPTIONS "/count-tokens" [] handle-preflight)
  (POST "/count-tokens" request (cors-headers (count-tokens request))))


(def app
  (-> app-routes
    (wrap-params)))

(defn -main [& args]
  (println "Starting server")
  (jetty/run-jetty app {:port 8080}))
