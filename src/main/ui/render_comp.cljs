(ns ui.render-comp
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components :as comp :refer [send-message-component chin chat-context chat-history]]
            [cljs.core.async.interop :as asy :refer [<p!]]
            [ui.extract-data :as ed :refer [data-for-pages q is-a-page?]]
            [reagent.dom :as rd]))




(defn log
 [& args]  (apply js/console.log args))


(defn get-child-with-str [block-uid s]
  (ffirst (q '[:find (pull ?c [:block/string :block/uid :block/order {:block/children ...}])
               :in $ ?uid ?s
               :where
               [?e :block/uid ?uid]
               [?e :block/children ?c]
               [?c :block/string ?s]]
            block-uid
            s)))




(defn move-block [parent-uid order block-uid callback]
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :move]
        (clj->js {:location {:parent-uid parent-uid
                             :order       order}
                  :block    {:uid    block-uid}}))
    (.then (fn []
             callback
             #_(println "new block in messages")))))


(defn create-new-block [parent-uid order string callback]
  (println "create new block" parent-uid)
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
        (clj->js {:location {:parent-uid parent-uid
                             :order       order}
                  :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                             :string string
                             :open   true}}))
    (.then (fn []
             callback))))

(defn update-block-string-and-move [block-uid string parent-uid order callback]
  (-> (j/call-in js/window [:roamAlphaAPI :data :block :update]
        (clj->js {:block {:uid    block-uid
                          :string string}}))
    (.then (fn []
             (println "-- updated block string now moving --")
             (move-block parent-uid order block-uid callback)))))

(goog-define url-endpoint "")


(defn call-openai-api [{:keys [messages settings callback]}]
  (let [passphrase (j/get-in js/window [:localStorage :passphrase])
        url     "https://roam-llm-chat-falling-haze-86.fly.dev/chat-complete"
        data    (clj->js {:documents messages
                          :settings settings
                          :passphrase passphrase})
        headers {"Content-Type" "application/json"}
        res-ch  (http/post url {:with-credentials? false
                                :headers headers
                                :json-params data})]
    (take! res-ch callback)))


(defn extract-from-code-block [s]
  (let [pattern #"(?s)```javascript\n \n(.*?)\n```"
        m (re-find pattern s)]
    (if m
      (str (second m) " \n ")
      (str s " \n "))))


(defn send-context-and-message [message-atom block-uid active? settings]
  (println "send-context-and-message" block-uid)
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
                                                                                    (println "new block in messages")
                                                                                    (reset! message-atom (get-child-with-str block-uid "Messages"))
                                                                                    (reset! active? false))
                                                                                  500))))})))


(defn load-context [context-atom messages-atom parent-id active? get-linked-refs? settings]
  (println "load context ")
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
            (println "order ------>" order)
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
                                                              order
                                                              (println "updated and moved block")))))))
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
                                              order
                                              (println "extracted page data and replaced with it"))))


              :else                     (<p!
                                          (move-block
                                            m-uid
                                            order
                                            child-uid
                                            (println "moved block" child-uid)))))))
      (<p! (create-new-block c-uid "first" "" (println "new block created")))
      (<p! (js/Promise. (fn [_]
                            (reset! messages-atom (get-child-with-str parent-id "Messages"))
                            (println "messages atom reset")
                            (send-context-and-message messages-atom parent-id active? settings))))
      (<p! (js/Promise. (fn [_] (reset! context-atom (get-child-with-str parent-id "Context"))))))))


(defn get-parent-parent [uid]
  (ffirst (q '[:find  ?p
               :in $ ?uid
               :where [?e :block/uid ?uid]
               [?e :block/parents ?p1]
               [?p1 :block/string "Context"]
               [?p1 :block/parents ?p2]
               [?p2 :block/string "{{ chat-llm }}"]
               [?p2 :block/uid ?p]]
            uid)))


(defn chat-ui [block-uid]
  (println "block uid for chat" block-uid)
  (let [settings (get-child-with-str block-uid "Settings")
        context  (r/atom (get-child-with-str block-uid "Context"))
        messages (r/atom (get-child-with-str block-uid "Messages"))
        active? (r/atom false)
        default-msg-value (r/atom 400)
        default-temp (r/atom 0.9)
        default-model (r/atom "gpt-4-1106-preview")
        get-linked-refs (r/atom true)]
   (fn [_]
     (let [msg               @messages
           c-msg             (:children @context)
           callback          (fn [{:keys [b-uid] :or {b-uid block-uid}}]
                               (println "called callback to load context")
                               (when (not @active?)
                                 (do
                                   (println "---- clicked send button ----")
                                   (reset! active? true)
                                   (load-context context messages b-uid active? get-linked-refs {:model @default-model
                                                                                                 :max-tokens @default-msg-value
                                                                                                 :temperature @default-temp}))))
           handle-key-event  (fn [event]
                               (when (and (.-altKey event) (= "Enter" (.-key event)))
                                 (let [buid (-> (j/call-in js/window [:roamAlphaAPI :ui :getFocusedBlock])
                                              (j/get :block-uid))
                                       b-parent (get-parent-parent buid)]
                                  (callback b-parent))))]
       [:div.chat-container
        {:style {:display "flex"
                 :flex-direction "column"
                 :border-radius "8px"
                 :overflow "hidden"}}
        [:> Card {:elevation 3
                  :style {:flex "1"
                          :margin "0"
                          :display "flex"
                          :flex-direction "column"
                          :border "2px solid rgba(0, 0, 0, 0.2)"
                          :border-radius "8px"}}
          [chat-history messages]
         [:div.chat-input-container
          {:style {:display "flex"
                   :flex-direction "row"
                   :border-radius "8px"
                   :margin "10px 10px -10px 10px  "
                   :background-color "whitesmoke"
                   :border "1px"}}
          [chat-context context handle-key-event]]
         [chin default-model default-msg-value default-temp get-linked-refs active? callback]]]))))


(defn main [{:keys [:block-uid]} & args]
  (println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    (println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))
