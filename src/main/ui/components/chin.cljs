(ns ui.components.chin
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.components.get-context :refer [get-context-button get-suggestions-button]]
            [ui.utils :refer [block-has-child-with-str? title->uid settings-button-popover buttons-settings gemini-safety-settings-struct button-with-tooltip get-child-of-child-with-str create-struct button-popover p get-child-with-str watch-children update-block-string-for-block-with-child]]
            ["@blueprintjs/core" :as bp :refer [RadioGroup MenuDivider Position Radio ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

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
  [button-with-tooltip
   "Send your message."
   [:> Button {:class-name "sp"
               :style {:width "30px"}
               :icon (if @active? nil "send-message")
               :minimal true
               :fill false
               :small true
               :loading @active?
               :on-click #(do #_(println "clicked send message compt")
                            (callback {}))}]])


(defn gemini-safety-component [block-uid]
  (let [settings-uid      (:uid (get-child-with-str block-uid "Settings"))
        harassment        (r/atom (get-child-of-child-with-str settings-uid "Safety settings" "Harassment"))
        hate-speech       (r/atom (get-child-of-child-with-str settings-uid "Safety settings" "Hate Speech"))
        sexually-explicit (r/atom (get-child-of-child-with-str settings-uid "Safety settings" "Sexually Explicit"))
        dangerous-content (r/atom (get-child-of-child-with-str settings-uid "Safety settings" "Dangerous Content"))]
    (fn [_]
      [button-popover
       "Safety settings"
       [:div ;.bp3-popover-dismiss
        [:> RadioGroup
         {:label "Harassment"
          :inline true
          :on-change (fn [v]
                       (let [e (.-value (.-currentTarget v))]
                        (update-block-string-for-block-with-child settings-uid "Safety settings" "Harassment" e)
                        (reset! harassment e)))
          :selected-value @harassment}
         [:> Radio {:label "Block none" :value "Block none"}]
         [:> Radio {:label "Block few" :value "Block few"}]
         [:> Radio {:label "Block some" :value "Block some"}]
         [:> Radio {:label "Block most" :value "Block most"}]]
        [:> RadioGroup
         {:label "Hate Speech"
          :inline true
          :on-change (fn [v]
                       (let [e (.-value (.-currentTarget v))]
                        (update-block-string-for-block-with-child settings-uid "Safety settings" "Hate Speech" e)
                        (reset! hate-speech e)))
          :selected-value @hate-speech}
         [:> Radio {:label "Block none" :value "Block none"}]
         [:> Radio {:label "Block few" :value "Block few"}]
         [:> Radio {:label "Block some" :value "Block some"}]
         [:> Radio {:label "Block most" :value "Block most"}]]
        [:> RadioGroup
         {:label "Sexually Explicit"
          :inline true
          :on-change (fn [v]
                       (let [e (.-value (.-currentTarget v))]
                         (update-block-string-for-block-with-child settings-uid "Safety settings" "Sexually Explicit" e)
                         (reset! sexually-explicit e)))
          :selected-value @sexually-explicit}
         [:> Radio {:label "Block none" :value "Block none"}]
         [:> Radio {:label "Block few" :value "Block few"}]
         [:> Radio {:label "Block some" :value "Block some"}]
         [:> Radio {:label "Block most" :value "Block most"}]]
        [:> RadioGroup
         {:label "Dangerous Content"
          :inline true
          :on-change (fn [v]
                       (let [e (.-value (.-currentTarget v))]
                        (update-block-string-for-block-with-child settings-uid "Safety settings" "Dangerous Content" e)
                        (reset! dangerous-content e)))
          :selected-value @dangerous-content}
         [:> Radio {:label "Block none" :value "Block none"}]
         [:> Radio {:label "Block few" :value "Block few"}]
         [:> Radio {:label "Block some" :value "Block some"}]
         [:> Radio {:label "Block most" :value "Block most"}]]]])))


(defn chin [{:keys [default-model default-max-tokens default-temp get-linked-refs? active? block-uid callback buttons? extract-query-pages? extract-query-pages-ref?]}]
  (let [co-get-context-uid          (:uid (get-child-with-str
                                            (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                            "Get context"))
        co-default-temp             (r/atom (js/parseFloat (get-child-of-child-with-str co-get-context-uid "Settings" "Temperature")))
        co-default-model            (r/atom (get-child-of-child-with-str co-get-context-uid "Settings" "Model"))
        co-default-max-tokens       (r/atom (js/parseInt (get-child-of-child-with-str co-get-context-uid "Settings" "Max tokens")))
        co-get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str co-get-context-uid "Settings" "Get linked refs"))
                                              true
                                              false))
        co-extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str co-get-context-uid "Settings" "Extract query pages"))
                                              true
                                              false))
        co-extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str co-get-context-uid "Settings" "Extract query pages ref?"))
                                              true
                                              false))
        co-pre-prompt                (get-child-of-child-with-str co-get-context-uid "Prompt" "Pre-prompt")
        co-remaining-prompt         (get-child-of-child-with-str co-get-context-uid "Prompt" "Further instructions")
        co-active?                  (r/atom false)
        sug-get-context-uid          (:uid (get-child-with-str
                                             (block-has-child-with-str? (title->uid "LLM chat settings") "Quick action buttons")
                                             "Get suggestions"))
        sug-default-model            (r/atom (get-child-of-child-with-str sug-get-context-uid "Settings" "Model"))
        sug-default-temp             (r/atom (js/parseFloat (get-child-of-child-with-str sug-get-context-uid "Settings" "Temperature")))

        sug-default-max-tokens       (r/atom (js/parseInt (get-child-of-child-with-str sug-get-context-uid "Settings" "Max tokens")))
        sug-get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str sug-get-context-uid "Settings" "Get linked refs"))
                                               true
                                               false))
        sug-extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str sug-get-context-uid "Settings" "Extract query pages"))
                                               true
                                               false))
        sug-extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str sug-get-context-uid "Settings" "Extract query pages ref?"))
                                               true
                                               false))
        sug-pre-prompt                (get-child-of-child-with-str sug-get-context-uid "Prompt" "Pre-prompt")
        sug-remaining-prompt         (get-child-of-child-with-str sug-get-context-uid "Prompt" "Further instructions")
        sug-active?                  (r/atom false)
        messages-uid                  (:uid (get-child-with-str block-uid "Messages"))]
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
     [get-context-button
      messages-uid
      co-default-model
      co-default-temp
      co-default-max-tokens
      co-get-linked-refs?
      co-extract-query-pages?
      co-extract-query-pages-ref?
      co-active?
      co-pre-prompt
      co-remaining-prompt]
     [get-suggestions-button
      messages-uid
      sug-default-model
      sug-default-temp
      sug-default-max-tokens
      sug-get-linked-refs?
      sug-extract-query-pages?
      sug-extract-query-pages-ref?
      sug-active?
      sug-pre-prompt
      sug-remaining-prompt]]
    [:> ButtonGroup
     [settings-button-popover
      [buttons-settings
       "Chat settings"
       block-uid
       default-temp
       default-model
       default-max-tokens
       get-linked-refs?
       extract-query-pages?
       extract-query-pages-ref?]
      "#fdf3ff"]
     [send-message-component
      active?
      callback]]]))







    
