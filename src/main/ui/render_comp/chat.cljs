(ns ui.render-comp.chat
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.components.chat :as comp :refer [chat-context chat-history]]
            [ui.components.chin :refer [chin]]
            [ui.utils :refer [get-safety-settings send-message-component model-mappings watch-children update-block-string-for-block-with-child watch-string create-struct settings-struct get-child-of-child-with-str q p get-parent-parent extract-from-code-block log update-block-string-and-move is-a-page? get-child-with-str move-block create-new-block]]
            [ui.actions.chat :refer [send-context-and-message load-context]]
            [reagent.dom :as rd]))




(defn chat-ui [block-uid]
  #_(println "block uid for chat" block-uid)
  (let [settings           (get-child-with-str block-uid "Settings")
        context            (r/atom (get-child-with-str block-uid "Context"))
        messages           (r/atom (get-child-with-str block-uid "Messages"))
        chat               (r/atom (get-child-with-str block-uid "Chat"))
        messages-atom      (r/atom (get-child-with-str block-uid "Messages"))
        active?            (r/atom (if (= "true" (get-child-of-child-with-str block-uid "Settings" "Active?"))
                                     true
                                     false))
        token-count        (r/atom (js/parseInt (get-child-of-child-with-str block-uid "Settings" "Token count")))
        default-max-tokens (r/atom (js/parseInt (get-child-of-child-with-str block-uid "Settings" "Max tokens")))
        default-temp       (r/atom (js/parseFloat (get-child-of-child-with-str block-uid "Settings" "Temperature")))
        default-model      (r/atom (get-child-of-child-with-str block-uid "Settings" "Model"))
        get-linked-refs?    (r/atom (if (= "true" (get-child-of-child-with-str block-uid "Settings" "Get linked refs"))
                                      true
                                      false))
        extract-query-pages? (r/atom (if (= "true" (get-child-of-child-with-str block-uid "Settings" "Extract query pages"))
                                       true
                                       false))]
    (watch-children
      (:uid @messages)
      (fn [_ aft]
        ;(p "context children changed" aft)
        (reset! messages-atom aft)))

    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Token count" false)
      (fn [b aft]
        (p "token count changed" aft)
        (reset! token-count (js/parseInt (:string aft)))))

    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Max tokens" false)
      (fn [_ aft]
        (p "max tokens changed" aft)
        (reset! default-max-tokens (js/parseInt (:string aft)))))

    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Temperature" false)
      (fn [_ aft]
        (p "temperature changed" aft)
        (reset! default-temp (js/parseFloat (:string aft)))))

    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Model" false)
      (fn [_ aft]
        (p "model name changed" aft)
        (reset! default-model (:string aft))))

    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Get linked refs" false)
      (fn [_ aft]
        (p "get linked refs changed" aft)
        (reset! get-linked-refs? (if (= "true" (:string aft))
                                    true
                                    false))))
    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Extract query pages" false)
      (fn [_ aft]
        (p "extract query results changed" aft)
        (reset! extract-query-pages? (if (= "true" (:string aft))
                                       true
                                       false))))

    (watch-string
      (get-child-of-child-with-str block-uid "Settings" "Active?" false)
      (fn [_ aft]
        (p "Active button changed" aft)
        (reset! active? (if (= "true" (:string aft))
                          true
                          false))))

   (fn [_]
     (let [msg-children      @messages-atom
           c-msg             (:children @context)
           callback          (fn [{:keys [b-uid] :or {b-uid block-uid}}]
                               ;(println "called callback to load context")
                               (when (not @active?)
                                 (p "*Send* Button clicked")
                                 (do
                                   (update-block-string-for-block-with-child block-uid "Settings" "Active?" (str (not @active?)))
                                   (reset! active? true)
                                   (load-context
                                     chat
                                     messages
                                     b-uid
                                     active?
                                     get-linked-refs?
                                     (merge
                                       {:model       (get model-mappings @default-model)
                                        :max-tokens  @default-max-tokens
                                        :temperature @default-temp}
                                       (when (= "gemini" @default-model)
                                         {:safety-settings (get-safety-settings b-uid)}))
                                     token-count
                                     extract-query-pages?))))
           handle-key-event  (fn [event]
                               (when (and (.-altKey event) (= "Enter" (.-key event)))
                                 (let [buid (-> (j/call-in js/window [:roamAlphaAPI :ui :getFocusedBlock])
                                              (j/get :block-uid))
                                       b-parent (get-parent-parent buid)]
                                  (callback b-parent))))]
       [:div
        {:class-name (str "chat-container-" block-uid)
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
         [:div.top-comp
           {:class-name (str "chat-input-container-" block-uid)
            :style {:display "flex"
                    :flex-direction "row"
                    :box-shadow "rgb(100 100 100) 0px 0px 5px 0px"
                    :margin-bottom "15px"
                    :background-color "whitesmoke"
                    :border "1px"}}
           [chat-context context #() {:min-height     ""
                                      :background-color "whitesmoke"
                                      :padding-bottom "10px"}]]

         [chat-history (:uid messages) messages-atom token-count {:key (hash msg-children)}]
         [:div.bottom-comp
          {:style {:box-shadow "rgb(175 104 230) 0px 0px 5px 0px"}}
          [:div.chat-input-container
           {:style {:display "flex"
                    :flex-direction "row"
                    :background-color "#f6cbfe3d"
                    :border "1px"}}
           [chat-context chat handle-key-event]]
          [chin {:default-model        default-model
                 :default-max-tokens   default-max-tokens
                 :default-temp         default-temp
                 :get-linked-refs?      get-linked-refs?
                 :active?              active?
                 :block-uid            block-uid
                 :callback             callback
                 :extract-query-pages? extract-query-pages?}]]]]))))


(defn main [{:keys [:block-uid]} & args]
  (let [parent-el (.getElementById js/document (str (second args)))]
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))
