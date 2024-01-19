(ns ui.render-comp
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.components :as comp :refer [send-message-component chin chat-context chat-history]]
            [ui.utils :refer [q get-parent-parent extract-from-code-block call-openai-api log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
            [ui.actions :refer [send-context-and-message load-context]]
            [reagent.dom :as rd]))



(defn chat-ui [block-uid]
  #_(println "block uid for chat" block-uid)
  (let [settings (get-child-with-str block-uid "Settings")
        context  (r/atom (get-child-with-str block-uid "Context"))
        messages (r/atom (get-child-with-str block-uid "Messages"))
        active? (r/atom false)
        default-msg-value (r/atom 400)
        default-temp (r/atom 0.9)
        default-model (r/atom "gpt-4-1106-preview")
        get-linked-refs (r/atom true)]
   (fn [_]
     (let [msg               @messages
           c-msg             (:children @context)
           callback          (fn [{:keys [b-uid] :or {b-uid block-uid}}]
                               ;(println "called callback to load context")
                               (when (not @active?)
                                 (do
                                   #_(println "---- clicked send button ----")
                                   (reset! active? true)
                                   (load-context context messages b-uid active? get-linked-refs {:model @default-model
                                                                                                 :max-tokens @default-msg-value
                                                                                                 :temperature @default-temp}))))
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
          [chat-history messages]
         [:div.chat-input-container
          {:style {:display "flex"
                   :flex-direction "row"
                   :border-radius "8px"
                   :margin "10px 10px -10px 10px  "
                   :background-color "whitesmoke"
                   :border "1px"}}
          [chat-context context handle-key-event]]
         [chin default-model default-msg-value default-temp get-linked-refs active? callback]]]))))


(defn main [{:keys [:block-uid]} & args]
  #_(println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    #_(println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))
