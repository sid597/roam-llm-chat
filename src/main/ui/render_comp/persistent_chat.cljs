(ns ui.render-comp.persistent-chat
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.components.chat :as comp :refer [chat-context chat-history]]
            [ui.components.chin :refer [chin]]
            [ui.utils :refer [render-child-node get-all-users buttons-settings extract-from-code-block create-new-block uid->eid get-current-user chat-ui-with-context-struct ai-block-exists? button-popover button-with-tooltip model-mappings get-safety-settings update-block-string-for-block-with-child settings-button-popover image-to-text-for p get-child-of-child-with-str title->uid q block-has-child-with-str? call-llm-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            [ui.actions.chat :refer [send-context-and-message load-context]]
            [reagent.dom :as rd]))


(comment
  (uid->eid (title->uid "Persistent llm chat"))
  (ffirst (q '[:find (pull ?c [:block/uid {:block/children ...}])
               :in $ ?c]
            41125))
  (let [page-eid    (uid->eid (title->uid "Persistent llm chat"))
        all-chats   (:children
                      (ffirst (q '[:find (pull ?c [:block/uid :block/order :block/string {:block/children ...}])
                                   :in $ ?c]
                                page-eid)))
        first-chat  (->> all-chats
                      (sort-by :order)
                      first)
        sorted-chat-messages (->> first-chat
                               :children
                               first
                               :children
                               (sort-by :order))]
    sorted-chat-messages))

(defn persistent-chat-ui []
  (let [page-eid                    (uid->eid (title->uid "Persistent llm chat"))
        all-chats                   (:children
                                      (ffirst (q '[:find (pull ?c [:block/uid :block/order :block/string {:block/children ...}])
                                                   :in $ ?c]
                                                page-eid)))
        first-chat                  (->> all-chats
                                      (sort-by :order)
                                      first)
        sorted-chat-messages        (->> first-chat
                                      :children
                                      first
                                      :children
                                      (sort-by :order))
        ps-uid                      (:uid
                                      (get-child-with-str
                                        (block-has-child-with-str?
                                          (title->uid "LLM chat settings")
                                          "Quick action buttons")
                                        "Persistent chat"))
        ps-default-model            (r/atom (get-child-of-child-with-str ps-uid "Settings" "Model"))
        ps-default-temp             (r/atom (js/parseFloat (get-child-of-child-with-str ps-uid "Settings" "Temperature")))
        ps-default-max-tokens       (r/atom (js/parseInt (get-child-of-child-with-str ps-uid "Settings" "Max tokens")))
        ps-get-linked-refs?         (r/atom (if (= "true" (get-child-of-child-with-str ps-uid "Settings" "Get linked refs"))
                                              true
                                              false))
        ps-extract-query-pages?     (r/atom (if (= "true" (get-child-of-child-with-str ps-uid "Settings" "Extract query pages"))
                                              true
                                              false))
        ps-extract-query-pages-ref? (r/atom (if (= "true" (get-child-of-child-with-str ps-uid "Settings" "Extract query pages ref?"))
                                              true
                                              false))
        ps-step-1-prompt            (get-child-of-child-with-str ps-uid "Prompt" "Step-1")
        ps-step-2-prompt            (get-child-of-child-with-str ps-uid "Prompt" "Step-2")
        ps-active?                  (r/atom false)
        chat-block-uid              (:uid (get-child-with-str ps-uid "Chat"))
        messages-ref (atom nil)]
    (fn []
      [:div
       {:class-name (str "persistent-chat-container")
        :style {:display "flex"
                :flex-direction "column"
                :border-radius "8px"
                :border "2px solid rgba(0, 0, 0, 0.2)"
                :background-color "#f8f9fa"
                :height "100%"
                :overflow "hidden"}
        #_[:> Card {:elevation 1
                    :style {:flex "1"
                            :margin "0"
                            :display "flex"
                            :flex-direction "column"
                            :border "2px solid rgba(0, 0, 0, 0.2)"
                            :border-radius "8px"}}]}
       [:div.top-comp
        {:class-name (str "chat-input-container-p")
         :style {:display "flex"
                 :box-shadow "#2e4ba4d1 0px 0px 4px 0px"
                 :margin-bottom "15px"
                 :height "90%"
                 :flex-direction "column"}}
        [:div
         {:class-name (str "chat-history-")
          :ref (fn [el]
                 (when (some? el)
                   (do
                     (set! (.-scrollTop el) (.-scrollHeight el))
                     (reset! messages-ref el))))
          :style {:flex "1"
                  :overflow-y "auto"}}
                  ;:background "aliceblue"}}
         (doall
           (for [child sorted-chat-messages]
             ^{:key (:uid child)}
             [render-child-node child]))]

        [:div
         {:class-name "messages-chin"
          :style {:display "flex"
                  :flex-direction "row"
                  :background-color "aliceblue"
                  :min-height "27px"
                  :justify-content "end"
                  :font-size "10px"
                  :padding "8px"
                  :align-items "center"
                  :border "1px"}}
         [:span (str "Tokens used: ")]
         [:> Button {:class-name (str "scroll-down-button")
                     :style {:width "30px"}
                     :icon "chevron-down"
                     :minimal true}]
         [button-with-tooltip
          "Scroll to the top of messages, i.e show the oldest chat messages."
          [:> Button {:class-name (str "scroll-up-button")
                      :style {:width "30px"}
                      :icon "chevron-up"
                      :minimal true
                      :fill false
                      :small true}]]]]
       [:div.bottom-comp
        {:style {:box-shadow "rgb(175 104 230) 0px 0px 5px 0px"
                 :height "10%"}}
        [:div
         {:class-name "ps-chat-context"
          :ref (fn [el]
                 (when (some? el)
                    (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                            (clj->js {:uid       chat-block-uid
                                      :zoom-path true
                                      :el        el}))
                        (.then
                          (fn [_]
                            (p "chat context block rendered successfully"))))))
          :style (merge {:flex "1 1 auto"
                         :height "100%"
                         :overflow "auto"
                         :flex-direction "column"
                         :display "flex"
                         :align-items "stretch"
                         :background-color "#f6cbfe3d"
                         :min-height "100px"
                         :max-height "700px"})}]
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
          [:div {:style {:flex "1 1 1 1"}}
           [button-with-tooltip
            "Summarize based on what is in the context window, also includes the linked discourse node references as context and
           if there is a query then we include all the context from the query result pages as well.
           "
            [:> Button
             {:minimal true
              :fill false}
             "Get context"]]]]]]])))
