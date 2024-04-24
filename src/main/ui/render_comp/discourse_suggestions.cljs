(ns ui.render-comp.discourse-suggestions
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.utils :refer [get-safety-settings send-message-component model-mappings watch-children update-block-string-for-block-with-child watch-string create-struct settings-struct get-child-of-child-with-str q p get-parent-parent extract-from-code-block log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
            [reagent.dom :as rd]))


(defn actions [child m-uid selections]
  (let [checked (r/atom false)]
    (fn [_ _ _]
      [:div
       {:style {:display "flex"}}
       [:div
        {:class-name (str "msg-" (:uid child))
         :style {:flex "1"}
         :ref (fn [el]
                (when (some? el)
                  (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                    (clj->js {:uid      (:uid child)
                              :open?    false
                              :zoom-path false
                              :el        el}))))}]
       [:div
        {:class-name (str "messages-chin-" m-uid)
         :style {:display          "flex"
                 :flex-direction   "row"
                 :background-color "aliceblue"
                 :min-height       "27px"
                 :justify-content  "end"
                 :font-size        "10px"
                 :align-items      "center"}}
        [:> Button {:class-name (str "scroll-down-button" m-uid)
                    :style      {:width "30px"}
                    :icon       "tick"
                    :minimal    true
                    :fill       false
                    :small      true
                    :on-click   (fn [e]
                                  (p "Create discourse node"))}]
        [:> Button {:class-name (str "scroll-up-button" m-uid)
                    :style      {:width "30px"}
                    :icon       "cross"
                    :minimal    true
                    :fill       false
                    :small      true
                    :on-click   (fn [e]
                                  (p "Discard selected option"))}]
        [:> Checkbox
         {:style     {:margin-bottom "0px"
                      :padding-left  "30px"}
          :checked   @checked
          :on-change (fn []
                       (do
                         (println "clicked checked")
                         (swap! selections conj m-uid)
                         (swap! checked not @checked)))}]]])))

(defn chat-history [m-uid m-children selections]
  [:div.middle-comp
   {:class-name (str "chat-history-container-" m-uid)
    :style
    {:display       "flex"
     :box-shadow    "#2e4ba4d1 0px 0px 4px 0px"
     :padding       "10px"
     :background    "aliceblue"
     :margin-bottom "15px"
     :flex-direction "column"}}
   [:div
    {:class-name (str "chat-history-" m-uid)
     :style {:overflow-y "auto"
             :min-height "300px"
             :max-height "700px"
             :background "aliceblue"}}
    (doall
      (for [child (sort-by :order @m-children)]
        ^{:key (:uid child)}
        [actions child m-uid selections]))]])


(defn discourse-node-suggestions-ui [block-uid]
 #_(println "block uid for chat" block-uid)
 (let [suggestions-data (get-child-with-str block-uid "Suggestions")
       uid              (:uid suggestions-data)
       suggestions      (r/atom (:children suggestions-data))
       selections       (r/atom [])]
   (watch-children
     uid
     (fn [_ aft]
       (reset! suggestions (:children aft))))
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

       [chat-history uid suggestions selections]
       [:div.bottom-comp
        [:div.chat-input-container
         {:style {:display "flex"
                  :flex-direction "row"}}]
        [:div
         {:class-name (str "messages-chin-")
          :style {:display "flex"
                  :flex-direction "row"
                  :justify-content "end"
                  :padding "10px"
                  :align-items "center"}}
         [:> Button {:class-name (str "scroll-down-button")
                     :minimal true
                     :fill false
                     :small true}
          "Create selected"]
         [:> Button {:class-name (str "scroll-up-button")
                     :minimal true
                     :fill false
                     :small true}
          "Discard selected"]]]]])))



(defn llm-dg-suggestions-main [block-uid dom-id]
  (let [parent-el (.getElementById js/document (str dom-id))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [discourse-node-suggestions-ui block-uid] parent-el)))