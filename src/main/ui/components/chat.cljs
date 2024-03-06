(ns ui.components.chat
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.utils :refer [p get-child-with-str watch-children update-block-string-for-block-with-child]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

(defn log
  [& args]  (apply js/console.log args))

(defn chat-context
  ([context handle-keydown-event]
   (chat-context context handle-keydown-event {}))
  ([context handle-keydown-event style-map]
   (let [context-ref (r/atom nil)
         chat-loaded (r/atom nil)
         update-fn   (fn [this]
                       (when-let [context-el @context-ref]
                         ;(println "4. chat context update fn")
                         ;(set! (.-innerHTML context-el ) "")
                         (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                               (clj->js {:uid (:uid @context)
                                         :zoom-path true
                                         :el context-el}))
                           (.then
                             (fn [_]
                               (p "chat context block rendered successfully")))
                           (.catch (fn [e]
                                     (log "Error in chat context block" (:uid @context) e @context))))))]

     (r/create-class
       {:component-did-mount  update-fn
        :component-did-update update-fn
        :reagent-render
        (fn []
          (let [cmsg (:children @context)]
            #_(println "3. chat context insdie component")
            [:div
             {:class-name (str "chat-context-" (:uid @context))
              :ref (fn [el] (reset! context-ref el))
              :on-key-down handle-keydown-event
              :style (merge {:flex "1 1 auto"
                             :height "100%"
                             :overflow "auto"
                             :flex-direction "column"
                             :display "flex"
                             :align-items "stretch"
                             :background-color "#f6cbfe3d"
                             :min-height "100px"
                             :max-height "700px"}
                       style-map)}]))}))))


(defn chat-history [m-uid m-children token-count]
  (let [history-ref  (r/atom nil)
        update-fn   (fn [this]
                      (when-let [hist-el @history-ref]
                        (p "chat history update fn: " @m-children)
                        (set! (.-innerHTML hist-el ) "")
                        (doall
                          (for [child (reverse (sort-by :order (:children @m-children)))]
                            ^{:key child}
                            (do
                             (let [uid (:uid child)
                                   msg-block-div (.createElement js/document (str "div.msg-" uid))]
                               (do
                                 ;(log "chat-history ref" hist-el)
                                 #_(println "chat histor child ---->" child)
                                 (if (.hasChildNodes hist-el)
                                   (.insertBefore hist-el msg-block-div (.-firstChild hist-el))
                                   (.appendChild hist-el msg-block-div (.-firstChild hist-el)))
                                 (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                   (clj->js {:uid uid
                                             :zoom-path true
                                             :el msg-block-div})))))))
                        (.scrollTo hist-el 0 (.-scrollHeight hist-el))))]


    (r/create-class
      {:component-did-update update-fn
       :component-did-mount  update-fn
       :reagent-render       (fn [_ _ _]
                               (let [tc @token-count
                                     id (random-uuid)]
                                 (p "TOKEN COUNT" tc)
                                 [:div.middle-comp
                                  {:class-name (str "chat-history-container-" m-uid)
                                   :style
                                    {:display "flex"
                                     :box-shadow "#2e4ba4d1 0px 0px 4px 0px"
                                     :margin-bottom "15px"
                                     :flex-direction "column"}}
                                  [:div
                                   {:class-name (str "chat-history-" m-uid)
                                    :ref   (fn [el] (reset! history-ref el))
                                    :style {:flex "1"
                                            :overflow-y "auto"
                                            :min-height "300px"
                                            :max-height "700px"
                                            :background "aliceblue"}}]

                                  [:div
                                   {:class-name (str "messages-chin-" m-uid)
                                    :style {:display "flex"
                                            :flex-direction "row"
                                            :background-color "aliceblue"
                                            :min-height "27px"
                                            :justify-content "end"
                                            :font-size "10px"
                                            :padding-right "11px"
                                            :padding-top "10px"
                                            :align-items "center"
                                            :border "1px"}}
                                   [:span (str "Tokens used: " tc)]
                                   [:> Button {:class-name (str "scroll-down-button" m-uid)
                                               :style {:width "30px"}
                                               :icon "chevron-down"
                                               :minimal true
                                               :fill false
                                               :small true
                                               :on-click #(let [el @history-ref]
                                                            (p "scroll down button clicked")
                                                            (when el
                                                              (set! (.-scrollTop el) (.-scrollHeight el))))}]
                                   [:> Button {:class-name (str "scroll-up-button" m-uid)
                                               :style {:width "30px"}
                                               :icon "chevron-up"
                                               :minimal true
                                               :fill false
                                               :small true
                                               :on-click #(let [el @history-ref]
                                                            (p "scroll up button clicked")
                                                            (when el
                                                              (set! (.-scrollTop el) 0)))}]]]))})))



(defn inject-style []
  (let [style-element (.createElement js/document "style")
        css-string ".sp svg { color: cadetblue; }"] ; Change 'blue' to your desired color

    (set! (.-type style-element) "text/css")
    (when (.-styleSheet style-element) ; For IE8 and below.
      (set! (.-cssText (.-styleSheet style-element)) css-string))
    (when-not (.-styleSheet style-element) ; For modern browsers.
      (let [text-node (.createTextNode js/document css-string)]
        (.appendChild style-element text-node)))
    (.appendChild (.-head js/document) style-element)))

(defn send-message-component [active? callback]
  (inject-style)
  [:> Button {:class-name "sp"
              :style {:width "30px"}
              :icon (if @active? nil "send-message")
              :minimal true
              :fill false
              :small true
              :loading @active?
              :on-click #(do #_(println "clicked send message compt")
                             (callback {}))}])

(defn button-popover
  ([button-text render-comp]
   (button-popover button-text render-comp "#eeebeb"))
  ([button-text render-comp bg-color]
   [:> Popover
    {:position "bottom"}
    [:> Button {:minimal true
                :small true}
     button-text]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))


(defn chin
  ([default-model default-msg-value default-temp get-linked-refs active? block-uid]
   (chin default-model default-msg-value default-temp get-linked-refs active? block-uid nil))
  ([default-model default-msg-value default-temp get-linked-refs active? block-uid callback]
   [:div.chin
     {:style {:display "flex"
              :flex-direction "row"
              :background-color "#f6cbfe3d"
              :min-height "27px"
              :justify-content "space-between"
              :font-size "10px"
              :padding-right "11px"
              :align-items "center"
              :border "1px"}}
     [:> ButtonGroup
      [:div {:style {:overflow  "hidden"}}
       [button-popover
        (str "Model: " @default-model)
        [:div
         [:span {:style {:margin-bottom "5px"}} "Select Model:"]
         [:> Divider]
         [:> Menu.Item
          {:text "gpt-4"
           :on-click (fn [e]
                       #_(js/console.log "clicked menu item" e)
                       (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-4")
                       (reset! default-model "gpt-4"))}]
         [:> Divider]
         [:> Menu
          [:> Menu.Item
           {:text "gpt-3.5"
            :on-click (fn [e]
                        #_(js/console.log "clicked menu item" e)
                        (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-3.5")
                        (reset! default-model "gpt-3.5"))}]]]]]
      [:> Divider]
      [:div {:style {:overflow  "hidden"}}
        [button-popover
         (str "Max Tokens: " @default-msg-value)
         [:div.bp3-popover-dismiss
          [:span {:style {:margin-bottom "5px"}} "Max output length:"]
          [:> Slider {:min 0
                      :max 2048
                      :label-renderer @default-msg-value
                      :value @default-msg-value
                      :label-values [0 2048]
                      :on-change (fn [e]

                                   (update-block-string-for-block-with-child block-uid "Settings" "Max tokens" (str e))
                                   (reset! default-msg-value e))
                      :on-release (fn [e]
                                    #_(log "slider value" e)
                                    (update-block-string-for-block-with-child block-uid "Settings" "Max tokens" (str e))
                                    (reset! default-msg-value e))}]]]]
      [:> Divider]
      [:div {:style {:overflow  "hidden"}}
        [button-popover
         (str "Temp: " (js/parseFloat (.toFixed @default-temp 1)))
         [:div.bp3-popover-dismiss
          {:style {:margin-bottom "10px"}}
          [:span {:style {:margin-bottom "5px"}} "Temperature:"]
          [:> Slider {:min 0
                      :max 2
                      :step-size 0.1
                      :label-renderer @default-temp
                      :value @default-temp
                      :label-values [0 2]
                      :on-change (fn [e]
                                   (update-block-string-for-block-with-child block-uid "Settings" "Temperature" (str e))
                                   (reset! default-temp e))
                      :on-release (fn [e]
                                    (update-block-string-for-block-with-child block-uid "Settings" "Temperature" (str e))
                                    (reset! default-temp e))}]]]]

      [:> Divider]
      [:div.chk
       {:style {:align-self "center"
                :margin-left "5px"}}
       [:> Checkbox
        {:style {:margin-bottom "0px"}
         :checked @get-linked-refs
         :on-change (fn [x]
                      (update-block-string-for-block-with-child block-uid "Settings" "Get linked refs" (str (not @get-linked-refs)))
                      (reset! get-linked-refs (not @get-linked-refs)))}
        [:span.bp3-button-text
         {:style {:font-size "14px"
                  :font-family "initial"
                  :font-weight "initial"}} "Include linked refs?"]]]]

    (when (not (nil? callback))
     [send-message-component
      active?
      callback])]))




