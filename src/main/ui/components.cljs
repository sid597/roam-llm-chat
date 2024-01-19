(ns ui.components
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

(defn log
  [& args]  (apply js/console.log args))

(defn chat-context [context handle-keydown-event]
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
                              #_(println "5. chat context block rendered successfully")))
                          (.catch (fn [e]
                                    (log "Error in chat context block" e))))))]
    (r/create-class
      {:component-did-mount  update-fn
       :component-did-update update-fn
       :reagent-render
       (fn []
         (let [cmsg (:children @context)]
           #_(println "3. chat context insdie component")
           [:div.chat-loader
            {:ref (fn [el] (reset! context-ref el))
             :on-key-down handle-keydown-event
             :style {:flex "1 1 auto"
                     :height "100%"
                     :overflow "auto"
                     :flex-direction "column"
                     :display "flex"
                     :align-items "stretch"
                     :background-color "whitesmoke"
                     :min-height "100px"
                     :border-radius "8px 8px 0px 0px"
                     :max-height "700px"}}]))})))

(defn chat-history [messages]
  #_(println "load chat-history")
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
                :small true
                :style {:background-color bg-color}}
     button-text]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))


(defn chin [default-model default-msg-value default-temp get-linked-refs active? callback]
   [:div.chin
    {:style {:display "flex"
             :flex-direction "row"
             :border-radius "0px 0px 8px 8px"
             :margin "10px"
             :background-color "#eeebeb"
             :min-height "27px"
             :justify-content "space-between"
             :font-size "10px"
             :padding-right "11px"
             :align-items "center"
             :border "1px"}}
    [:> ButtonGroup
     [button-popover
      (str "Model: " @default-model)
      [:div
       [:span {:style {:margin-bottom "5px"}} "Select Model:"]
       [:> Divider]
       [:> Menu.Item
        {:text "gpt-4-1106-preview"
         :on-click (fn [e]
                     #_(js/console.log "clicked menu item" e)
                     (reset! default-model "gpt-4-1106-preview"))}]
       [:> Divider]
       [:> Menu
        [:> Menu.Item
         {:text "gpt-3.5-turbo-1106"
          :on-click (fn [e]
                      #_(js/console.log "clicked menu item" e)
                      (reset! default-model "gpt-3.5-turbo-1106"))}]]]]
     [:> Divider]
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
                                (reset! default-msg-value e))
                   :on-release (fn [e]
                                 #_(log "slider value" e)
                                 (reset! default-msg-value e))}]]]
     [:> Divider]
     [button-popover
      (str "Temperature: " (js/parseFloat (.toFixed @default-temp 1)))
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
                                (reset! default-temp e))
                   :on-release (fn [e]
                                 (reset! default-temp e))}]]]

     [:> Divider]
     [:div.chk
      {:style {:align-self "center"
               :margin-left "5px"}}
      [:> Checkbox
       {:style {:margin-bottom "0px"}
        :checked @get-linked-refs
        :on-change (fn [x]
                     (reset! get-linked-refs (not @get-linked-refs)))}
       [:span.bp3-button-text
        {:style {:font-size "14px"
                 :font-family "initial"
                 :font-weight "initial"}} "Include linked references?"]]]]


    [send-message-component
     active?
     callback]])



(defn bottom-bar-buttons []
  (let []
    (fn []
      [:> ButtonGroup
       [:> Divider]
       [:> Button {:minimal true
                   :small true
                   :on-click #(js/alert "clicked")}
        "Summarise this page"]
       [:> Divider]
       [:> Button {:minimal true
                   :small true
                   :on-click #(js/alert "clicked")}
        "Chat with this page"]
       [:> Divider]
       [:> Button {:minimal true
                          :small true
                          :on-click #(js/alert "clicked")}
        "Start chat in daily notes, show in sidebar"]
       [:> Divider]
       [:> Button {:minimal true
                   :small true
                   :on-click #(js/alert "clicked")}
        "Load filtered pages into chat"]
       [:> Divider]])))
