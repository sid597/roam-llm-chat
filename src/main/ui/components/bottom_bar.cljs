(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components.chat :refer [chat-context chin]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [log get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

(defn button-popover
  ([render-comp]
   (button-popover render-comp "#eeebeb"))
  ([render-comp bg-color]
   [:> Popover
    ;{:position "bottom"}
    [:> Button {:icon "cog"
                :minimal true
                :small true
                :style {:background-color bg-color}}]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))



(defn button-with-settings [button-name]
  (let [get-linked-refs (r/atom true)
        active? (r/atom false)
        default-msg-value (r/atom 400)
        default-temp (r/atom 0.9)
        default-model (r/atom "gpt-4-1106-preview")
        context (r/atom (get-child-of-child-with-str-on-page "llm chat" "Quick action buttons" button-name "Context"))]
    [:> ButtonGroup
     {:minimal true
      :small true}
     [button-popover
      [:> Card {:elevation 3
                :style {:flex "1"
                        :margin "0"
                        :display "flex"
                        :flex-direction "column"
                        :border "2px solid rgba(0, 0, 0, 0.2)"
                        :border-radius "8px"}}
       [:div.chat-input-container
        {:style {:display "flex"
                 :flex-direction "row"
                 :border-radius "8px"
                 :margin "10px 10px -10px 10px  "
                 :background-color "whitesmoke"
                 :border "1px"}}
        [chat-context context #()]]
       [chin default-model default-msg-value default-temp get-linked-refs active?]]]
     [:> Button {:minimal true
                 :small true
                 :on-click (fn [e])}
      "Summarise this page"]]))



(defn bottom-bar-buttons []
   (fn []
     [:> ButtonGroup
      [:> Divider]
      [button-with-settings "Summarise this page"]
      [:> Divider]
      [:> Button {:minimal true
                  :small true
                  :on-click (fn [e]
                              (go
                                (let [chat-block-uid (gen-new-uid)
                                      open-page-uid (<p! (get-open-page-uid))
                                      chat-struct (default-chat-struct chat-block-uid)]
                                  (create-struct
                                    chat-struct
                                    open-page-uid
                                    chat-block-uid
                                    true))))}
       "Chat with this page"]
      [:> Divider]
      [:> Button {:minimal true
                  :small true
                  :on-click (fn [e]
                              (let [chat-block-uid (gen-new-uid)
                                    chat-struct (default-chat-struct chat-block-uid)]
                                (create-struct
                                  chat-struct
                                  (get-todays-uid)
                                  chat-block-uid
                                  true)))}

       "Start chat in daily notes, show in sidebar"]
      [:> Divider]
      [:> Button {:minimal true
                  :small true
                  :on-click (fn [e]

                              (let [chat-block-uid (gen-new-uid)
                                    [parent-uid
                                     block-order]  (get-block-parent-with-order (get-focused-block))
                                    chat-struct (default-chat-struct chat-block-uid nil block-order)]
                                (create-struct
                                  chat-struct
                                  parent-uid
                                  chat-block-uid
                                  false)))}

       "Start chat in focused block"]
      [:> Divider]
      [filtered-pages-button]
      [:> Divider]]))
