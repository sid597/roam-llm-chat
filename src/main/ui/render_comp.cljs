(ns ui.render-comp
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [cljs.core.async.interop :as asy :refer [<p!]]
            [ui.extract-data :as ed :refer [data-for-pages q]]
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

(defn chat-context [context]
  ;(println "2. load chat-content")
  (let [context-ref (r/atom nil)
        chat-loaded (r/atom nil)
        update-fn   (fn [this]
                      (when-let [context-el @context-ref]
                        ;(println "4. chat context update fn")
                        ;(pprint @context)
                        ;(set! (.-innerHTML context-el ) "")
                        (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                              (clj->js {:uid (:uid @context)
                                        :zoom-path true
                                        :el context-el}))
                          (.then
                            (fn [_]
                              (println "5. chat context block rendered successfully")))
                          (.catch (fn [e]
                                    (log "Error in chat context block" e))))))]
    (r/create-class
      {:component-did-mount  update-fn
       :component-did-update update-fn
       :reagent-render
       (fn []
         (let [cmsg (:children @context)]
           (println "3. chat context insdie component")
          [:div.chat-loader
           {:ref (fn [el] (reset! context-ref el))
            :style {:flex "1 1 auto"
                    :height "100%"
                    :overflow "auto"
                    :flex-direction "column"
                    :display "flex"
                    :align-items "stretch"
                    :background-color "whitesmoke"
                    :min-height "100px"
                    :border-radius "8px"
                    :max-height "700px"}}]))})))

(defn chat-history [messages]
  (println "load chat-history")
  ;(pprint (sort-by :order (:children @messages)))
  (let [history-ref (r/atom nil)
        update-fn   (fn [this]
                      (when-let [hist-el @history-ref]
                        (set! (.-innerHTML hist-el ) "")
                        (doall
                          (for [child (reverse (sort-by :order (:children @messages)))]
                            ^{:key child}
                            (let [uid (:uid child)
                                  msg-block-div (.createElement js/document (str "div.msg-" uid))]
                              (do
                                ;(log "chat-history ref" hist-el)
                                #_(println "chat histor child ---->" child)
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
                                           :border-radius "8px"
                                           :background "aliceblue"}}]))})))

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


(defn call-openai-api [{:keys [messages settings]} callback]
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
    (call-openai-api [{:messages {:role "user"
                                  :content @res}
                       :settings settings}]
      (fn [response]
        (let [res-str (-> response
                        :body)]
          (create-new-block m-uid "last" (str "Assistant: " res-str) (js/setTimeout
                                                                       (fn []
                                                                         (println "new block in messages")
                                                                         (reset! message-atom (get-child-with-str block-uid "Messages"))
                                                                         (reset! active? true))
                                                                       500)))))))


(defn load-context [context-atom messages-atom parent-id active? settings]
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
                  cstr))               (<p! (-> (j/call-in js/window [:roamjs :extension :queryBuilder :runQuery] child-uid)
                                                (.then (fn [r]
                                                         (let [res (js->clj r :keywordize-keys true)
                                                               page-data (str
                                                                           "```"
                                                                           (clojure.string/join "\n -----" (data-for-pages res))
                                                                           "```")]
                                                           (update-block-string-and-move
                                                             child-uid
                                                             page-data
                                                             m-uid
                                                             order
                                                             (println "updated and moved block")))))))
              :else                  (<p!
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





(defn chat-ui [block-uid]
  (println "block uid for chat" block-uid)
  (let [settings (get-child-with-str block-uid "Settings")
        context  (r/atom (get-child-with-str block-uid "Context"))
        messages (r/atom (get-child-with-str block-uid "Messages"))
        active? (r/atom true)
        default-msg-value (r/atom 400)
        default-temp (r/atom 0.9)
        default-model (r/atom "gpt-4-1106-preview")
        settings {:model default-model
                  :max-tokens default-msg-value
                  :temperature default-temp}]

   (fn [_]
     (let [msg @messages
           c-msg (:children @context)]
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
                   :margin "10px"
                   :background-color "whitesmoke"
                   :border "1px"}}
          [chat-context context]
          [:> ButtonGroup
           {:vertical true
            :style {:align-self "flex-end"
                    :padding "15px"}}
           [:> Button {:class-name "sp"
                       :style {:width "30px"}
                       :icon (if @active? "send-message" nil)
                       :min-height "20px"
                       :minimal true
                       :fill false
                       :large true
                       :loading (not @active?)
                       :on-click (fn []
                                   (when @active?
                                     (do
                                        (println "clicked send button")
                                        (reset! active? false)
                                        (load-context context messages block-uid active? settings))))}]
           [:div
            [:> Popover
             {:arrow true
              :position "bottom"
              :style {:width "200px"
                      :padding "20px"}}
             [:> Button {:class-name "sp"
                         :icon "cog"
                         :minimal true
                         :small true
                         :fill true
                         :style {:height "10px"
                                 :border "none"}}]
             [:> Menu
              {:style {:padding "20px"}}
              [:span {:style {:margin-bottom "5px"}} "Select Model:"]
              [:> HTMLSelect
               {:fill true
                :style {:margin-bottom "10px"}
                :on-change (fn [e]
                             (log "select value" e)
                             (reset! default-model e))
                :value @default-model}

               [:option {:value "gpt-4-1106-preview"} "gpt-4-1106-preview"]
               [:option {:value "gpt-3.5-turbo-1106"} "gpt-3.5-turbo-1106"]]
              [:> MenuDivider {:style {:margin "5px"}}]
              [:div
               {:style {:margin-bottom "10px"}}
               [:span {:style {:margin-bottom "5px"}} "Max output length:"]
               [:> Slider {:min 0
                           :max 2048
                           :label-renderer @default-msg-value
                           :value @default-msg-value
                           :label-values [0 2048]
                           :on-change (fn [e]
                                        (log "slider value" e)
                                        (reset! default-msg-value e))
                           :on-release (fn [e]
                                         (log "slider value" e)
                                         (reset! default-msg-value e))}]]
              [:> MenuDivider {:style {:margin "5px"}}]
              [:div
               {:style {:margin-bottom "10px"}}
               [:span {:style {:margin-bottom "5px"}} "Temperature:"]
               [:> Slider {:min 0
                           :max 2
                           :step-size 0.1
                           :label-renderer @default-temp
                           :value @default-temp
                           :label-values [0 2]
                           :on-change (fn [e]
                                        (log "slider value" e)
                                        (reset! default-temp e))
                           :on-release (fn [e]
                                         (log "slider value" e)
                                         (reset! default-temp e))}]]]]]]]]]))))





(defn main [{:keys [:block-uid]} & args]
  (println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    (println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))
