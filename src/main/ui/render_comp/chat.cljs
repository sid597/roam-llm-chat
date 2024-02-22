(ns ui.render-comp.chat
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.components.chat :as comp :refer [send-message-component chin chat-context chat-history]]
            [ui.utils :refer [create-struct settings-struct get-child-of-child-with-str q p get-parent-parent extract-from-code-block call-openai-api log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
            [ui.actions.chat :refer [send-context-and-message load-context]]
            [reagent.dom :as rd]))



(defn chat-ui [block-uid]
  #_(println "block uid for chat" block-uid)
  (let [settings          (get-child-with-str block-uid "Settings")
        context           (r/atom (get-child-with-str block-uid "Context"))
        messages          (r/atom (get-child-with-str block-uid "Messages"))
        chat              (r/atom (get-child-with-str block-uid "Chat"))
        active?           (r/atom false)
        token-count       (r/atom (js/parseInt (get-child-of-child-with-str block-uid "Settings" "Token count")))
        default-msg-value (r/atom (js/parseInt (get-child-of-child-with-str block-uid "Settings" "Max tokens")))
        default-temp      (r/atom (js/parseFloat (get-child-of-child-with-str block-uid "Settings" "Temperature")))
        default-model     (r/atom (get-child-of-child-with-str block-uid "Settings" "Model"))
        get-linked-refs   (r/atom (if (= "true" (get-child-of-child-with-str block-uid "Settings" "Get linked refs"))
                                    true
                                    false))]
   (fn [_]
     ;(p "--------> default model" @default-model "default temp" @default-temp "default msg value" @default-msg-value "default token count" @token-count)
     #_(when (nil? @default-model)
         (create-struct settings-struct block-uid nil false)
         (p "reseting dummy atom" @dummy-atom)
         (reset! dummy-atom 2))
     (let [msg               @messages
           c-msg             (:children @context)
           callback          (fn [{:keys [b-uid] :or {b-uid block-uid}}]
                               ;(println "called callback to load context")
                               (when (not @active?)
                                 (p "*Send* Button clicked")
                                 (do
                                   (reset! active? true)
                                   (load-context
                                     chat
                                     messages
                                     b-uid
                                     active?
                                     get-linked-refs
                                     {:model (if (= "gpt-4" @default-model)
                                               "gpt-4-0125-preview"
                                               "gpt-3.5-turbo-0125")
                                      :max-tokens @default-msg-value
                                      :temperature @default-temp}
                                     token-count))))

           handle-key-event  (fn [event]
                               (when (and (.-altKey event) (= "Enter" (.-key event)))
                                 (let [buid (-> (j/call-in js/window [:roamAlphaAPI :ui :getFocusedBlock])
                                              (j/get :block-uid))
                                       b-parent (get-parent-parent buid)]
                                  (callback b-parent))))]
       [:div.chat-container
        {:style {:display "flex"
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
         [:div.chat-input-container
                   {:style {:display "flex"
                            :flex-direction "row"
                            :border-radius "8px"
                            :margin "10px 10px "
                            :background-color "whitesmoke"
                            :border "1px"}}
                   [chat-context context #() {:min-height ""
                                              :padding-bottom "10px"}]]
         [chat-history messages token-count]
         [:div.chat-input-container
          {:style {:display "flex"
                   :flex-direction "row"
                   :border-radius "8px"
                   :margin "10px 10px -10px 10px  "
                   :background-color "whitesmoke"
                   :border "1px"}}
          [chat-context chat handle-key-event]]
         [chin default-model default-msg-value default-temp get-linked-refs active? block-uid callback]]]))))



(defn main [{:keys [:block-uid]} & args]
  (let [parent-el (.getElementById js/document (str (second args)))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))
