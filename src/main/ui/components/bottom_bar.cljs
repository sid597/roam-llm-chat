(ns ui.components.bottom-bar
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [reagent.core :as r]
            [ui.utils :refer [log get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))


(defn filtered-pages-button []
  (let [disabled? (r/atom true)]
    (fn []
      (let [mutation-callback (fn mutation-callback [mutations observer]
                                (println "mutation callback bottom bar")
                                (doseq [mutation mutations]
                                  (when (= (.-type mutation) "childList")
                                    (doseq [node (array-seq (.-addedNodes mutation))]
                                      (let [parent-class (j/get node :className)
                                            child-class (j/get-in node [:firstChild :className])]
                                        (when (and (instance? js/Element node)
                                                (or (= "roam-body-main"
                                                      parent-class)
                                                  (= "rm-graph-view"
                                                    parent-class)))
                                          (if (or (= "rm-graph-view"
                                                     child-class)
                                                (= "rm-graph-view"
                                                  parent-class))
                                            (reset! disabled? false)
                                            (reset! disabled? true))))))))
            star-observing    (let [observer (js/MutationObserver. mutation-callback)]
                                (.observe observer js/document #js {:childList true
                                                                    :subtree true}))])
      (println "--disabled?--" disabled?)
      [:> Button {:minimal true
                  :small true
                  :disabled @disabled?
                  :on-click #(js/alert "clicked")}
       "Load filtered pages into chat"])))



(defn bottom-bar-buttons []
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
      [filtered-pages-button]
      [:> Divider]]))
