(ns ui.components.chin
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.components.get-context :refer [get-context-button]]
            [ui.utils :refer [gemini-safety-settings-struct button-with-tooltip get-child-of-child-with-str create-struct button-popover p get-child-with-str watch-children update-block-string-for-block-with-child]]
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
  (let [dismiss-popover? (if (some? callback) true false)]
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

     [get-context-button block-uid]
     [:div
       {:style {:overflow  "hidden"}}
      (when (some? default-model)
        [button-popover
          (str "Model: " @default-model)
         [button-with-tooltip
          "Pricing is per million token but it does not mean we can use 1 Million tokens with every llm.
           Only Gemini models support 1M tokens, Claude supports 200k, GPT-4o supports 128k and GPT-3.5 supports 16k tokens.
           One useful strategy to optimise for pricing is to use different models for different type of messages in the same
           chat for e.g you can use lower priced models to extract the necessary data from the context and then select
           a stronger model to do reasoning on it."
          [:div
           [button-with-tooltip
            "If your page or context has any images and you want the llm to also consider them while
            replying then you should use the GPT-4 Vision model."
            [:> MenuDivider {:title "With vision capabilities"}]
            (.-RIGHT Position)]
           [:> Menu.Item
                       {:text "GPT-4 Vision"
                        :labelElement "$5.00"
                        :should-dismiss-popover dismiss-popover?
                        :on-click (fn [e]
                                    #_(js/console.log "clicked menu item" e)
                                    (p "chose gpt-4-vision")
                                    (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-4-vision")
                                    (reset! default-model "gpt-4-vision"))}]
           [button-with-tooltip
            "Gemini-1.5 Flash has 1 Million token context window so if you want to select a few pages with large context
             and then chat with them, this model is the only one able to support such large context and not charge for it.
             The catch is that they might use the data for training their models, we can opt for a paid plan and in that
             case google will not use the data and would charge us  $0.35/1 Million tokens"
            [:> MenuDivider {:title "Fast Free and 1M tokens"}]
            (.-RIGHT Position)]
           [:> Menu.Item
            {:text "Gemini 1.5 Flash"
             :should-dismiss-popover dismiss-popover?
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (p "chose gemini-1.5-flash")
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "gemini-1.5-flash")
                         (let [settings-exist? (get-child-of-child-with-str block-uid "Settings" "Safety settings")]
                           (if (nil? settings-exist?)
                              (create-struct
                                  gemini-safety-settings-struct
                                  (:uid (get-child-with-str block-uid "Settings"))
                                  nil
                                  nil
                                  #(reset! default-model "gemini-1.5-flash"))
                              (reset! default-model "gemini-1.5-flash"))))}]
           [button-with-tooltip
            "For text only context you can consider the reasoning ability, context length and pricing of the models.
            Note that if your context is say 50k tokens and you do back-and-forth with the llm 10 times then your
            total token usage would be approx. 500k (approx. 50k tokens send to llm 10 times)"
            [:> MenuDivider {:title "Top of the line"}]
            (.-RIGHT Position)]
           [:> Menu.Item
                       {:text "GPT-4o"
                        :labelElement "$5.00"
                        :should-dismiss-popover dismiss-popover?
                        :on-click (fn [e]
                                    #_(js/console.log "clicked menu item" e)
                                    (p "chose gpt-4o")
                                    (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-4o")
                                    (reset! default-model "gpt-4o"))}]
           [:> Menu.Item
            {:text "Claude 3.5 Sonnet"
             :labelElement "$3.00"
             :should-dismiss-popover dismiss-popover?
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (p "chose claude-3.5-sonnet")
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "claude-3.5-sonnet")
                         (reset! default-model "claude-3.5-sonnet"))}]
           [:> Menu.Item
            {:text "Gemini 1.5 Pro"
             :labelElement "$3.50"
             :should-dismiss-popover dismiss-popover?
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (p "chose gemini-1.5-pro")
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "gemini-1.5-pro")
                         (let [settings-exist? (get-child-of-child-with-str block-uid "Settings" "Safety settings")]
                           (if (nil? settings-exist?)
                             (create-struct
                               gemini-safety-settings-struct
                               (:uid (get-child-with-str block-uid "Settings"))
                               nil
                               nil
                               #(reset! default-model "gemini-1.5-pro"))
                             (reset! default-model "gemini-1.5-pro"))))}]
           [:> Menu.Item
                       {:text "Claude 3 Opus"
                        :labelElement "$15.0"
                        :should-dismiss-popover dismiss-popover?
                        :on-click (fn [e]
                                    #_(js/console.log "clicked menu item" e)
                                    (p "chose claude-3-opus")
                                    (update-block-string-for-block-with-child block-uid "Settings" "Model" "claude-3-opus")
                                    (reset! default-model "claude-3-opus"))}]
           [button-with-tooltip
            "If you want to some tasks that don't need \"strong\" reasoning ability then you can use these models."
            [:> MenuDivider {:title "Fast and cheap"}]
            (.-RIGHT Position)]
           [:> Menu.Item
                       {:text "Claude 3 Haiku"
                        :labelElement "$0.25"
                        :should-dismiss-popover dismiss-popover?
                        :on-click (fn [e]
                                    #_(js/console.log "clicked menu item" e)
                                    (p "chose claude-3-haiku")
                                    (update-block-string-for-block-with-child block-uid "Settings" "Model" "claude-3-haiku")
                                    (reset! default-model "claude-3-haiku"))}]
           [:> Menu.Item
            {:text "GPT-3.5 Turbo"
             :labelElement "$0.50"
             :should-dismiss-popover dismiss-popover?
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (p "chose gpt-3.5")
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-3.5")
                         (reset! default-model "gpt-3.5"))}]]
          (.-LEFT Position)]])]

     (when (and (some? default-model)
             (contains? #{"gemini-1.5-flash" "gemini-1.5-pro"} @default-model))
       [gemini-safety-component block-uid])
     (when (some? default-max-tokens)
       [:> Divider]
       [:div {:style {:overflow  "hidden"}}
        [button-popover
         (str "Max Tokens: " @default-max-tokens)
         [:div
          (when (some? callback)
            {:class-name "bp3-popover-dismiss"})
          [button-with-tooltip
           "The maximum number of tokens to generate before stopping. Models may stop before reaching this maximum. This parameter only specifies the absolute maximum number of tokens to generate."
           [:div
            [:span {:style {:margin-bottom "5px"}} "Max output length:"]
            [:> Slider {:min 0
                        :max 4096
                        :label-renderer @default-max-tokens
                        :value @default-max-tokens
                        :label-values [0 4096]
                        :on-change (fn [e]
                                     (update-block-string-for-block-with-child block-uid "Settings" "Max tokens" (str e))
                                     (reset! default-max-tokens e))
                        :on-release (fn [e]
                                      #_(log "slider value" e)
                                      (update-block-string-for-block-with-child block-uid "Settings" "Max tokens" (str e))
                                      (reset! default-max-tokens e))}]]]]]])
     (when (some? default-temp)
       [:> Divider]
       [:div {:style {:overflow  "hidden"}}
        [button-popover
         (str "Temp: " (js/parseFloat (.toFixed @default-temp 1)))
         [:div
          (merge
           (when (some? callback)
             {:class-name "bp3-popover-dismiss"})
           {:style {:margin-bottom "10px"}})
          [button-with-tooltip
           "Amount of randomness injected into the response.\n\nDefaults to 0.9. Ranges from 0.0 to 1.0 for claude and 0.0 to 2.0 for other models. Use temperature closer to 0.0 for analytical / multiple choice, and closer to 1.0 for creative and generative tasks.\n\nNote that even with temperature of 0.0, the results will not be fully deterministic."
           [:div
            [:span {:style {:margin-bottom "5px"}} "Temperature:"]
            [:> Slider {:min 0
                        :max (if (contains? #{"claude-3-haiku""claude-3.5-sonnet""claude-3-opus"} @default-model)
                               1
                               2)
                        :step-size 0.1
                        :label-renderer @default-temp
                        :value @default-temp
                        :label-values (if (contains? #{"claude-3-haiku""claude-3.5-sonnet""claude-3-opus"} @default-model)
                                        [0 1]
                                        [0 2])
                        :on-change (fn [e]
                                     (update-block-string-for-block-with-child block-uid "Settings" "Temperature" (str e))
                                     (reset! default-temp e))
                        :on-release (fn [e]
                                      (update-block-string-for-block-with-child block-uid "Settings" "Temperature" (str e))
                                      (reset! default-temp e))}]]]]]])


     (when (some? get-linked-refs?)
       [:> Divider]
       [:div.chk
        {:style {:align-self "center"
                 :margin-left "5px"}}
        [button-with-tooltip
         "For each page in the context, if you want to include references to other discourse nodes from that page select this option."
         [:> Checkbox
          {:style {:margin-bottom "0px"}
           :checked @get-linked-refs?
           :on-change (fn [x]
                        (update-block-string-for-block-with-child block-uid "Settings" "Get linked refs" (str (not @get-linked-refs?)))
                        (reset! get-linked-refs? (not @get-linked-refs?)))}
          [:span.bp3-button-text
           {:style {:font-size "14px"
                    :font-family "initial"
                    :font-weight "initial"}} "Include discourse node refs?"]]]])
     (when (some? extract-query-pages?)
       [:> Divider]
       [:div.chk
        {:style {:align-self "center"
                 :margin-left "5px"}}

        [button-with-tooltip
         "When checked, if you put any page in the context, and that page has a query block in it then we will extract
          all the results of the query block. For each result if the result is a page then we extract the whole content of
          the result page and if the result is a block ref we extract the content of the block ref. "
         [:> Checkbox
          {:style {:margin-bottom "0px"}
           :checked @extract-query-pages?
           :on-change (fn [x]
                        (update-block-string-for-block-with-child block-uid "Settings" "Extract query pages" (str (not @extract-query-pages?)))
                        (reset! extract-query-pages? (not @extract-query-pages?)))}
          [:span.bp3-button-text
           {:style {:font-size "14px"
                    :font-family "initial"
                    :font-weight "initial"}} "Extract query results?"]]]])

     (when (some? extract-query-pages-ref?)
       [:> Divider]
       [:div.chk
        {:style {:align-self "center"
                 :margin-left "5px"}}
        [button-with-tooltip
         "This builds on top of the previous button `Extract query results?`. For each page result in the query builder's list of results
         we also extract that result page's linked discourse node references. "
         [:> Checkbox
          {:style {:margin-bottom "0px"}
           :checked @extract-query-pages-ref?
           :on-change (fn [x]
                        (update-block-string-for-block-with-child block-uid "Settings" "Extract query pages ref?" (str (not @extract-query-pages-ref?)))
                        (reset! extract-query-pages-ref? (not @extract-query-pages-ref?)))}
          [:span.bp3-button-text
           {:style {:font-size "14px"
                    :font-family "initial"
                    :font-weight "initial"}} "Extract query pages ref?"]]]])
     (when (some? buttons?)
       buttons?)]
    (when (some? callback)
     [send-message-component
      active?
      callback])]))