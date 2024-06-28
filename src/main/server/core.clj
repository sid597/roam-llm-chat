(ns server.core
  (:gen-class)
  (:require [compojure.core :refer :all]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.core.async :as async :refer [<! >! <!! >!! thread promise-chan go]]
            [wkok.openai-clojure.api :as api]
            [cheshire.core :as json]
            [tolkien.core :as token]
            [clj-http.client :as client]
            [server.env :as env :refer [oai-key pass-key pinecone-dg-index-host anthropic-key gemini-key pinecone-dg-nodes-key]]
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
                temperature
                tools
                tool_choice]} settings]

    {:model model
     :messages messages
     :tools tools
     :tool_choice tool_choice
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
        body    (json/generate-string
                   {:model      model
                    :messages   messages
                    :temperature temperature
                    :max_tokens max-tokens})
        headers {"Content-Type" "application/json"
                 "Authorization" (str "Bearer " oai-key)}
        url "https://api.openai.com/v1/chat/completions"
        res (client/post url
              {:headers headers
               :body body
               :content-type :json
               :as :json
               :throw-exceptions false})
        res-body (-> res :body)
        reply-body (cond
                     (not= 200 (:status res))
                     (str "^^ Error: code: " (:status res)
                       " and message: "
                       (-> res-body (json/parse-string-strict true) :error :message)
                       " ^^")

                     :else
                     (-> res-body
                       :choices
                       first
                       :message
                       :content))]
    (println "reply body: " reply-body)
    {:status (:status res)
     :headers {"Content-Type" "text/plain"}
     :body   reply-body}))

(comment
  (client/post
    "https://api.openai.com/v1/chat/completions"
    {:headers {"Content-Type" "application/json"
               "Authorization" (str "Bearer " oai-key)}
     :body (json/generate-string
             {:model    "gpt-3.5-turbo"
              :messages [{:rolre "system" :content "You are a helpful assistant."}
                         {:role "user" :content "Who won the world series in 2020?"}
                         {:role "assistant" :content "The Los Angeles Dodgers won the World Series in 2020."}
                         {:role "user" :content "Where was it played?"}]})
     :as :json
     :throw-exceptions false}))



(defn chat-anthropic [request]
  (println "chat anthropic")
  (let [{:keys
         [model
          messages
          temperature
          max-tokens
          tools
          tool_choice]} (extract-request request)
        api-key        anthropic-key
        url            "https://api.anthropic.com/v1/messages"
        headers        {"x-api-key"         api-key
                        "anthropic-version" "2023-06-01"
                        "Content-Type"      "application/json"}
        body      (json/generate-string{:model      model
                                        :max_tokens max-tokens
                                        :messages   messages
                                        :tools tools
                                        :tool_choice tool_choice
                                        :temperature temperature})

        _ (println "body" {:tools (json/generate-string tools)
                           #_#_:tool_choice (json/generate-string tool_choice)})
        response       (client/post url
                         {:headers headers
                          :body    body
                          :content-type :json
                          :as :json
                          :throw-exceptions false})
        res-body       (-> response :body)
        _ (println "response" res-body)
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
    {:status (:status response)
     :headers {"Content-Type" "text/plain"}
     :body   reply-body}))


(defn chat-gemini [request]
  (println "chat gemini")
  (let [{:keys [model
                settings
                temperature
                max-tokens
                messages]} (extract-request request)
        _ (println model)
        mod     (if (= "gemini" model)
                  "gemini-1.5-flash"
                  model)
        api-key  gemini-key
        url      (str
                   "https://generativelanguage.googleapis.com/v1/models/"
                   mod
                   ":generateContent?key="
                   api-key)
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

(defn get-openai-embedding-for [input]
  (let [body       (json/generate-string
                     {:model "text-embedding-3-small"
                      :input input})
        headers    {"Content-Type" "application/json"
                    "Authorization" (str "Bearer " oai-key)}
        url        "https://api.openai.com/v1/embeddings"]
    (client/post url
      {:headers headers
       :body body
       :content-type :json
       :as :json
       :throw-exceptions false})))


(defn merge-embeddings-with-metadata [embeddings uids strings]
  (vec (map
         (fn [e u s]
           {:id u
            :values (:embedding e)
            :metadata {:title s}})
         embeddings
         uids
         strings)))

(defn get-openai-embedding-from [request]
  (go
    (let [input  (-> request
                   :body
                   slurp
                   (json/parse-string true)
                   :input)
          result-chan (promise-chan)
          inp-strs    (map #(:string %) input)
          inp-uids    (map #(:uid %) input)
          res        (<! (thread (get-openai-embedding-for inp-strs)))
          _          (println "RES FROM OPEN AI: ")
          res-body   (:body res)
          reply-body (cond
                       (not= 200
                         (:status res)) (str "^^ Error: code: " (:status res)
                                          " and message: "
                                          (-> res-body (json/parse-string-strict true) :error :message)
                                          " ^^")
                       :else            (merge-embeddings-with-metadata (-> res-body :data) inp-uids inp-strs))]
        ;(println "embeddings reply body: " reply-body)
        (>!! result-chan {:status (:status res)
                          :headers {"Content-Type" "application/json"}
                          :body   (json/generate-string reply-body)})
        (<!! result-chan))))


(defn upsert-single [request]
  (go
   (let [embedding-res  (-> (<! (thread (get-openai-embedding-for ["Hello"])))
                          :body
                          :data
                          first
                          :embedding)
         result-chan (promise-chan)
         _ (println "embeddings res" embedding-res)
         vectors        [{:id     "1"
                          :values embedding-res}]
         body           (json/generate-string
                          {:vectors vectors})
         headers    {"Content-Type" "application/json"
                     "Api-Key" (str pinecone-dg-nodes-key)}
         url        (str  pinecone-dg-index-host "/vectors/upsert")

         res        (client/post url
                      {:headers headers
                       :body body
                       :content-type :json
                       :as :json
                       :throw-exceptions false})
         _ (println "RES -" res)
         res-body   (-> res :body)
         reply-body (cond
                      (not= 200 (:status res))
                      (str "^^ Error: code: " (:status res)
                        " and message: "
                        (-> res-body (json/parse-string-strict true) :error :message)
                        " ^^")
                      :else (json/generate-string (str res-body)))]
     (println "embeddings reply body: " reply-body)
     (>!! result-chan {:status (:status res)
                       :headers {"Content-Type" "application/json"}
                       :body   reply-body})
     (<!! result-chan))))


(defn upsert-all [request]
  (let [rq-map (-> request
                 :body
                 slurp
                 (json/parse-string true))
        result-chan (promise-chan)]
    (go
      (let [input          (:input rq-map)
            inp-strs       (map #(:title %) input)
            inp-uids       (map #(:uid %) input)
            embed-res      (<! (thread (get-openai-embedding-for inp-strs)))
            embeddings     (-> embed-res :body :data)
            all-embeddings (merge-embeddings-with-metadata embeddings inp-uids inp-strs)
            _ (println "UPLOADING " (count all-embeddings) " EMBEDDINGS")
            body           (json/generate-string
                             {:vectors all-embeddings}
                             {:pretty true})
            headers        {"content-type" "application/json"
                            "Api-Key" (str pinecone-dg-nodes-key)}
            url            (str  pinecone-dg-index-host "/vectors/upsert")
            _ (println "SENDING REQUEST ")
            upsert-res     (<! (thread (client/post url
                                         {:headers headers
                                          :body body
                                          :content-type :json
                                          :as :json
                                          :throw-exceptions false})))

            res-body       (:body upsert-res)
            _ (println "UPSERTED: "(-> res-body
                                     :upsertedCount) "==" (:status upsert-res))
            reply-body     (cond
                             (not= 200 (:status upsert-res))
                             (str "^^ Error: code: " (:status upsert-res)
                               " and message: "
                               (-> res-body (json/parse-string-strict true) :error :message)
                               " ^^")
                             :else res-body)]
        (println "embeddings reply body: " reply-body)
        (>!! result-chan {:status (:status upsert-res)
                          :headers {"Content-Type" "application/json"}
                          :body (json/generate-string reply-body)})))
    (<!! result-chan)))



(defn query-single-embedding [request]
  (let [rq-map (-> request
                 :body
                 slurp
                 (json/parse-string true))
        result-chan (promise-chan)]
    (go
      (let [inp-str          (:input rq-map)
            top-k            (:top-k rq-map)
            vector-embedding (-> (<! (thread (get-openai-embedding-for inp-str)))
                               :body :data first :embedding)
            _ (println "--" vector-embedding)
            body           (json/generate-string
                             {:vector          vector-embedding
                              :topK            top-k
                              :includeMetadata true}
                             {:pretty true})
            _ (println "BODY --" body)
            headers        {"content-type" "application/json"
                            "Api-Key" (str pinecone-dg-nodes-key)}
            url            (str  pinecone-dg-index-host "/query")
            query-res     (<! (thread (client/post url
                                        {:headers headers
                                         :body body
                                         :content-type :json
                                         :as :json
                                         :throw-exceptions false})))

            res-body       (:body query-res)
            _ (println "QUERY RESULT: "(-> res-body
                                         :matches) "==" (:status query-res))
            reply-body     (cond
                             (not= 200 (:status query-res))
                             (str "^^ Error: code: " (:status query-res)
                               " and message: "
                               (-> res-body (json/parse-string-strict true) :error :message)
                               " ^^")
                             :else (:matches res-body))]
        (println "Query embeddings reply body: " reply-body)
        (>!! result-chan {:status (:status query-res)
                          :headers {"Content-Type" "application/json"}
                          :body (json/generate-string reply-body)})))
    (<!! result-chan)))

(defn get-query-res-for [vector-embedding top-k]
  (let [body           (json/generate-string
                         {:vector          vector-embedding
                          :topK            top-k
                          :includeMetadata true}
                         {:pretty true})
        ;_ (println "BODY --" body)
        headers        {"content-type" "application/json"
                        "Api-Key" (str pinecone-dg-nodes-key)}
        url            (str  pinecone-dg-index-host "/query")]
    (client/post url
      {:headers headers
       :body body
       :content-type :json
       :as :json
       :throw-exceptions false})))


(defn query-multiple-embeddings [request]
  (let [rq-map (-> request
                 :body
                 slurp
                 (json/parse-string true))
        result-chan (promise-chan)]
    (go
      (let [inp-strs         (:input rq-map)
            top-k            (:top-k rq-map)
            vector-embeddings (-> (<! (thread (get-openai-embedding-for inp-strs)))
                                :body :data)
            all-query-res     (atom [])
            _                 (doseq [embedding vector-embeddings]
                                (let [query-res (-> (<! (thread (get-query-res-for (-> embedding :embedding) top-k)))
                                                  :body
                                                  :matches)]
                                  (swap! all-query-res conj query-res)))
            ;_ (println "--" vector-embeddings)
            _ (println "QUERY RESULT: " @all-query-res)]
        (>!! result-chan {:status (:status all-query-res)
                          :headers {"Content-Type" "application/json"}
                          :body (json/generate-string @all-query-res)})))
    (<!! result-chan)))


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
  (OPTIONS "/get-openai-embeddings" [] handle-preflight)
  (POST "/get-openai-embeddings" request (cors-headers (query-multiple-embeddings request)))
  (OPTIONS "/count-tokens" [] handle-preflight)
  (POST "/count-tokens" request (cors-headers (count-tokens request))))


(def app
  (-> app-routes
    (wrap-params)))

(defn -main [& args]
  (println "Starting server")
  (jetty/run-jetty app {:port 3000}))
