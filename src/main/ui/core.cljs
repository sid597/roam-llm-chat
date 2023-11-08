(ns ui.core
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [reagent.dom :as rd]))

(defn log
  [& args]
  (apply js/console.log args))

(defn chat-ui [e]
  [:div.chat-container
   {:style {:display "flex"
            :flex-direction "column"
            :height "500px" ;; Adjust the height as needed
            :border-radius "8px"
            :overflow "hidden"}}
   [:> Card {:interactive true
             :elevation 3
             :style {:flex "1"
                     :margin "0"
                     :display "flex"
                     :flex-direction "column"
                     :border "1px solid rgba(0, 0, 0, 0.2)"
                     :border-radius "8px"}}
    [:div.chat-history
     {:style {:flex "1"
              :overflow-y "auto"
              :margin "10px"}}]
     ;; Content of chat history goes here

    [:div.chat-input-container
     {:style {:display "flex"
              :align-items "center"
              :padding "10px"}}
     [:> InputGroup {:fill true
                     :large true
                     :placeholder "Type a message..."
                     :style {:flex "1" ;; Ensures input takes available space
                             :margin-right "10px"}}] ;; Adds a gap between input and button
     [:> Button {:icon "arrow-right"
                 :intent "primary"
                 :large true
                 :style {:margin-left "10px"}}]]]]) ;; Adds a gap on the left side of the button


(defn add-new-option-to-context-menu []
  (let [block-context-menus (j/get-in js/window [:roamAlphaAPI :ui :blockContextMenu])]
    (log "block context menu" block-context-menus)
    (.addCommand block-context-menus
      (clj->js {:label "Chat LLM: Hello from ClojureScript"
                :display-conditional (fn [e]
                                       #_(log "display conditional" e)
                                       true)
                :callback (fn [e]
                            (let [dom-id (str "block-input-" (j/get e :window-id) "-" (j/get e :block-uid))
                                  parent-el (.getElementById js/document dom-id)]
                               (rd/render [chat-ui e] parent-el)))}))))


;; Returns
#_{:block-uid "8CskYJbhx"
   :page-uid "11-08-2023"
   :window-id "m0n15kMpYIaPLcMEchKcuWKdLAK2-body-outline-11-08-2023"
   :read-only? false
   :block-string ""
   :heading nil}



(defn init []
  (js/console.log "Hello from roam cljs plugin boilerplate!")
  (add-new-option-to-context-menu))