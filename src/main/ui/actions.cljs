(ns ui.actions
 (:require
   [applied-science.js-interop :as j]
   [ui.utils :refer [q get-parent-parent extract-from-code-block call-openai-api log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
   [cljs.core.async.interop :as asy :refer [<p!]]
   [ui.extract-data :as ed :refer [data-for-pages]]
   [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]))


(defn send-context-and-message [message-atom block-uid active? settings]
  #_(println "send-context-and-message" block-uid)
  (let [res           (atom "")
        message-block (get-child-with-str block-uid "Messages")
        messages      (sort-by :order (:children message-block))
        m-uid         (:uid message-block)]
    (doall
      (for [msg messages]
        (let [msg-str (:string msg)]
          (swap! res str (extract-from-code-block msg-str)))))
    (call-openai-api
      {:messages [{:role "user"
                   :content @res}]
       :settings settings
       :callback (fn [response]
                   (let [res-str (-> response
                                   :body)]
                     (create-new-block m-uid "last" (str "Assistant: " res-str) (js/setTimeout
                                                                                  (fn []
                                                                                    #_(println "new block in messages")
                                                                                    (reset! message-atom (get-child-with-str block-uid "Messages"))
                                                                                    (reset! active? false))
                                                                                  500))))})))
(defn load-context [context-atom messages-atom parent-id active? get-linked-refs? settings]
  #_(println "load context ")
  ;(pprint context)
  (let [messages (get-child-with-str parent-id "Messages")
        context  (get-child-with-str parent-id "Context")
        m-len    (count (:children messages))
        m-uid    (:uid messages)
        children (:children context)
        c-uid    (:uid context)
        count    (count children)]
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
                                                        (let [res (js->clj r :keywordize-keys true)
                                                              page-data (str
                                                                          "```"
                                                                          (clojure.string/join "\n -----" (data-for-pages res get-linked-refs?))
                                                                          "```")]
                                                          (update-block-string-and-move
                                                            child-uid
                                                            page-data
                                                            m-uid
                                                            order))))))

              (some? (is-a-page? cstr)) (<p!
                                          (let [page-data (str
                                                            "```"
                                                            (clojure.string/join "\n -----" (data-for-pages
                                                                                              [{:text (is-a-page? cstr)}]
                                                                                              get-linked-refs?))
                                                            "```")]
                                            (update-block-string-and-move
                                              child-uid
                                              page-data
                                              m-uid
                                              order)))



              :else                     (<p!
                                          (move-block
                                            m-uid
                                            order
                                            child-uid))))))

      (<p! (create-new-block c-uid "first" "" ()))
      (<p! (js/Promise. (fn [_]
                          (reset! messages-atom (get-child-with-str parent-id "Messages"))
                          #_(println "messages atom reset")
                          (send-context-and-message messages-atom parent-id active? settings))))
      (<p! (js/Promise. (fn [_] (reset! context-atom (get-child-with-str parent-id "Context"))))))))
