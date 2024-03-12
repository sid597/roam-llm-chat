(ns ui.components.chin
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.utils :refer [button-popover p get-child-with-str watch-children update-block-string-for-block-with-child]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

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


(defn chin
  ([default-model default-max-tokens default-temp get-linked-refs active? block-uid]
   (chin default-model default-max-tokens default-temp get-linked-refs active? block-uid nil nil))
  ([default-model default-max-tokens default-temp get-linked-refs active? block-uid callback]
   (chin default-model default-max-tokens default-temp get-linked-refs active? block-uid callback nil))
  ([default-model default-max-tokens default-temp get-linked-refs active? block-uid callback buttons?]
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
      (when (some? default-model)
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
           [:> Menu.Item
            {:text "gpt-3.5"
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "gpt-3.5")
                         (reset! default-model "gpt-3.5"))}]
           [:> Divider]
           [:> Menu.Item
            {:text "claude-3-opus"
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "claude-3-opus")
                         (reset! default-model "claude-3-opus"))}]
           [:> Divider]
           [:> Menu.Item
            {:text "claude-3-sonnet"
             :on-click (fn [e]
                         #_(js/console.log "clicked menu item" e)
                         (update-block-string-for-block-with-child block-uid "Settings" "Model" "claude-3-sonnet")
                         (reset! default-model "claude-3-sonnet"))}]]])]
     (when (some? default-max-tokens)
       [:> Divider]
       [:div {:style {:overflow  "hidden"}}
        [button-popover
         (str "Max Tokens: " @default-max-tokens)
         [:div.bp3-popover-dismiss
          [:span {:style {:margin-bottom "5px"}} "Max output length:"]
          [:> Slider {:min 0
                      :max 2048
                      :label-renderer @default-max-tokens
                      :value @default-max-tokens
                      :label-values [0 2048]
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
                                    (reset! default-temp e))}]]]])

     (when (some? get-linked-refs)
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
                   :font-weight "initial"}} "Include linked refs?"]]])
     (when (some? buttons?)
       buttons?)]
    (when (some? callback)
      [send-message-component
       active?
       callback])]))