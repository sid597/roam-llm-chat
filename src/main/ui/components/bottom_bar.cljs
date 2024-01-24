(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.utils :refer [get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

(defn bottom-bar-buttons []
  (let []
    (fn []
      [:> ButtonGroup
       [:> Divider]
       [:> Button {:minimal true
                   :small true
                   :disabled true
                   :on-click #(js/alert "clicked")}
        "Summarise this page"]
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
       [:> Button {:minimal true
                   :small true
                   :disabled true
                   :on-click #(js/alert "clicked")}
        "Load filtered pages into chat"]
       [:> Divider]])))
