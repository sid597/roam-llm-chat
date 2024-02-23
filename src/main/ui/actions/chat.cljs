(ns ui.actions.chat
  (:require
    [applied-science.js-interop :as j]
    [reagent.core :as r]
    [ui.utils :refer [count-tokens-api q p pp get-parent-parent extract-from-code-block call-openai-api log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [ui.extract-data.chat :as ed :refer [data-for-pages]]
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]))


(defn send-context-and-message [message-atom block-uid active? settings token-count-atom]
  (p "*load context* send message to llm for uid: " block-uid)
  (let [pre            "*load context* :"
        message-block (get-child-with-str block-uid "Messages")
        messages      (sort-by :order (:children message-block))
        message-by-role (r/atom [])
        m-uid         (:uid message-block)]
    (doall
      (for [msg messages]
        (let [msg-str (:string msg)]
          (p "---"(re-find #"Assistant: " msg-str))
          (if (some? (re-find #"Assistant: " msg-str))
            (swap! message-by-role conj {:role "assistant"
                                         :content (str (clojure.string/replace msg-str #"Assistant: " ""))})
            (swap! message-by-role conj {:role "user"
                                         :content (str (extract-from-code-block msg-str))})))))

    (p (str pre "Calling openai api, with settings :"))
    ;(pp settings)
    (p (str pre "and messages :"))
    ;(pp @message-by-role)
    (p (str pre "Counting tokens for message:"))
    (count-tokens-api {:message @message-by-role
                       :model (:model settings)
                       :token-count-atom token-count-atom
                       :block-uid block-uid})
    (p (str pre "Now sending message and wait for response ....."))
    (call-openai-api
      {:messages @message-by-role
       :settings settings
       :callback (fn [response]
                   (println "received response from llm")
                   (p (str pre "openai api response received:"))
                   ;(pp response)
                   (let [res-str (-> response
                                   :body)]
                     (create-new-block m-uid "last" (str "Assistant: " res-str) (js/setTimeout
                                                                                  (fn []
                                                                                    #_(println "new block in messages")
                                                                                    (p (str pre "Update token count, after llm response"))
                                                                                    (count-tokens-api {:message res-str
                                                                                                       :model (:model settings)
                                                                                                       :update? true
                                                                                                       :block-uid block-uid
                                                                                                       :token-count-atom token-count-atom})
                                                                                    (p (str pre "Add assistant response block in messages: " m-uid))
                                                                                    (reset! message-atom (get-child-with-str block-uid "Messages"))
                                                                                    (reset! active? false))
                                                                                  500))))})))


(defn load-context [chat-atom messages-atom parent-id active? get-linked-refs? settings token-count-atom]
  #_(println "load context ")
  ;(pprint context)
  (p "*load context* for block with uid:" parent-id)
  (let [pre      "*load context* :"
        messages (get-child-with-str parent-id "Messages")
        chat     (get-child-with-str parent-id "Chat")
        context  (get-child-with-str parent-id "Context")
        con-uid  (:uid context)
        m-len    (count (:children messages))
        m-uid    (:uid messages)
        children (:children chat)
        c-uid    (:uid chat)
        count    (count children)]
    (p (str pre "for these: "))
    ;(pp children)
    (go
      (doseq [child children]
        ^{:key child}
        (let [child-uid (:uid child)
              cstr (:string child)
              order (+ m-len (:order child))]
          (do
            #_(println "order ------>" order)
            (cond
              (or (= "{{query block}}"
                    cstr)
                (= "{{ query block }}"
                  cstr))                (<p! (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuery] child-uid)
                                               (.then (fn [r]
                                                        (p (str pre "This is query block: " cstr))
                                                        (let [res (js->clj r :keywordize-keys true)
                                                              page-data (str
                                                                          "```"
                                                                          (clojure.string/join "\n -----" (data-for-pages res get-linked-refs?))
                                                                          "```")]
                                                          (p (str pre "extracted data from query pages:"))
                                                          ;(pp page-data)
                                                          (create-new-block
                                                            m-uid
                                                            order
                                                            page-data
                                                            #())
                                                          (update-block-string-and-move
                                                            child-uid
                                                            cstr
                                                            con-uid
                                                            "last"))))))
              (some? (is-a-page? cstr)) (<p!
                                          (do
                                           (p (str pre "This is a page: " cstr))
                                           (let [page-data (str
                                                             "```"
                                                             (clojure.string/join "\n -----" (data-for-pages
                                                                                               [{:text (is-a-page? cstr)}]
                                                                                               get-linked-refs?))
                                                             "```")]
                                             (p (str pre "extracted data for the page:"))
                                             ;(pp page-data)
                                             (create-new-block
                                               m-uid
                                               order
                                               page-data
                                               #())
                                             (update-block-string-and-move
                                               child-uid
                                               cstr
                                               con-uid
                                               "last"))))
              :else                     (<p!
                                          (do
                                            (p (str pre "This is a normal block: " cstr))
                                            (move-block
                                              m-uid
                                              order
                                              child-uid)))))))

      (<p! (create-new-block c-uid "first" "" ()))
      (<p! (js/Promise. (fn [_]
                          (p (str pre "refresh messages window with parent-id: " parent-id))
                          (reset! messages-atom (get-child-with-str parent-id "Messages"))
                          #_(println "messages atom reset")
                          (send-context-and-message messages-atom parent-id active? settings token-count-atom))))
      (<p! (js/Promise. (fn [_]
                          (p (str pre "refresh chat window with parent-id: " parent-id))
                          (reset! chat-atom (get-child-with-str parent-id "Chat"))))))))
