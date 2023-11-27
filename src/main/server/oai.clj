(ns server.oai
  (:require [wkok.openai-clojure.api :as api]
            [ring.middleware.anti-forgery :as af :refer [wrap-anti-forgery]] ; <--- Recommended
            [clojure.core.async :as a]))

(defn call-openai-api [send-fn user-id messages]
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
    (a/go
      (loop []
        (let [event (a/<! events)]
          (when (not= :done event)
            ;; Send the event token to the client
            (send-fn user-id [:openai-event event])
            (recur)))))))

#_(call-openai-api [{:role "system" :content "You are a helpful assistant."}
                    {:role "user" :content "Who won the world series in 2020?"}
                    {:role "assistant" :content "The Los Angeles Dodgers won the World Series in 2020."}
                    {:role "user" :content "Where was it played?"}])

#_(api/create-chat-completion {:model "gpt-3.5-turbo"
                               :messages [{:role "system" :content "You are a helpful assistant."}
                                          {:role "user" :content "Who won the world series in 2020?"}
                                          {:role "assistant" :content "The Los Angeles Dodgers won the World Series in 2020."}
                                          {:role "user" :content "Where was it played?"}]}
                              #_{:api-key "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"})
#_(api/create-chat-completion {:model "gpt-3.5-turbo"
                               :messages [{:role "system" :content "You are a helpful assistant."}
                                          {:role "user" :content "Who won the world series in 2020?"}
                                          {:role "assistant" :content "The Los Angeles Dodgers won the World Series in 2020."}
                                          {:role "user" :content "Where was it played?"}]
                               :stream true
                               :on-next #(prn %)}
    {:api-key "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"})


