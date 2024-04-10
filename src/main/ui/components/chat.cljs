(ns ui.components.chat
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [ui.utils :refer [p get-child-with-str watch-children update-block-string-for-block-with-child]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))

(defn log
  [& args]  (apply js/console.log args))

(defn chat-context
  ([context handle-keydown-event]
   (chat-context context handle-keydown-event {}))
  ([context handle-keydown-event style-map]
   (let [context-ref (r/atom nil)
         chat-loaded (r/atom nil)
         update-fn   (fn [this]
                       (let [context-el @context-ref
                             uid (:uid @context)]
                         (when (and context-el
                                 uid)
                           ;(println "4. chat context update fn")
                           ;(set! (.-innerHTML context-el ) "")

                           (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                 (clj->js {:uid (:uid @context)
                                           :zoom-path true
                                           :el context-el}))
                             (.then
                               (fn [_]
                                 (p "chat context block rendered successfully")))
                             (.catch (fn [e]
                                       (log "Error in" (if (not-empty style-map)
                                                         "chat-area-"
                                                         "chat-context-") (:uid @context) "--" e "--" @context "--" context-el)))))))]

     (r/create-class
       {:component-did-mount  update-fn
        :component-did-update update-fn
        :reagent-render
        (fn []
          (let [cmsg (:children @context)]
            #_(println "3. chat context insdie component")
            [:div
             {:class-name (str (if (not-empty style-map)
                                 "chat-area-"
                                 "chat-context-") (:uid @context))
              :ref (fn [el] (reset! context-ref el))
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
                       style-map)}]))}))))


(defn chat-history [m-uid m-children token-count]
  (let [history-ref  (r/atom nil)
        update-fn   (fn [this]
                      (when-let [hist-el @history-ref]
                        ;(p "chat history update fn: " @m-children)
                        (set! (.-innerHTML hist-el ) "")
                        (doall
                          (for [child (reverse (sort-by :order (:children @m-children)))]
                            ^{:key child}
                            (do
                             (let [uid (:uid child)
                                   msg-block-div (.createElement js/document (str "div.msg-" uid))]
                               (do
                                 ;(log "chat-history ref" hist-el)
                                 #_(println "chat histor child ---->" child)
                                 (if (.hasChildNodes hist-el)
                                   (.insertBefore hist-el msg-block-div (.-firstChild hist-el))
                                   (.appendChild hist-el msg-block-div (.-firstChild hist-el)))
                                 (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                   (clj->js {:uid uid
                                             :zoom-path true
                                             :el msg-block-div})))))))
                        (.scrollTo hist-el 0 (.-scrollHeight hist-el))))]


    (r/create-class
      {:component-did-update update-fn
       :component-did-mount  update-fn
       :reagent-render       (fn [_ _ _]
                               (let [tc @token-count
                                     id (random-uuid)]
                                 ;(p "TOKEN COUNT" tc)
                                 [:div.middle-comp
                                  {:class-name (str "chat-history-container-" m-uid)
                                   :style
                                    {:display "flex"
                                     :box-shadow "#2e4ba4d1 0px 0px 4px 0px"
                                     :margin-bottom "15px"
                                     :flex-direction "column"}}
                                  [:div
                                   {:class-name (str "chat-history-" m-uid)
                                    :ref   (fn [el] (reset! history-ref el))
                                    :style {:flex "1"
                                            :overflow-y "auto"
                                            :min-height "300px"
                                            :max-height "700px"
                                            :background "aliceblue"}}]

                                  [:div
                                   {:class-name (str "messages-chin-" m-uid)
                                    :style {:display "flex"
                                            :flex-direction "row"
                                            :background-color "aliceblue"
                                            :min-height "27px"
                                            :justify-content "end"
                                            :font-size "10px"
                                            :padding-right "11px"
                                            :padding-top "10px"
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
                                                              (set! (.-scrollTop el) 0)))}]]]))})))








