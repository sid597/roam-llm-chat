(ns ui.actions.chat
  (:require
    [applied-science.js-interop :as j]
    [reagent.core :as r]
    [ui.utils :refer [count-tokens-api q p pp get-parent-parent extract-from-code-block call-openai-api log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
    [cljs.core.async.interop :as asy :refer [<p!]]
    [ui.extract-data.chat :as ed :refer [data-for-pages]]
    [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]))


(defn send-context-and-message [message-atom block-uid active? settings token-count-atom context]
  (p "*load context* send message to llm for uid: " block-uid)
  (let [pre "*load context* :"
        message-block (get-child-with-str block-uid "Messages")
        messages (sort-by :order (:children message-block))
        message-by-role (r/atom [])
        m-uid (:uid message-block)]
    (doall
      (for [msg messages]
        (let [msg-str (:string msg)]
          (p  (re-find #"Assistant?: " msg-str))
          (if (some? (re-find #"Assistant: " msg-str))
            (swap! message-by-role conj {:role    "assistant"
                                         :content (str (clojure.string/replace msg-str #"Assistant: " ""))})
            (swap! message-by-role conj {:role    "user"
                                         :content (str (extract-from-code-block msg-str))})))))

    (p (str pre "append chat messages to context messages"))
    (reset! message-by-role (vec (concat context @message-by-role)))

    (p (str pre "Calling openai api, with settings : " settings))
    (p (str pre "and messages : " @message-by-role))
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
                   (p (str pre "openai api response received: " response))
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

(defn extract-context [children pre get-linked-refs?]
  (p (str pre " Extracting context data"))
  (let [pre (str pre " Extracting context data: ")
        ext (r/atom "\n Initial context: \n")]
    (doseq [child children]
      (let [cstr (:string child)
            child-uid (:uid child)]
        (p (str pre " == " cstr))
        (cond
          (or (= "{{query block}}"
                cstr)
            (= "{{ query block }}"
              cstr))                (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuery] child-uid)
                                      (.then (fn [r]
                                               (p (str pre "This is query block: " cstr))
                                               (let [res (js->clj r :keywordize-keys true)
                                                     page-data (clojure.string/join "\n "(data-for-pages res get-linked-refs?))]
                                                 (p (str pre "extracted data from query pages: " page-data))
                                                 (swap! ext str "\n " page-data)))))
          (some? (is-a-page? cstr)) (do
                                      (p (str pre "This is a page: " cstr)
                                        (let [page-data (clojure.string/join "\n " (data-for-pages
                                                                                     [{:text (is-a-page? cstr)}]
                                                                                     get-linked-refs?))]
                                          (p (str pre "extracted data for the page: " page-data))
                                          (swap! ext str "\n " page-data))))
          :else                     (do
                                      (p (str pre "This is a normal block: " cstr))
                                      (swap! ext str "\n " cstr)))))
    (str "``` " @ext "\n ```")))

(comment
  (extract-context (:children (get-child-with-str "Yif1J5lmJ" "Context"))
    "gm: "
    (r/atom false))

  (send-context-and-message nil  "Yif1J5lmJ" nil nil nil [{:role "user"
                                                           :content (extract-context (:children (get-child-with-str "Yif1J5lmJ" "Context"))
                                                                        "gm: "
                                                                        (r/atom false))}]))


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
        count    (count children)
        context-str [{:role    "user"
                      :content (extract-context (:children context) pre get-linked-refs?)}]]


    (p (str pre "for these: " children))
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
                                                        (p (str pre "Query result are: " r))
                                                        (let [res (js->clj r :keywordize-keys true)
                                                              page-data (str
                                                                          "```"
                                                                          (clojure.string/join "\n -----" (data-for-pages res get-linked-refs?))
                                                                          "```")]
                                                          (p (str pre "extracted data from query pages: " page-data))
                                                          (update-block-string-and-move
                                                            child-uid
                                                            page-data
                                                            m-uid
                                                            order))))))


              (some? (is-a-page? cstr)) (<p!
                                          (do
                                            (p (str pre "This is a page: " cstr))
                                            (let [page-data (str
                                                              "```"
                                                              (clojure.string/join "\n -----" (data-for-pages
                                                                                                [{:text (is-a-page? cstr)}]
                                                                                                get-linked-refs?))
                                                              "```")]
                                              (p (str pre "extracted data for the page: " page-data))
                                              (update-block-string-and-move
                                                child-uid
                                                page-data
                                                m-uid
                                                order))))
              :else                     (<p!
                                          (do
                                            (p (str pre "This is a normal block: " cstr))
                                            (move-block
                                              m-uid
                                              order
                                              child-uid))))))))

    (go
      (<p! (create-new-block c-uid "first" "" ()))
      (<p! (js/Promise. (fn [_]
                          (p (str pre "refresh messages window with parent-id: " parent-id))
                          (reset! messages-atom (get-child-with-str parent-id "Messages"))
                          #_(println "messages atom reset")
                          (send-context-and-message messages-atom parent-id active? settings token-count-atom context-str))))
      (<p! (js/Promise. (fn [_]
                          (p (str pre "refresh chat window with parent-id: " parent-id))
                          (reset! chat-atom (get-child-with-str parent-id "Chat"))))))))


