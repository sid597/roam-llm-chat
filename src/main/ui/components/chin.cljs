(ns ui.components.chin
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.utils :refer [gemini-safety-settings-struct get-child-of-child-with-str create-struct button-popover p get-child-with-str watch-children update-block-string-for-block-with-child]]
            ["@blueprintjs/core" :as bp :refer [RadioGroup MenuDivider Radio ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

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
     [:div
       {:style {:overflow  "hidden"}}
      (when (some? default-model)
        [button-popover
          (str "Model: " @default-model)
          [:div
           [:> MenuDivider {:title "With vision capabilities"}]
           [:> Menu.Item
                       {:text "GPT-4-vision"
                        :labelElement "$5.00"
                        :should-dismiss-popover dismiss-popover?
                        :on-click (fn [e]
                                    #_(js/console.log "clicked menu item" e)
                                    (p "chose gpt-4-vision")
                                    (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-4-vision")
                                    (reset! default-model "gpt-4-vision"))}]
           [:> MenuDivider {:title "Fast Free and 1M tokens"}]
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
           [:> MenuDivider {:title "Top of the line"}]
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
           [:> MenuDivider {:title "Fast and cheap"}]
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
                         (reset! default-model "gpt-3.5"))}]]])]

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
                                    (reset! default-max-tokens e))}]]]])
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
                                    (reset! default-temp e))}]]]])


     (when (some? get-linked-refs?)
       [:> Divider]
       [:div.chk
        {:style {:align-self "center"
                 :margin-left "5px"}}
        [:> Checkbox
         {:style {:margin-bottom "0px"}
          :checked @get-linked-refs?
          :on-change (fn [x]
                       (update-block-string-for-block-with-child block-uid "Settings" "Get linked refs" (str (not @get-linked-refs?)))
                       (reset! get-linked-refs? (not @get-linked-refs?)))}
         [:span.bp3-button-text
          {:style {:font-size "14px"
                   :font-family "initial"
                   :font-weight "initial"}} "Include discourse node refs?"]]])
     (when (some? extract-query-pages?)
       [:> Divider]
       [:div.chk
        {:style {:align-self "center"
                 :margin-left "5px"}}
        [:> Checkbox
         {:style {:margin-bottom "0px"}
          :checked @extract-query-pages?
          :on-change (fn [x]
                       (update-block-string-for-block-with-child block-uid "Settings" "Extract query pages" (str (not @extract-query-pages?)))
                       (reset! extract-query-pages? (not @extract-query-pages?)))}
         [:span.bp3-button-text
          {:style {:font-size "14px"
                   :font-family "initial"
                   :font-weight "initial"}} "Extract query pages?"]]])

     (when (some? extract-query-pages-ref?)
       [:> Divider]
       [:div.chk
        {:style {:align-self "center"
                 :margin-left "5px"}}
        [:> Checkbox
         {:style {:margin-bottom "0px"}
          :checked @extract-query-pages-ref?
          :on-change (fn [x]
                       (update-block-string-for-block-with-child block-uid "Settings" "Extract query pages ref?" (str (not @extract-query-pages-ref?)))
                       (reset! extract-query-pages-ref? (not @extract-query-pages-ref?)))}
         [:span.bp3-button-text
          {:style {:font-size "14px"
                   :font-family "initial"
                   :font-weight "initial"}} "Extract query pages ref?"]]])
     (when (some? buttons?)
       buttons?)]
    (when (some? callback)
      [send-message-component
       active?
       callback])]))