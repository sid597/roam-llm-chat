(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components.quick-buttons :refer [button-with-settings]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [ai-block-exists? chat-ui-with-context-struct uid->title log get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))




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
                                      page-title    (uid->title open-page-uid)
                                      context-struct [{:s (str "[[" page-title "]]")}]
                                      ai-block? (ai-block-exists? open-page-uid)]
                                  (if (some? ai-block?)
                                    (create-struct
                                      (default-chat-struct chat-block-uid nil nil context-struct)
                                      ai-block?
                                      chat-block-uid
                                      true)
                                   (create-struct
                                     (chat-ui-with-context-struct chat-block-uid nil context-struct)
                                     open-page-uid
                                     chat-block-uid
                                     true)))))}
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
