(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components.quick-buttons :refer [button-with-settings]]
            [ui.extract-data.chat :refer [data-for-blocks]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [ai-block-exists? chat-ui-with-context-struct uid->title log get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))



(defn bottom-bar-buttons []
   (fn []
     [:> ButtonGroup
      [:> Divider]
      [:div {:style {:flex "1 1 1"}}
       [button-with-settings "Summarise this page"]]

      [:> Divider]
      [:div
       {:style {:flex "1 1 1"}}
       [:> Button {:minimal true
                   :small true
                   :style {:flex "1 1 1"}
                   :on-click (fn [e]
                               (go
                                 (let [chat-block-uid (gen-new-uid)
                                       open-page-uid (<p! (get-open-page-uid))
                                       page-title    (uid->title open-page-uid)
                                       block-data    (when (nil? page-title)
                                                       (str
                                                         "```"
                                                         (clojure.string/join "\n -----" (data-for-blocks [open-page-uid]))
                                                         "```"))
                                       page-data     (str "[[" page-title "]]")
                                       context-struct [{:s (if (nil? page-title)
                                                             block-data
                                                             page-data)}
                                                       {:s ""}]
                                       ai-block? (ai-block-exists? open-page-uid)]

                                   ;(println "open page uid" open-page-uid)
                                   ;(println "page title" page-title)
                                   ;(println "extract block" block-data)
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
        "Chat with this page"]]
      [:> Divider]
      [:div
       {:style {:flex "1 1 1"}}
       [:> Button {:minimal true
                   :small true
                   :on-click (fn [e]
                               (let [chat-block-uid (gen-new-uid)
                                     ai-block?      (ai-block-exists? (get-todays-uid))]
                                 (if (some? ai-block?)
                                   (create-struct
                                     (default-chat-struct chat-block-uid)
                                     ai-block?
                                     chat-block-uid
                                     true)
                                   (create-struct
                                     (chat-ui-with-context-struct chat-block-uid)
                                     (get-todays-uid)
                                     chat-block-uid
                                     true))))}
        "Start chat in daily notes, show in sidebar"]]
      [:> Divider]
      [:div
       {:style {:flex "1 1 1"}}
       [:> Button {:minimal true
                   :small true
                   :style {:flex "1 1 1"}
                   :on-click (fn [e]
                               (let [chat-block-uid (gen-new-uid)
                                     [parent-uid
                                      block-order] (get-block-parent-with-order (get-focused-block))
                                     chat-struct   (chat-ui-with-context-struct chat-block-uid nil nil block-order)]
                                 (create-struct
                                   chat-struct
                                   parent-uid
                                   chat-block-uid
                                   false)))}
        "Start chat in focused block"]]
      [:> Divider]
      [:div {:style {:flex "1 1 1"}}
       [filtered-pages-button]]
      [:> Divider]]))
