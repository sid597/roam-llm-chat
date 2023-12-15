(ns ui.components
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [cljs.core.async.interop :as asy :refer [<p!]]
            [ui.extract-data :as ed :refer [data-for-pages q]]
            [reagent.dom :as rd]))

(defn log
  [& args]  (apply js/console.log args))
(defn send-message-component [active? callback]
  [:> Button {:class-name "sp"
              :style {:width "30px"}
              :icon (if @active? "send-message" nil)
              :min-height "20px"
              :minimal true
              :fill false
              :large true
              :loading (not @active?)
              :on-click callback}])

(defn chin []
  )


(defn settings-menu [{:keys [default-model default-msg-value default-temp]}]
   [:div
    [:> Popover
     {:arrow true
      :position "bottom"
      :style {:width "200px"
              :padding "20px"}}
     [:> Button {:class-name "sp"
                 :icon "cog"
                 :minimal true
                 :small true
                 :fill true
                 :style {:height "10px"
                         :border "none"}}]
     [:> Menu
      {:style {:padding "20px"}}
      [:span {:style {:margin-bottom "5px"}} "Select Model:"]
      [:> HTMLSelect
       {:fill true
        :style {:margin-bottom "10px"}
        :on-change (fn [e]
                     (reset! default-model (j/get-in e [:currentTarget :value])))
        :value @default-model}

       [:option {:value "gpt-4-1106-preview"} "gpt-4-1106-preview"]
       [:option {:value "gpt-3.5-turbo-1106"} "gpt-3.5-turbo-1106"]]
      [:> MenuDivider {:style {:margin "5px"}}]
      [:div
       {:style {:margin-bottom "10px"}}
       [:span {:style {:margin-bottom "5px"}} "Max output length:"]
       [:> Slider {:min 0
                   :max 2048
                   :label-renderer @default-msg-value
                   :value @default-msg-value
                   :label-values [0 2048]
                   :on-change (fn [e]
                                (reset! default-msg-value e))
                   :on-release (fn [e]
                                 (log "slider value" e)
                                 (reset! default-msg-value e))}]]
      [:> MenuDivider {:style {:margin "5px"}}]
      [:div
       {:style {:margin-bottom "10px"}}
       [:span {:style {:margin-bottom "5px"}} "Temperature:"]
       [:> Slider {:min 0
                   :max 2
                   :step-size 0.1
                   :label-renderer @default-temp
                   :value @default-temp
                   :label-values [0 2]
                   :on-change (fn [e]
                                (reset! default-temp e))
                   :on-release (fn [e]
                                 (reset! default-temp e))}]]]]])
