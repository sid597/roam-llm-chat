(ns ui.render-comp.discourse-suggestions
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.utils :refer [get-safety-settings send-message-component model-mappings watch-children update-block-string-for-block-with-child watch-string create-struct settings-struct get-child-of-child-with-str q p get-parent-parent extract-from-code-block log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
            [reagent.dom :as rd]))

(defn chat-history [m-uid m-children]
  (fn [_ _ _]
      [:div.middle-comp
       {:class-name (str "chat-history-container-" m-uid)
        :style
        {:display "flex"
         :box-shadow "#2e4ba4d1 0px 0px 4px 0px"
         :margin-bottom "15px"
         :flex-direction "column"}}
       [:div
        {:class-name (str "chat-history-" m-uid)
         :ref   (fn [el] (do
                           ;(p "chat history update fn: " @m-children)
                           (set! (.-innerHTML el ) "")
                           (doall
                             (for [child (reverse (sort-by :order (:children @m-children)))]
                               ^{:key child}
                               (do
                                 (let [uid (:uid child)
                                       msg-block-div (.createElement js/document (str "div.msg-" uid))]
                                   (do
                                     ;(log "chat-history ref" hist-el)
                                     #_(println "chat histor child ---->" child)
                                     (if (.hasChildNodes el)
                                       (.insertBefore el msg-block-div (.-firstChild el))
                                       (.appendChild el msg-block-div (.-firstChild el)))
                                     (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                       (clj->js {:uid uid
                                                 :zoom-path true
                                                 :el msg-block-div})))))))
                           (.scrollTo el 0 (.-scrollHeight el))))
         :style {:flex "1"
                 :overflow-y "auto"
                 :min-height "300px"
                 :max-height "700px"
                 :background "aliceblue"}}]]))


(defn discourse-node-suggestions-ui [block-uid]
 #_(println "block uid for chat" block-uid)
 (let [messages-atom      (r/atom (get-child-with-str block-uid "Suggestions"))
       token-count        (r/atom (js/parseInt (get-child-of-child-with-str block-uid "Settings" "Token count")))]
   (fn [_]
      [:div
       {:class-name (str "dg-suggestions-container-" block-uid)
        :style {:display "flex"
                :flex-direction "column"
                :border-radius "8px"
                :overflow "hidden"}}
       [:> Card {:elevation 3
                 :style {:flex "1"
                         :margin "0"
                         :display "flex"
                         :flex-direction "column"
                         :border "2px solid rgba(0, 0, 0, 0.2)"
                         :border-radius "8px"}}

        [chat-history (:uid @messages-atom) messages-atom]
        [:div.bottom-comp
         {:style {:box-shadow "rgb(175 104 230) 0px 0px 5px 0px"}}
         [:div.chat-input-container
          {:style {:display "flex"
                   :flex-direction "row"
                   :background-color "#f6cbfe3d"
                   :border "1px"}}]]]])))




(defn llm-dg-suggestions-main [block-uid dom-id]
  (let [parent-el (.getElementById js/document (str dom-id))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [discourse-node-suggestions-ui block-uid] parent-el)))