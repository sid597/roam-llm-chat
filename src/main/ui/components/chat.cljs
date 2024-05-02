(ns ui.components.chat
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.utils :refer [p delete-block get-child-with-str watch-children update-block-string-for-block-with-child]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

(defn log
  [& args]  (apply js/console.log args))



(defn chat-context
  ([context handle-keydown-event]
   (chat-context context handle-keydown-event {}))
  ([context handle-keydown-event style-map]
   (fn [_ _ _]
     (let [uid (:uid @context)]
       [:div
        {:class-name (str (if (not-empty style-map)
                            "chat-area-"
                            "chat-context-") uid)
         :ref (fn [el]
                (when (and (some? el)
                        uid)
                 (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                       (clj->js {:uid       uid
                                 :zoom-path true
                                 :el        el}))
                   (.then
                     (fn [_]
                       (p "chat context block rendered successfully")))
                   (.catch (fn [e]
                             (log "Error in" (if (not-empty style-map)
                                               "chat-area-"
                                               "chat-context-") uid "--" e "--" "--" el))))))
         :on-key-down handle-keydown-event
         :style (merge {:flex "1 1 auto"
                        :height "100%"
                        :overflow "auto"
                        :flex-direction "column"
                        :display "flex"
                        :align-items "stretch"
                        :background-color "#f6cbfe3d"
                        :min-height "100px"
                        :max-height "700px"}
                  style-map)}]))))

(defn child-node [child with-actions?]
  (fn [_]
    (let [uid (:uid child)]
      [:div
       {:class-name (str "child-node-" uid)
        :style {:display "flex"}}
       [:div
        {:class-name (str "child-node-render-")
         :style {:flex "1"}
         :ref (fn [el]
                (when (some? el)
                  (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                        (clj->js {:uid       uid
                                  :open      false
                                  :zoom-path false
                                  :el        el}))
                    (.then
                      (fn [_]
                        (p "MESSAGES block rendered successfully"))))))}]
       (when with-actions?
         [:div
          {:class-name (str "messages-chin-" uid)
           :style {:display          "flex"
                   :flex-direction   "row"
                   :background-color "aliceblue"
                   :min-height       "27px"
                   :justify-content  "end"
                   :font-size        "10px"
                   :align-items      "center"}}
          [:> Button {:class-name (str "create-node-button" uid)
                      :style      {:width "30px"}
                      :icon       "tick"
                      :minimal    true
                      :fill       false
                      :small      true
                      #_#_:on-click   (fn [e]
                                        (let [block-uid    (:uid child)
                                              block-string (:string (ffirst (uid-to-block block-uid)))]
                                          (p "Create discourse node" child)
                                          (do
                                            (create-discourse-node-with-title block-string)
                                            (when (template-data-for-node block-string))
                                            (update-block-string block-uid (str "^^ {{[[DONE]]}} " block-string "^^")))))}]

          [:> Button {:class-name (str "discard-node-button" uid)
                      :style      {:width "30px"}
                      :icon       "cross"
                      :minimal    true
                      :fill       false
                      :small      true
                      :on-click   (fn [e]
                                    (p "Discard selected option")
                                    (delete-block (:uid child)))}]])])))



(defn chat-history [m-uid m-children token-count]
  (let [history-ref (atom nil)]
    (fn [_ _ _]
      (let [tc @token-count
            children (:children @m-children)]
        ;(p "TOKEN COUNT" tc)
        [:div.middle-comp
         {:class-name (str "chat-history-container-" m-uid)
          :style {:display "flex"
                  :box-shadow "#2e4ba4d1 0px 0px 4px 0px"
                  :margin-bottom "15px"
                  :flex-direction "column"}}
         [:div
          {:class-name (str "chat-history-" m-uid)
           :ref (fn [el] (when (some? el) (reset! history-ref el)))
           :style {:flex "1"
                   :overflow-y "auto"
                   :min-height "300px"
                   :max-height "700px"
                   :background "aliceblue"}}
          (doall
           (for [child (sort-by :order children)]
             (if (= "Suggestions for new discourse nodes" (:string child))
               (doall
                 (for [ch (sort-by :order (:children child))]
                   ^{:key (:uid ch)}
                   [child-node ch true]))
               (do
                 ^{:key (:uid child)}
                [child-node child false]))))]
         [:div
          {:class-name (str "messages-chin-" m-uid)
           :style {:display "flex"
                   :flex-direction "row"
                   :background-color "aliceblue"
                   :min-height "27px"
                   :justify-content "end"
                   :font-size "10px"
                   :padding "8px"
                   :align-items "center"
                   :border "1px"}}
          [:span (str "Tokens used: " tc)]
          [:> Button {:class-name (str "scroll-down-button" m-uid)
                      :style {:width "30px"}
                      :icon "chevron-down"
                      :minimal true
                      :fill false
                      :small true
                      :on-click #(let [el @history-ref]
                                   (p "scroll down button clicked")
                                   (when el
                                     (set! (.-scrollTop el) (.-scrollHeight el))))}]
          [:> Button {:class-name (str "scroll-up-button" m-uid)
                      :style {:width "30px"}
                      :icon "chevron-up"
                      :minimal true
                      :fill false
                      :small true
                      :on-click #(let [el @history-ref]
                                   (p "scroll up button clicked")
                                   (when el
                                     (set! (.-scrollTop el) 0)))}]
          [:> Button {:class-name (str "scroll-up-button" m-uid)
                      :style {:width "30px"}
                      :icon "new-link"
                      :minimal true
                      :fill false
                      :small true}]]]))))

