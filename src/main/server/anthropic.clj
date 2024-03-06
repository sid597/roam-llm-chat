(ns server.anthropic
  (:require [clojure.data.json :as json]
            [cheshire.core :as json]
            [martian.core :as martian]
            [martian.test :as test]
            [clj-http.client :as client]
            [schema.core :as s]
            [server.env :refer [anthropic-key]]))

(let [api-key anthropic-key ;; Replace with your actual Anthropic API key
      url     "https://api.anthropic.com/v1/messages"
      headers {"x-api-key" api-key
               "anthropic-version" "2023-06-01"
               "Content-Type" "application/json"}
      body    {:model "claude-3-opus-20240229"
               :max_tokens 1024
               :messages [{:role "user", :content "Hello, world"}]}
      response (client/post url
                 {:headers headers
                  :body (json/write-str body)
                  :content-type :json
                  :as :json})]
  (:body response))



(def text-content-schema
  {:type (s/eq "text")
   :text s/Str})

(def image-content-schema
  {:type (s/eq "image")
   :source {:type (s/eq "base64")
            :media_type (s/enum "image/jpeg" "image/png" "image/gif" "image/webp")
            :data s/Str}})

(def message-content-schema
  (s/conditional
    string?        s/Str
    #(and (map? %) (= (:type %) "text")) text-content-schema
    #(and (map? %) (= (:type %) "image")) image-content-schema
    :else          s/Any))


(def message-schema
  {:role    (s/enum "user" "assistant")
   :content message-content-schema})


(def message-request-schema
  {:model                           s/Str
   :max-tokens                      java.lang.Long
   :messages                        [message-schema]
   (s/optional-key :stop-sequences) [s/Str]
   (s/optional-key :system)         s/Str
   (s/optional-key :stream)         s/Bool
   (s/optional-key :temperature)    s/Num
   (s/optional-key :top-p)          s/Num
   (s/optional-key :top-k)          s/Int})

(def message-response-schema
  {:id            s/Str
   :type          (s/eq "message")
   :role          (s/eq "assistant")
   :content       [message-content-schema]
   :model         s/Str
   :stop_reason   (s/enum "end_turn" "max_tokens" "stop_sequence")
   :stop-sequence (s/maybe s/Str)
   :usage         {:input-tokens s/Int, :output-tokens s/Int}})


(def default-headers
  {:x-api-key         anthropic-key
   :anthropic-version "2023-06-01"
   :content-type      "application/json"})


(defn bootstrap-anthropic-api []
  (martian/bootstrap "https://api.anthropic.com"
    [{:route-name       :create-message
      :path-parts       ["/v1/messages"]
      :method           :post
      :body-schema      message-request-schema
      :response-schema  message-response-schema
      :headers-schema   {:x-api-key s/Str
                         :anthropic-version s/Str
                         :content-type (s/eq "application/json")}}]
    {:interceptors [{:name ::add-new-headers
                     :enter (fn [context]
                              (do
                               (assoc-in context [:request :headers :x-api-key] anthropic-key)
                               (assoc-in context [:request :headers :anthropic-version] "2023-06-01")
                               (assoc-in context [:request :headers :content-type] "application/json"))
                              #_(update-in context [:request :headers] merge default-headers))}]}))

(def request-body
  {:model       "claude-v1"
   :max-tokens  100
   :messages    [{:role "user" :content "Hello, how are you?"}]
   :temperature 0.7})

(martian/response-for (bootstrap-anthropic-api) :create-message {:max-tokens 100000})


(def api (bootstrap-anthropic-api))


(def dummy-data {:model       "claude-3-sonnet-20240229"
                  :max-tokens  100
                  :messages    [{:role "user",
                                 :content [{:type "text"
                                            :text "Hello, world"}]}]
                  :temperature 0.7})


(comment
  (martian/explore api)
  (martian/explore api :create-message)
  (martian/url-for api :create-message dummy-data)
  (martian/request-for api :create-message dummy-data)
  (martian/response-for api :create-message dummy-data))



(defn -main []
  (let [api (bootstrap-anthropic-api)
        res (-> (martian/request-for api dummy-data)
                :body)]
    (println res)))

