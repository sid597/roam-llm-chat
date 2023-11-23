(ns ui.render-comp
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            ["openai" :as oai :refer [OpenAI]]
            [ui.extract-data :as ed :refer [data-for-pages q]]
            [reagent.dom :as rd]))





#_(ns ui.core
    (:require [reagent.core :as r]
              [applied-science.js-interop :as j]
              [roam.datascript.reactive :as dr]
              [roam.datascript :as d]
              [clojure.pprint :as pp :refer [pprint]]
              [blueprintjs.core :as bp :refer [Button InputGroup Card]]
              [roam.ui.main-window :as cmp]
              [reagent.dom :as rd]))

(defn log
 [& args]  (apply js/console.log args))


(defn get-child-with-str [block-uid s]
  (ffirst (q '[:find (pull ?c [:block/string :block/uid {:block/children ...}])
               :in $ ?uid ?s
               :where
               [?e :block/uid ?uid]
               [?e :block/children ?c]
               [?c :block/string ?s]]
            block-uid
            s)))

(defn chat-context [uid]
  (let [context-ref (r/atom nil)
        chat-loaded (r/atom nil)
        update-fn   (fn []
                      (when-let [context-el @context-ref]
                        (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                              (clj->js {:uid uid
                                        :zoom-path true
                                        :el context-el}))
                          (.then (fn [_])))))]

    (r/create-class
      {:component-did-mount  update-fn
       :component-did-update update-fn
       :reagent-render
       (fn []
         [:div.chat-loader
          {:ref (fn [el] (reset! context-ref el))
           :style {:flex "1 1 auto"
                   :height "100%"
                   :overflow "auto"
                   :display "flex"
                   :align-items "flex-start"
                   :max-height "700px"}}])})))

(defn chat-history [messages]
  (let [history-ref (r/atom nil)
        update-fn   (fn [this]
                      (when-let [hist-el @history-ref]
                        (set! (.-innerHTML hist-el ) "")
                        (doall
                          (for [child (reverse (:children @messages))]
                            ^{:key child}
                            (let [uid (:uid child)
                                  msg-block-div (.createElement js/document (str "div.msg-" uid))]
                              (do
                                (log "chat-history ref" hist-el)
                                (println "child ---->" child)
                                (if (.hasChildNodes hist-el)
                                  (.insertBefore hist-el msg-block-div (.-firstChild hist-el))
                                  (.appendChild hist-el msg-block-div (.-firstChild hist-el)))
                                (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                      (clj->js {:uid uid
                                                :zoom-path true
                                                :el msg-block-div}))
                                  (.then (fn [_])))))))
                        (.scrollTo hist-el 0 (.-scrollHeight hist-el))))]

    (r/create-class
      {:component-did-update update-fn
       :component-did-mount  update-fn
       :reagent-render       (fn []
                               (let [msgs @messages
                                     id (random-uuid)]
                                 [:div
                                  {:ref   (fn [el] (reset! history-ref el))
                                   :class (str "chat-history-" id)
                                   :style {:flex "1"
                                           :overflow-y "auto"
                                           :margin "10px"
                                           :min-height "300px"
                                           :max-height "700px"
                                           :background "aliceblue"}}]))})))



(defn load-context [context messages]
  (println "load context ")
  (let [children      (:children context)
        m-len         (count @messages)
        m-uid         (:uid @messages)]
      (js/Promise.
        (fn [resolve _]
          (let [promises (doall
                          (for [child children]
                            ^{:key child}
                            (let [child-uid (:uid child)
                                  cstr (:string child)
                                  ctr  (atom m-len)]
                              (do
                                #_(println "child ---->" (= "{{ query block }}"
                                                           cstr) child-uid)
                                (cond
                                  (= "{{ query block }}"
                                    cstr)                 (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuery] child-uid)
                                                            (.then (fn [r]
                                                                     (let [res (js->clj r :keywordize-keys true)
                                                                           page-data (data-for-pages res)]
                                                                       (doall
                                                                        (for [dg-node page-data]
                                                                          (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
                                                                                (clj->js {:location {:parent-uid (str m-uid)
                                                                                                     :order       -1}
                                                                                          :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                                                                                     :string (str "``` " dg-node "```")
                                                                                                     :open   true}}))
                                                                            (.then (fn [_]
                                                                                     #_(println "new block in messages" page-data))))))))))


                                  :else                  (do
                                                           (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
                                                                 (clj->js {:location {:parent-uid (str m-uid)
                                                                                      :order       -1}
                                                                           :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                                                                      :string (str cstr)
                                                                                      :open   true}}))
                                                             (.then (fn []
                                                                      #_(println "new block in messages"))))
                                                           (swap! ctr inc)))))))]
            (-> (js/Promise.all promises)
              (.then (fn [_]
                       (println "all promises resolved")
                       (resolve nil)))))))))

(defn call-openai-api [messages callback]
  (let [client (OpenAI. #js {:apiKey "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"
                             :dangerouslyAllowBrowser true})
        response (j/call-in client  [:chat :completions :create]
                   (clj->js {:model "gpt-4-1106-preview"
                             :messages (clj->js messages)
                             :temperature 1
                             :max_tokens 512
                             :top_p 1
                             :frequency_penalty 0
                             :presence_penalty 0}))]

    (comment
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
    (-> response
      (.then callback)
      (.catch (fn [error] (println "Error:" error))))))

#_(call-openai-api [{:role "user"
                     :content "Hello, I'm a human."}]
    (fn [response]
      (pprint (-> (js->clj response :keywordize-keys true)
                :choices
                first
                :message
                :content))))


(defn extract-from-code-block [s]
  (let [pattern #"(?s)```javascript\n \n(.*?)\n```"
        m (re-find pattern s)]
    (if m (second m) s)))

(comment
  (def text "```javascript\n \n{:title [[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis., :body --\n title: [[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.\n url: https://roamresearch.com/#/app/akamatsulab/page/0PAm1s8St\n author: Heonsu Kim\n date: Fri Apr 28 2023 12:30:01 GMT-0700 (Pacific Daylight Time)\n --\n Source of Claim\n Notes\n Discourse Context\n **Supported By::** [[[[EVD]] - About 30 DNM2 molecules were required for forming the encytosis in SK-MEL-2 cells.  - [[@grassart2014actin]]]], :refs [October 21st, 2023 > #Import > [[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis. [[EVD]] - About 30 DNM2 molecules were required for forming the encytosis in SK-MEL-2 cells.  - [[@grassart2014actin]] >  > [[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis. [[QUE]] - What are the phenotypes of knocking down dynamin2? > Related [[QUE]] [[CLM]] [[EVD]] > [[SupportedBy]]\n [[[[EVD]] - About 30 DNM2 molecules were required for forming the encytosis in SK-MEL-2 cells.  - [[@grassart2014actin]]]][[CLM]] - Enough number of DNM2 molecules is important for performing endocytosis.]}\n```")
  (pprint (extract-from-code-block text)))

(defn send-context-and-message [message-block reset-atom block-uid]
  (let [res (atom "")
        messages (:children message-block)
        m-uid    (:uid message-block)]

    (doall
     (for [msg messages]
       (let [msg-str (:string msg)]
         (swap! res str (extract-from-code-block msg-str)))))
    (call-openai-api [{:role "user"
                       :content @res}]
      (fn [response]
        (let [res-str (-> (js->clj response :keywordize-keys true)
                        :choices
                        first
                        :message
                        :content)]
          (-> (j/call-in js/window [:roamAlphaAPI :data :block :create]
                (clj->js {:location {:parent-uid (str m-uid)
                                     :order       -1}
                          :block    {:uid    (j/call-in js/window [:roamAlphaAPI :util :generateUID])
                                     :string res-str
                                     :open   true}}))
            (.then (fn [_]
                     (js/setTimeout
                       (fn []
                        (println "new block in messages")
                        (reset! reset-atom (get-child-with-str block-uid "Messages")))
                       500)))))))))


#_(send-context-and-message "kOxujvJLm")

(defn chat-ui [block-uid]
  (println "block uid for chat" block-uid)
  (let [settings (get-child-with-str block-uid "Settings")
        context  (get-child-with-str block-uid "Context")
        messages (r/atom (get-child-with-str block-uid "Messages"))
        c-uid    (:uid context)]

   (fn [_]
     (let [msg @messages]
       [:div.chat-container
        {:style {:display "flex"
                 :flex-direction "column"
                 :border-radius "8px"
                 :overflow "hidden"}}
        [:> Card {:interactive true
                  :elevation 3
                  :style {:flex "1"
                          :margin "0"
                          :display "flex"
                          :flex-direction "column"
                          :border "2px solid rgba(0, 0, 0, 0.2)"
                          :border-radius "8px"}}
         [chat-history messages]
         [:div.chat-input-container
          {:style {:display "flex"
                   :align-items "center"
                   :border "1px"
                   :padding "10px"}}
          [chat-context c-uid]
          [:> Button {:icon "arrow-right"
                      :intent "primary"
                      :large true
                      :on-click (fn []
                                  (-> (load-context context messages)
                                    (.then (fn []
                                             (js/setTimeout
                                               (fn []
                                                 (let [new-msgs (get-child-with-str block-uid "Messages")]
                                                   (do
                                                     (reset! messages new-msgs)
                                                     (send-context-and-message new-msgs messages block-uid))))
                                               900)))))
                      :style {:margin-left "10px"}}]]]]))))




(defn main [{:keys [:block-uid]} & args]
  (println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    (println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))

